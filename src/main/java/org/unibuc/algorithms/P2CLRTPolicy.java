package org.unibuc.algorithms;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;
import org.unibuc.core.SimulationConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class P2CLRTPolicy implements LoadBalancingPolicy {
    private final Random random = new Random();
    private final ObservedBackendStats stats = new ObservedBackendStats();
    private final Map<Long, Double> directOutstandingServiceTime = new HashMap<>();
    private final int choices;
    private boolean directModeActive;
    private long seed;

    public P2CLRTPolicy(long seed) {
        this(SimulationConfig.P2C_LRT_CHOICES, seed);
    }

    public P2CLRTPolicy(int choices, long seed) {
        this.choices = Math.max(2, choices);
        reset(seed);
    }

    public P2CLRTPolicy() {
        this(SimulationConfig.P2C_LRT_CHOICES, SimulationConfig.DEFAULT_POLICY_SEED);
    }

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }

        stats.ensureVms(availableVms);
        List<Vm> sampledVms = sampleDistinct(availableVms);
        Vm selected = sampledVms.getFirst();
        double minScore = stats.score(selected);

        for (int i = 1; i < sampledVms.size(); i++) {
            Vm candidate = sampledVms.get(i);
            double candidateScore = stats.score(candidate);
            if (candidateScore < minScore) {
                minScore = candidateScore;
                selected = candidate;
            }
        }

        stats.requestStarted(selected);
        return selected;
    }

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp,
                       long cloudletLength, double arrivalTime) {
        if (!SimulationConfig.USE_DIRECT_POLICY_INFORMATION) {
            return selectVm(availableVms, requestIndex, sourceIp);
        }
        directModeActive = true;
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }

        List<Vm> sampledVms = sampleDistinctDirect(availableVms);
        Vm selected = sampledVms.getFirst();
        double minScore = directScore(selected, cloudletLength);
        for (int i = 1; i < sampledVms.size(); i++) {
            Vm candidate = sampledVms.get(i);
            double candidateScore = directScore(candidate, cloudletLength);
            if (candidateScore < minScore) {
                minScore = candidateScore;
                selected = candidate;
            }
        }

        directOutstandingServiceTime.merge(
                selected.getId(),
                directEstimatedServiceTime(selected, cloudletLength),
                Double::sum
        );
        return selected;
    }

    @Override
    public void onRequestComplete(Vm vm, double responseTimeSeconds) {
        if (!directModeActive) {
            stats.requestCompleted(vm, responseTimeSeconds);
        }
    }

    @Override
    public void onRequestComplete(Cloudlet cloudlet, double responseTimeSeconds) {
        if (!directModeActive) {
            stats.requestCompleted(cloudlet.getVm(), responseTimeSeconds);
            return;
        }

        long vmId = cloudlet.getVm().getId();
        double completedServiceTime =
                directEstimatedServiceTime(cloudlet.getVm(), cloudlet.getLength());
        directOutstandingServiceTime.compute(
                vmId,
                (key, value) -> Math.max(
                        0,
                        (value == null ? 0 : value) - completedServiceTime
                )
        );
    }

    @Override
    public String getName() {
        return "P2C-LRT Hybrid";
    }

    @Override
    public void reset() {
        stats.reset();
        directOutstandingServiceTime.clear();
        directModeActive = false;
        random.setSeed(seed);
    }

    @Override
    public void reset(long seed) {
        this.seed = seed;
        stats.reset();
        directOutstandingServiceTime.clear();
        directModeActive = false;
        random.setSeed(seed);
    }

    private List<Vm> sampleDistinct(List<Vm> availableVms) {
        int sampleSize = Math.min(choices, availableVms.size());
        Set<Integer> selectedIndices = new LinkedHashSet<>(sampleSize);
        while (selectedIndices.size() < sampleSize) {
            selectedIndices.add(random.nextInt(availableVms.size()));
        }

        List<Vm> sample = new ArrayList<>(sampleSize);
        for (int index : selectedIndices) {
            sample.add(availableVms.get(index));
        }
        return sample;
    }

    private List<Vm> sampleDistinctDirect(List<Vm> availableVms) {
        int sampleSize = Math.min(choices, availableVms.size());
        List<Vm> sample = new ArrayList<>(availableVms);
        Collections.shuffle(sample, random);
        return sample.subList(0, sampleSize);
    }

    private double directScore(Vm vm, long cloudletLength) {
        return directOutstandingServiceTime.getOrDefault(vm.getId(), 0.0)
                + directEstimatedServiceTime(vm, cloudletLength);
    }

    private double directEstimatedServiceTime(Vm vm, long cloudletLength) {
        double mips = Math.max(1.0, vm.getProcessor().getMips());
        return Math.max(0, cloudletLength) / mips;
    }

    /*
     * Previous capacity-aware implementation retained for comparison.
     * It estimated queued service time from exact cloudlet MI and VM MIPS.
     *
     * private static final double ALPHA = 0.5;
     * private static final double INITIAL_EMA_MS = 100.0;
     * private final int D;
     * private final Map<Long, Integer> activeConnections = new ConcurrentHashMap<>();
     * private final Map<Long, Double> emaResponseTime = new ConcurrentHashMap<>();
     * private final Map<Long, Double> outstandingServiceTime = new ConcurrentHashMap<>();
     *
     * @Override
     * public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp,
     *                    long cloudletLength, double arrivalTime) {
     *     if (availableVms.isEmpty()) {
     *         throw new IllegalStateException("No VMs available");
     *     }
     *     List<Vm> selectedVms = sampleDistinct(availableVms);
     *     Vm selected = selectedVms.getFirst();
     *     double minScore = score(selected, cloudletLength);
     *     for (Vm vm : selectedVms) {
     *         double vmScore = score(vm, cloudletLength);
     *         if (vmScore < minScore) {
     *             minScore = vmScore;
     *             selected = vm;
     *         }
     *     }
     *     activeConnections.merge(selected.getId(), 1, Integer::sum);
     *     outstandingServiceTime.merge(
     *             selected.getId(),
     *             estimatedServiceTime(selected, cloudletLength),
     *             Double::sum
     *     );
     *     return selected;
     * }
     *
     * @Override
     * public void onRequestComplete(Vm vm, double responseTimeSeconds) {
     *     long id = vm.getId();
     *     double previous = emaResponseTime.getOrDefault(id, INITIAL_EMA_MS);
     *     double updated = ALPHA * (responseTimeSeconds * 1000.0)
     *             + (1.0 - ALPHA) * previous;
     *     emaResponseTime.put(id, updated);
     *     activeConnections.computeIfPresent(id, (key, value) -> value > 0 ? value - 1 : 0);
     * }
     *
     * @Override
     * public void onRequestComplete(Cloudlet cloudlet, double responseTimeSeconds) {
     *     onRequestComplete(cloudlet.getVm(), responseTimeSeconds);
     *     long id = cloudlet.getVm().getId();
     *     double completedServiceTime =
     *             estimatedServiceTime(cloudlet.getVm(), cloudlet.getLength());
     *     outstandingServiceTime.compute(
     *             id,
     *             (key, value) -> Math.max(0, (value == null ? 0 : value) - completedServiceTime)
     *     );
     * }
     *
     * private double score(Vm vm, long cloudletLength) {
     *     double queuedServiceTime =
     *             outstandingServiceTime.getOrDefault(vm.getId(), 0.0);
     *     return queuedServiceTime + estimatedServiceTime(vm, cloudletLength);
     * }
     *
     * private double estimatedServiceTime(Vm vm, long cloudletLength) {
     *     if (cloudletLength <= 0) {
     *         return emaResponseTime.getOrDefault(vm.getId(), INITIAL_EMA_MS) / 1000.0;
     *     }
     *     double mips = Math.max(1.0, vm.getProcessor().getMips());
     *     return cloudletLength / mips;
     * }
     */
}
