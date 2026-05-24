package org.unibuc.algorithms;

import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;
import org.unibuc.core.SimulationConfig;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

//Power Of 2 Choices Policy
public class P2CPolicy implements LoadBalancingPolicy {
    private final Random random = new Random();
    private long seed;
    private final Map<Long, Integer> activeConnections = new ConcurrentHashMap<>();

    public P2CPolicy(long seed) {
        reset(seed);
    }
    public P2CPolicy() {
        this(SimulationConfig.DEFAULT_POLICY_SEED);
    }

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) throw new IllegalStateException("No VMs available");
        if (availableVms.size() == 1) return availableVms.getFirst();

        // Pick two distinct VMs at random
        int idxA = random.nextInt(availableVms.size());
        int idxB;
        do {
            idxB = random.nextInt(availableVms.size());
        } while (idxB == idxA);

        Vm vmA = availableVms.get(idxA);
        Vm vmB = availableVms.get(idxB);

        Vm chosen = connections(vmA) <= connections(vmB) ? vmA : vmB;
        activeConnections.merge(chosen.getId(), 1, Integer::sum);
        return chosen;
    }

    public void onRequestComplete(Vm vm) {
        onRequestComplete(vm, 0);
    }

    @Override
    public void onRequestComplete(Vm vm, double responseTimeSeconds) {
        activeConnections.computeIfPresent(vm.getId(),
                (id, count) -> count > 0 ? count - 1 : 0);
    }

    private int connections(Vm vm) {
        return activeConnections.getOrDefault(vm.getId(), 0);
    }

    @Override
    public String getName() { return "Power of Two Choices"; }

    @Override
    public void reset() {
        activeConnections.clear();
        random.setSeed(seed);
    }

    @Override
    public void reset(long seed) {
        this.seed = seed;
        activeConnections.clear();
        random.setSeed(seed);
    }
}
