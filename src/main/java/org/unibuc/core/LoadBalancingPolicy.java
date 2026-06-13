package org.unibuc.core;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.List;

public interface LoadBalancingPolicy {
    Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp);

    default Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp,
                        long cloudletLength, double arrivalTime) {
        return selectVm(availableVms, requestIndex, sourceIp);
    }

    String getName();
    default void reset() {}
    default void reset(long seed) { reset(); }
    default void onRequestComplete(Vm vm, double responseTimeSeconds) {}
    default void onRequestComplete(Cloudlet cloudlet, double responseTimeSeconds) {
        onRequestComplete(cloudlet.getVm(), responseTimeSeconds);
    }
}
