package org.unibuc.core;

import org.unibuc.algorithms.IpHashPolicy;
import org.unibuc.algorithms.LeastConnectionsPolicy;
import org.unibuc.algorithms.LeastReponseTimePolicy;
import org.unibuc.algorithms.P2CLRTPolicy;
import org.unibuc.algorithms.P2CPolicy;
import org.unibuc.algorithms.RandomPolicy;
import org.unibuc.algorithms.RoundRobinPolicy;
import org.unibuc.algorithms.WeightedRoundRobinPolicy;
import org.unibuc.metrics.AggregatedMetrics;
import org.unibuc.metrics.LoadExperimentFailure;
import org.unibuc.metrics.LoadScenarioResult;
import org.unibuc.metrics.LoadSweepResultsExporter;
import org.unibuc.metrics.MetricsCollector;
import org.unibuc.util.Workload;
import org.unibuc.util.WorkloadGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class FinalLoadExperimentRunner {
    private static final List<PolicyFactory> POLICY_FACTORIES = List.of(
            new PolicyFactory("Round Robin", RoundRobinPolicy::new),
            new PolicyFactory("Adaptive Weighted Round Robin", WeightedRoundRobinPolicy::new),
            new PolicyFactory("Least Connections", LeastConnectionsPolicy::new),
            new PolicyFactory("Random", RandomPolicy::new),
            new PolicyFactory("Power of Two Choices", P2CPolicy::new),
            new PolicyFactory("Least Response Time", LeastReponseTimePolicy::new),
            new PolicyFactory("P2C-LRT Hybrid", P2CLRTPolicy::new),
            new PolicyFactory("IP Hash", IpHashPolicy::new)
    );

    public static void main(String[] args) throws IOException {
        runExperiment(
                FinalLoadExperimentConfig.CLOUDLET_COUNT,
                FinalLoadExperimentConfig.RUNS_PER_ALGORITHM,
                FinalLoadExperimentConfig.OUTPUT_DIR
        );
    }

    static List<LoadScenarioResult> runExperiment(
            int cloudletCount,
            int runsPerAlgorithm,
            String outputDir) throws IOException {

        if (cloudletCount <= 0 || runsPerAlgorithm <= 0) {
            throw new IllegalArgumentException(
                    "Cloudlet count and runs per algorithm must be positive");
        }

        List<LoadScenarioResult> allResults = new ArrayList<>();
        List<LoadExperimentFailure> failures = new ArrayList<>();
        Set<String> disabledAlgorithms = new HashSet<>();
        LoadSweepResultsExporter exporter = new LoadSweepResultsExporter(outputDir);
        printConfiguration(cloudletCount);

        for (FinalLoadExperimentConfig.LoadLevel loadLevel
                : FinalLoadExperimentConfig.LOAD_LEVELS) {
            System.out.println("\nLoad " + loadLevel.label());

            for (PolicyFactory factory : POLICY_FACTORIES) {
                if (disabledAlgorithms.contains(factory.name())) {
                    failures.add(new LoadExperimentFailure(
                            loadLevel.label(),
                            loadLevel.percentage(),
                            factory.name(),
                            0,
                            "SKIPPED_AFTER_TIMEOUT",
                            0,
                            cloudletCount,
                            0
                    ));
                    exporter.export(allResults, failures);
                    System.out.printf("SKIP %-27s %,d/%,d requests%n",
                            factory.name(), 0, cloudletCount);
                    continue;
                }

                LoadBalancingPolicy policy = factory.supplier().get();
                List<MetricsCollector> runResults = new ArrayList<>(runsPerAlgorithm);
                boolean incomplete = false;

                for (int run = 0; run < runsPerAlgorithm; run++) {
                    long workloadSeed = FinalLoadExperimentConfig.BASE_WORKLOAD_SEED + run;
                    long policySeed = FinalLoadExperimentConfig.BASE_POLICY_SEED + run;

                    Workload workload = new WorkloadGenerator(workloadSeed)
                            .generateWorkload(
                                    cloudletCount,
                                    SimulationConfig.UNIQUE_SOURCE_IPS,
                                    loadLevel.meanInterarrivalTimeSeconds()
                            );

                    policy.reset(policySeed);
                    printRequestCount(factory.name(), 0, cloudletCount);
                    long runWallStart = System.nanoTime();
                    MetricsCollector metrics =
                            SimulationRunner.runSimulationForExperiment(policy, workload);
                    double runWallSeconds =
                            (System.nanoTime() - runWallStart) / 1_000_000_000.0;
                    runResults.add(metrics);

                    printRequestCount(
                            factory.name(),
                            metrics.getTotalRequests(),
                            cloudletCount
                    );

                    if (metrics.getTotalRequests() < cloudletCount) {
                        failures.add(new LoadExperimentFailure(
                                loadLevel.label(),
                                loadLevel.percentage(),
                                factory.name(),
                                run + 1,
                                "TIMEOUT_NO_PROGRESS",
                                metrics.getTotalRequests(),
                                cloudletCount,
                                runWallSeconds
                        ));
                        disabledAlgorithms.add(factory.name());
                        incomplete = true;
                        System.out.printf(
                                "FAILED %-24s %,d/%,d requests%n",
                                factory.name(),
                                metrics.getTotalRequests(),
                                cloudletCount
                        );
                        break;
                    }
                }

                if (!incomplete) {
                    AggregatedMetrics aggregated =
                            new AggregatedMetrics(factory.name(), runResults);
                    allResults.add(new LoadScenarioResult(
                            loadLevel.label(),
                            loadLevel.percentage(),
                            loadLevel.targetRequestsPerSecond(),
                            loadLevel.meanInterarrivalTimeSeconds(),
                            aggregated
                    ));
                }

                exporter.export(allResults, failures);
            }
        }

        System.out.println("\nFinal load experiment completed.");
        System.out.println("Results: " + outputDir);
        return List.copyOf(allResults);
    }

    private static void printConfiguration(int cloudletCount) {
        System.out.println("Final progressive-load experiment");
        System.out.println("Cloudlets per simulation : " + cloudletCount);
    }

    private static void printRequestCount(
            String algorithmName,
            int completedRequests,
            int expectedRequests) {
        System.out.printf(
                "%-32s %,d/%,d requests%n",
                algorithmName,
                completedRequests,
                expectedRequests
        );
    }

    private record PolicyFactory(
            String name,
            Supplier<LoadBalancingPolicy> supplier) {
    }
}
