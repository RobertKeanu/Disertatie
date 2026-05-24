package org.unibuc.algorithms;

import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LeastConnectionsPolicy implements LoadBalancingPolicy {
    private final Map<Long, Integer> activeConnections = new ConcurrentHashMap<>();
    private int tieBreaker;

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }

        int minConnections = Integer.MAX_VALUE;
        List<Vm> candidates = new java.util.ArrayList<>();

        for (Vm vm : availableVms) {
            int connections = activeConnections.getOrDefault(vm.getId(), 0);
            if (connections < minConnections) {
                minConnections = connections;
                candidates.clear();
                candidates.add(vm);
            } else if (connections == minConnections) {
                candidates.add(vm);
            }
        }

        Vm selected = candidates.get(Math.floorMod(tieBreaker++, candidates.size()));
        activeConnections.merge(selected.getId(), 1, Integer::sum);
        return selected;
    }

    public void onRequestComplete(Vm vm) {
        onRequestComplete(vm, 0);
    }

    @Override
    public void onRequestComplete(Vm vm, double responseTimeSeconds) {
        activeConnections.computeIfPresent(vm.getId(),
                (id, count) -> count > 0 ? count - 1 : 0);
    }

    @Override
    public String getName() {
        return "Least Connections";
    }

    @Override
    public void reset() {
        activeConnections.clear();
        tieBreaker = 0;
    }
}
 
