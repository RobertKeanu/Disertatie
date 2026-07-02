package org.unibuc.algorithms;

import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;
import org.unibuc.core.SimulationConfig;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeightedRoundRobinPolicy implements LoadBalancingPolicy {
    private static final int MAX_DYNAMIC_WEIGHT = 10;

    private final ObservedBackendStats stats = new ObservedBackendStats();
    private final Map<Long, Integer> currentWeights = new HashMap<>();
    private int[] directCurrentWeights = new int[0];

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }

        stats.ensureVms(availableVms);
        int[] weights = calculateDynamicWeights(availableVms);
        int totalWeight = 0;
        Vm selected = availableVms.getFirst();
        int selectedCurrentWeight = Integer.MIN_VALUE;

        for (int i = 0; i < availableVms.size(); i++) {
            Vm vm = availableVms.get(i);
            int weight = weights[i];
            totalWeight += weight;

            int currentWeight = currentWeights.merge(vm.getId(), weight, Integer::sum);
            if (currentWeight > selectedCurrentWeight) {
                selected = vm;
                selectedCurrentWeight = currentWeight;
            }
        }

        currentWeights.merge(selected.getId(), -totalWeight, Integer::sum);
        stats.requestStarted(selected);
        return selected;
    }

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp,
                       long cloudletLength, double arrivalTime) {
        if (!SimulationConfig.USE_DIRECT_POLICY_INFORMATION) {
            return selectVm(availableVms, requestIndex, sourceIp);
        }
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }
        if (directCurrentWeights.length != availableVms.size()) {
            directCurrentWeights = new int[availableVms.size()];
        }

        int totalWeight = 0;
        int selectedIndex = 0;
        for (int i = 0; i < availableVms.size(); i++) {
            int weight = Math.max(
                    1,
                    SimulationConfig.WRR_WEIGHTS[
                            i % SimulationConfig.WRR_WEIGHTS.length
                    ]
            );
            totalWeight += weight;
            directCurrentWeights[i] += weight;
            if (directCurrentWeights[i] > directCurrentWeights[selectedIndex]) {
                selectedIndex = i;
            }
        }

        directCurrentWeights[selectedIndex] -= totalWeight;
        return availableVms.get(selectedIndex);
    }

    @Override
    public void onRequestComplete(Vm vm, double responseTimeSeconds) {
        stats.requestCompleted(vm, responseTimeSeconds);
    }

    @Override
    public String getName() {
        return SimulationConfig.USE_DIRECT_POLICY_INFORMATION
                ? "Weighted Round Robin"
                : "Adaptive Weighted Round Robin";
    }

    @Override
    public void reset() {
        stats.reset();
        currentWeights.clear();
        Arrays.fill(directCurrentWeights, 0);
    }

    private int[] calculateDynamicWeights(List<Vm> availableVms) {
        double[] qualities = new double[availableVms.size()];
        double maxQuality = 0;

        for (int i = 0; i < availableVms.size(); i++) {
            qualities[i] = 1.0 / stats.score(availableVms.get(i));
            maxQuality = Math.max(maxQuality, qualities[i]);
        }

        int[] weights = new int[availableVms.size()];
        for (int i = 0; i < availableVms.size(); i++) {
            double normalizedQuality = maxQuality == 0 ? 1.0 : qualities[i] / maxQuality;
            weights[i] = Math.max(1, (int) Math.round(MAX_DYNAMIC_WEIGHT * normalizedQuality));
        }
        return weights;
    }

    /*
     * private final int[] weights;
     * private int[] currentWeights;
     *
     * public WeightedRoundRobinPolicy() {
     *     this.weights = SimulationConfig.WRR_WEIGHTS;
     *     this.currentWeights = new int[0];
     * }
     *
     * @Override
     * public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
     *     if (availableVms.isEmpty()) {
     *         throw new IllegalStateException("No VMs available");
     *     }
     *
     *     if (currentWeights.length != availableVms.size()) {
     *         currentWeights = new int[availableVms.size()];
     *     }
     *
     *     int totalWeight = 0;
     *     int selectedIndex = 0;
     *     for (int i = 0; i < availableVms.size(); i++) {
     *         int weight = effectiveWeight(i);
     *         totalWeight += weight;
     *         currentWeights[i] += weight;
     *         if (currentWeights[i] > currentWeights[selectedIndex]) {
     *             selectedIndex = i;
     *         }
     *     }
     *
     *     currentWeights[selectedIndex] -= totalWeight;
     *     return availableVms.get(selectedIndex);
     * }
     *
     * @Override
     * public void reset() {
     *     Arrays.fill(currentWeights, 0);
     * }
     *
     * private int effectiveWeight(int vmIndex) {
     *     if (weights.length == 0) {
     *         return 1;
     *     }
     *     return Math.max(1, weights[vmIndex % weights.length]);
     * }
     */
}
