package org.unibuc.algorithms;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;
import org.unibuc.core.SimulationConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LeastReponseTimePolicy implements LoadBalancingPolicy {
    private static final double EPSILON = 1.0e-9;

    private final ObservedBackendStats stats = new ObservedBackendStats();
    private final Map<Long, Double> directOutstandingServiceTime = new HashMap<>();
    private boolean directModeActive;
    private int tieBreaker;

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }

        stats.ensureVms(availableVms);
        double minScore = Double.MAX_VALUE;
        List<Vm> candidates = new ArrayList<>();

        for (Vm vm : availableVms) {
            double vmScore = stats.score(vm);
            if (vmScore < minScore - EPSILON) {
                minScore = vmScore;
                candidates.clear();
                candidates.add(vm);
            } else if (Math.abs(vmScore - minScore) <= EPSILON) {
                candidates.add(vm);
            }
        }

        Vm selected = candidates.get(Math.floorMod(tieBreaker++, candidates.size()));
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

        double minScore = Double.MAX_VALUE;
        List<Vm> candidates = new ArrayList<>();
        for (Vm vm : availableVms) {
            double vmScore = directScore(vm, cloudletLength);
            if (vmScore < minScore - EPSILON) {
                minScore = vmScore;
                candidates.clear();
                candidates.add(vm);
            } else if (Math.abs(vmScore - minScore) <= EPSILON) {
                candidates.add(vm);
            }
        }

        Vm selected = candidates.get(Math.floorMod(tieBreaker++, candidates.size()));
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
        return "Least Response Time";
    }

    @Override
    public void reset() {
        stats.reset();
        directOutstandingServiceTime.clear();
        directModeActive = false;
        tieBreaker = 0;
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
     * private static final double ALPHA = 0.5;
     * private static final double INITIAL_EMA_MS = 100.0;
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
     *
     *     double minScore = Double.MAX_VALUE;
     *     List<Vm> candidates = new ArrayList<>();
     *     for (Vm vm : availableVms) {
     *         double vmScore = score(vm, cloudletLength);
     *         if (vmScore < minScore - EPSILON) {
     *             minScore = vmScore;
     *             candidates.clear();
     *             candidates.add(vm);
     *         } else if (Math.abs(vmScore - minScore) <= EPSILON) {
     *             candidates.add(vm);
     *         }
     *     }
     *     Vm selected = candidates.get(Math.floorMod(tieBreaker++, candidates.size()));
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
