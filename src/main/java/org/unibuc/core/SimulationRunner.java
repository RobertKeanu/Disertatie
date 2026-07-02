package org.unibuc.core;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimEntity;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.events.SimEvent;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.unibuc.algorithms.*;
import org.unibuc.metrics.AggregatedMetrics;
import org.unibuc.metrics.MetricsCollector;
import org.unibuc.metrics.ResultsExporter;
import org.unibuc.util.Workload;
import org.unibuc.util.WorkloadGenerator;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimulationRunner {
    private static final List<LoadBalancingPolicy> loadBalancingAlgorithms = List.of(
            new RoundRobinPolicy(),
            new WeightedRoundRobinPolicy(),
            new LeastConnectionsPolicy(),
            new IpHashPolicy(),
            new RandomPolicy(),
            new P2CPolicy(),
            new LeastReponseTimePolicy(),
            new P2CLRTPolicy()
    );
    private static final long SINGLE_RUN_WORKLOAD_SEED = 25616L;
    private static final long SINGLE_RUN_POLICY_SEED = 75616L;
    private static final long BASE_WORKLOAD_SEED = 1000L;
    private static final long BASE_POLICY_SEED = 10_000L;
    public static void main(String[] args) throws IOException {

        System.out.println("Algorithms : " + loadBalancingAlgorithms.stream()
                .map(LoadBalancingPolicy::getName).toList());
        System.out.println("Requests  : " + SimulationConfig.CLOUDLET_COUNT);
        System.out.println("VMs       : " + SimulationConfig.VM_COUNT);
        System.out.println("Hosts     : " + SimulationConfig.HOST_COUNT);
        System.out.println("Runs each : " + SimulationConfig.RUNS_PER_ALGORITHM);
        System.out.println();

        Workload singleRunWorkload = new WorkloadGenerator(SINGLE_RUN_WORKLOAD_SEED)
                .generateWorkload(SimulationConfig.CLOUDLET_COUNT, SimulationConfig.UNIQUE_SOURCE_IPS);

        List<MetricsCollector> singleRunResults = new ArrayList<>();

        for (LoadBalancingPolicy policy : loadBalancingAlgorithms) {
            System.out.println("Running: " + policy.getName() + " ...");
            policy.reset(SINGLE_RUN_POLICY_SEED);

            MetricsCollector metrics = runSimulation(policy, singleRunWorkload);
            singleRunResults.add(metrics);
            System.out.println(metrics);
        }

        System.out.println("\n═══ Multi-run phase ("
                + SimulationConfig.RUNS_PER_ALGORITHM + " runs each) ═══\n");

        Map<String, List<MetricsCollector>> perAlgoRuns = new LinkedHashMap<>();
        List<Workload> workloads = new ArrayList<>(SimulationConfig.RUNS_PER_ALGORITHM);
        for (int run = 0; run < SimulationConfig.RUNS_PER_ALGORITHM; run++) {
            workloads.add(new WorkloadGenerator(BASE_WORKLOAD_SEED + run)
                    .generateWorkload(SimulationConfig.CLOUDLET_COUNT, SimulationConfig.UNIQUE_SOURCE_IPS));
        }

        for (LoadBalancingPolicy policy : loadBalancingAlgorithms) {
            List<MetricsCollector> runs = new ArrayList<>();

            for (int run = 0; run < SimulationConfig.RUNS_PER_ALGORITHM; run++) {
                System.out.printf("  %-40s  [run %d/%d]%n",
                        policy.getName(), run + 1, SimulationConfig.RUNS_PER_ALGORITHM);

                policy.reset(BASE_POLICY_SEED + run);
                runs.add(runSimulation(policy, workloads.get(run)));
            }

            perAlgoRuns.put(policy.getName(), runs);
        }

        ResultsExporter exporter = new ResultsExporter(SimulationConfig.OUTPUT_DIR);

        exporter.export(singleRunResults);

        List<AggregatedMetrics> aggregated = perAlgoRuns.entrySet().stream()
                .map(e -> new AggregatedMetrics(e.getKey(), e.getValue()))
                .toList();

        exporter.exportAggregated(aggregated);

        System.out.println("\n═══ Aggregated results ═══");
        aggregated.forEach(System.out::println);
    }

    private static MetricsCollector runSimulation(
            LoadBalancingPolicy policy,
            Workload workload) {

        long wallClockStart = System.nanoTime();
        CloudSimPlus simulation = new CloudSimPlus();

        List<Host> hosts = createHosts();

        new DatacenterSimple(simulation, hosts);

        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);
        broker.setShutdownWhenIdle(false);

        List<Vm> vms = createVms();
        broker.submitVmList(vms);

        AtomicInteger completedCloudlets = new AtomicInteger();
        AtomicLong lastProgressNanos = new AtomicLong(wallClockStart);
        List<Cloudlet> cloudlets = workload.createCloudlets();
        cloudlets.forEach(cloudlet -> cloudlet.addOnFinishListener(
                info -> {
                    notifyCompletion(policy, info.getCloudlet());
                    int completed = completedCloudlets.incrementAndGet();
                    lastProgressNanos.set(System.nanoTime());
                    if (completed % SimulationConfig.PROGRESS_INTERVAL == 0
                            || completed == cloudlets.size()) {
                        printSimulationProgress(
                                policy.getName(),
                                completed,
                                cloudlets.size(),
                                wallClockStart
                        );
                    }
                }
        ));

        List<Vm> availableVms = new ArrayList<>(vms);
        broker.setVmMapper(cloudlet -> {
            int requestIndex = Math.toIntExact(cloudlet.getJobId());
            Workload.Request request = workload.getRequest(requestIndex);
            if (SimulationConfig.USE_DIRECT_POLICY_INFORMATION) {
                return policy.selectVm(
                        availableVms,
                        requestIndex,
                        request.sourceIp(),
                        request.cloudletLength(),
                        request.arrivalTime()
                );
            }
            return policy.selectVm(availableVms, requestIndex, request.sourceIp());
        });

        new WorkloadSubmitter(simulation, broker, workload, cloudlets);
        ScheduledExecutorService heartbeat = startHeartbeat(
                simulation,
                policy.getName(),
                completedCloudlets,
                lastProgressNanos,
                cloudlets.size(),
                wallClockStart
        );
        try {
            simulation.start();
        } finally {
            heartbeat.shutdownNow();
        }

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        return new MetricsCollector(policy.getName(), finished, vms);
    }

    private static ScheduledExecutorService startHeartbeat(
            CloudSimPlus simulation,
            String algorithmName,
            AtomicInteger completedCloudlets,
            AtomicLong lastProgressNanos,
            int totalCloudlets,
            long wallClockStart) {
        ScheduledExecutorService heartbeat =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "simulation-progress");
                    thread.setDaemon(true);
                    return thread;
                });
        long heartbeatSeconds = Long.getLong(
                "simulation.progressHeartbeatSeconds",
                SimulationConfig.PROGRESS_HEARTBEAT_SECONDS
        );
        long maxNoProgressSeconds = Long.getLong(
                "simulation.maxNoProgressSeconds",
                SimulationConfig.MAX_NO_PROGRESS_SECONDS
        );
        AtomicBoolean abortRequested = new AtomicBoolean();
        heartbeat.scheduleAtFixedRate(
                () -> {
                    printSimulationProgress(
                            algorithmName,
                            completedCloudlets.get(),
                            totalCloudlets,
                            wallClockStart
                    );

                    double stalledSeconds = (System.nanoTime() - lastProgressNanos.get())
                            / 1_000_000_000.0;
                    if (stalledSeconds >= maxNoProgressSeconds
                            && abortRequested.compareAndSet(false, true)) {
                        System.out.printf(
                                "    TIMEOUT  %-28s no completion for %.1f s; "
                                        + "aborting this simulation at %,d/%,d completed.%n",
                                algorithmName,
                                stalledSeconds,
                                completedCloudlets.get(),
                                totalCloudlets
                        );
                        simulation.abort();
                    }
                },
                heartbeatSeconds,
                heartbeatSeconds,
                TimeUnit.SECONDS
        );
        return heartbeat;
    }

    private static void printSimulationProgress(
            String algorithmName,
            int completed,
            int total,
            long wallClockStart) {
        double elapsedSeconds = (System.nanoTime() - wallClockStart) / 1_000_000_000.0;
        System.out.printf(
                "    progress %-28s %,d/%,d completed, wall time %.1f s%n",
                algorithmName,
                completed,
                total,
                elapsedSeconds
        );
    }

    public static MetricsCollector runSimulationForExperiment(
            LoadBalancingPolicy policy,
            Workload workload) {
        return runSimulation(policy, workload);
    }

    private static void notifyCompletion(LoadBalancingPolicy policy, Cloudlet cloudlet) {
        policy.onRequestComplete(cloudlet, responseTimeSeconds(cloudlet));
    }

    private static double responseTimeSeconds(Cloudlet cloudlet) {
        double arrivalTime = cloudlet.getDcArrivalTime();
        if (arrivalTime < 0) {
            arrivalTime = cloudlet.getBrokerArrivalTime();
        }
        if (arrivalTime < 0) {
            arrivalTime = cloudlet.getStartTime();
        }
        return Math.max(0, cloudlet.getFinishTime() - arrivalTime);
    }

    private static List<Host> createHosts() {
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < SimulationConfig.HOST_COUNT; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < SimulationConfig.HOST_PES; j++) {
                peList.add(new PeSimple(SimulationConfig.HOST_MIPS));
            }
            hosts.add(new HostSimple(
                    SimulationConfig.HOST_RAM_MB,
                    SimulationConfig.HOST_BW_MBPS,
                    SimulationConfig.HOST_STORAGE_MB,
                    peList
            ));
        }
        return hosts;
    }

    private static List<Vm> createVms() {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < SimulationConfig.VM_COUNT; i++) {
            VmSimple vm = new VmSimple(
                    SimulationConfig.VM_MIPS_PER_VM[i % SimulationConfig.VM_MIPS_PER_VM.length],
                    SimulationConfig.VM_PES,
                    new CloudletSchedulerSpaceShared()
            );
            vm.setRam(SimulationConfig.VM_RAM_MB)
                    .setBw(SimulationConfig.VM_BW_MBPS)
                    .setSize(SimulationConfig.VM_STORAGE_MB);
            vms.add(vm);
        }
        return vms;
    }

    private static class WorkloadSubmitter extends CloudSimEntity {
        private static final int SUBMIT_CLOUDLET = 10_000;

        private final DatacenterBrokerSimple broker;
        private final Workload workload;
        private final List<Cloudlet> cloudlets;
        private int submittedCloudlets;

        private WorkloadSubmitter(CloudSimPlus simulation,
                                  DatacenterBrokerSimple broker,
                                  Workload workload,
                                  List<Cloudlet> cloudlets) {
            super(simulation);
            this.broker = broker;
            this.workload = workload;
            this.cloudlets = cloudlets;
        }

        @Override
        protected void startInternal() {
            if (cloudlets.isEmpty()) {
                shutdown();
                return;
            }

            for (Cloudlet cloudlet : cloudlets) {
                int requestIndex = Math.toIntExact(cloudlet.getJobId());
                double arrivalTime = workload.getRequest(requestIndex).arrivalTime();
                schedule(arrivalTime, SUBMIT_CLOUDLET, cloudlet);
            }
        }

        @Override
        public void processEvent(SimEvent evt) {
            if (evt.getTag() != SUBMIT_CLOUDLET) {
                return;
            }

            broker.submitCloudlet((Cloudlet) evt.getData());
            submittedCloudlets++;
            if (submittedCloudlets == cloudlets.size()) {
                broker.setShutdownWhenIdle(true);
                broker.requestShutdownWhenIdle();
                shutdown();
            }
        }
    }
}
