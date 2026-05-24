package org.unibuc.algorithms;

import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;
import org.unibuc.core.SimulationConfig;

import java.util.Arrays;
import java.util.List;

public class WeightedRoundRobinPolicy implements LoadBalancingPolicy {

    private final int[] weights;
    private int[] currentWeights;

    public WeightedRoundRobinPolicy() {
        this.weights = SimulationConfig.WRR_WEIGHTS;
        this.currentWeights = new int[0];
    }

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }

        if (currentWeights.length != availableVms.size()) {
            currentWeights = new int[availableVms.size()];
        }

        int totalWeight = 0;
        int selectedIndex = 0;
        for (int i = 0; i < availableVms.size(); i++) {
            int weight = effectiveWeight(i);
            totalWeight += weight;
            currentWeights[i] += weight;
            if (currentWeights[i] > currentWeights[selectedIndex]) {
                selectedIndex = i;
            }
        }

        currentWeights[selectedIndex] -= totalWeight;
        return availableVms.get(selectedIndex);
    }

    @Override
    public String getName() {
        return "Weighted Round Robin";
    }

    @Override
    public void reset() {
        Arrays.fill(currentWeights, 0);
    }

    private int effectiveWeight(int vmIndex) {
        if (weights.length == 0) {
            return 1;
        }
        return Math.max(1, weights[vmIndex % weights.length]);
    }
}
