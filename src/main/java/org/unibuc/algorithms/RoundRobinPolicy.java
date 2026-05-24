package org.unibuc.algorithms;

import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinPolicy implements LoadBalancingPolicy {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available to handle request");
        }

        int index = Math.floorMod(counter.getAndIncrement(), availableVms.size());
        return availableVms.get(index);
    }

    @Override
    public String getName() {
        return "Round Robin";
    }

    @Override
    public void reset() {
        counter.set(0);
    }
}
 
