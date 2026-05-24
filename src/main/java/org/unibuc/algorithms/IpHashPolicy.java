package org.unibuc.algorithms;

import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;

import java.util.List;
public class IpHashPolicy implements LoadBalancingPolicy {

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }
        int index = Math.floorMod(sourceIp.hashCode(), availableVms.size());
        return availableVms.get(index);
    }

    @Override
    public String getName() {
        return "IP Hash";
    }
}
 
