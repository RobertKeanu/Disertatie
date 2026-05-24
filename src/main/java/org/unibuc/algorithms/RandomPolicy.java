package org.unibuc.algorithms;

import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;
import org.unibuc.core.SimulationConfig;

import java.util.List;
import java.util.Random;

public class RandomPolicy implements LoadBalancingPolicy {

    private final Random random = new Random();
    private long seed;

    public RandomPolicy(long seed) {
        reset(seed);
    }

    public RandomPolicy() {
        this(SimulationConfig.DEFAULT_POLICY_SEED);
    }

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }
        return availableVms.get(random.nextInt(availableVms.size()));
    }

    @Override
    public String getName() {
        return "Random";
    }

    @Override
    public void reset() {
        random.setSeed(seed);
    }

    @Override
    public void reset(long seed) {
        this.seed = seed;
        random.setSeed(seed);
    }
}
