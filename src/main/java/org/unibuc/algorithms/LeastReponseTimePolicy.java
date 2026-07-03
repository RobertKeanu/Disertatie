package org.unibuc.algorithms;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;
import org.unibuc.core.SimulationConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.unibuc.core.LoadBalancingPolicy.clearObservedStats;
import static org.unibuc.core.LoadBalancingPolicy.ensureObservedVms;
import static org.unibuc.core.LoadBalancingPolicy.observedRequestCompleted;
import static org.unibuc.core.LoadBalancingPolicy.observedRequestStarted;
import static org.unibuc.core.LoadBalancingPolicy.observedScore;

public class LeastReponseTimePolicy implements LoadBalancingPolicy {
    private static final double EPSILON = 1.0e-9;

    private final Map<Long, Integer> activeRequests = new HashMap<>();
    private final Map<Long, Double> latencySums = new HashMap<>();
    private final Map<Long, Long> completedRequests = new HashMap<>();
    private final Map<Long, Double> directOutstandingServiceTime =
            new HashMap<>();
    private boolean directModeActive;
    private int tieBreaker;

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }

        ensureObservedVms(
                availableVms,
                activeRequests,
                latencySums,
                completedRequests
        );
        double minScore = Double.MAX_VALUE;
        List<Vm> candidates = new ArrayList<>();

        for (Vm vm : availableVms) {
            double vmScore = observedScore(
                    vm,
                    activeRequests,
                    latencySums,
                    completedRequests
            );
            if (vmScore < minScore - EPSILON) {
                minScore = vmScore;
                candidates.clear();
                candidates.add(vm);
            } else if (Math.abs(vmScore - minScore) <= EPSILON) {
                candidates.add(vm);
            }
        }

        Vm selected = candidates.get(Math.floorMod(tieBreaker++, candidates.size()));
        observedRequestStarted(selected, activeRequests);
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
        directRequestStarted(selected, cloudletLength);
        return selected;
    }

    @Override
    public void onRequestComplete(Vm vm, double responseTimeSeconds) {
        if (!directModeActive) {
            observedRequestCompleted(
                    vm,
                    responseTimeSeconds,
                    activeRequests,
                    latencySums,
                    completedRequests
            );
        }
    }

    @Override
    public void onRequestComplete(Cloudlet cloudlet, double responseTimeSeconds) {
        if (!directModeActive) {
            observedRequestCompleted(
                    cloudlet.getVm(),
                    responseTimeSeconds,
                    activeRequests,
                    latencySums,
                    completedRequests
            );
            return;
        }

        directRequestCompleted(cloudlet);
    }

    @Override
    public String getName() {
        return "Least Response Time";
    }

    @Override
    public void reset() {
        clearObservedStats(activeRequests, latencySums, completedRequests);
        directOutstandingServiceTime.clear();
        directModeActive = false;
        tieBreaker = 0;
    }

    private double directScore(Vm vm, long cloudletLength) {
        return directOutstandingServiceTime.getOrDefault(vm.getId(), 0.0)
                + estimatedServiceTime(vm, cloudletLength);
    }

    private void directRequestStarted(Vm vm, long cloudletLength) {
        directOutstandingServiceTime.merge(
                vm.getId(),
                estimatedServiceTime(vm, cloudletLength),
                Double::sum
        );
    }

    private void directRequestCompleted(Cloudlet cloudlet) {
        long vmId = cloudlet.getVm().getId();
        double completedServiceTime =
                estimatedServiceTime(cloudlet.getVm(), cloudlet.getLength());
        directOutstandingServiceTime.compute(
                vmId,
                (key, value) -> Math.max(
                        0,
                        (value == null ? 0 : value) - completedServiceTime
                )
        );
    }

    private double estimatedServiceTime(Vm vm, long cloudletLength) {
        double mips = Math.max(1.0, vm.getProcessor().getMips());
        return Math.max(0, cloudletLength) / mips;
    }
}
