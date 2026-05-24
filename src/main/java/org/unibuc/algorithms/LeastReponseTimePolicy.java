package org.unibuc.algorithms;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LeastReponseTimePolicy implements LoadBalancingPolicy {
    private static final double ALPHA = 0.5;
    private static final double INITIAL_EMA_MS = 100.0;
    private static final double EPSILON = 1.0e-9;

    private final Map<Long, Integer> activeConnections = new ConcurrentHashMap<>();
    private final Map<Long, Double>  emaResponseTime   = new ConcurrentHashMap<>();
    private final Map<Long, Double>  outstandingServiceTime = new ConcurrentHashMap<>();
    private int tieBreaker;

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp){
        return selectVm(availableVms, requestIndex, sourceIp, 0, 0);
    }

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp,
                       long cloudletLength, double arrivalTime){
        if(availableVms.isEmpty()){
            throw new IllegalStateException("No VMs available");
        }

        double minScore = Double.MAX_VALUE;
        List<Vm> candidates = new ArrayList<>();
        for(Vm vm : availableVms){
            double vmScore = score(vm, cloudletLength);
            if(vmScore < minScore - EPSILON){
                minScore = vmScore;
                candidates.clear();
                candidates.add(vm);
            } else if (Math.abs(vmScore - minScore) <= EPSILON) {
                candidates.add(vm);
            }
        }
        Vm selected = candidates.get(Math.floorMod(tieBreaker++, candidates.size()));
        activeConnections.merge(selected.getId(), 1, Integer::sum);
        outstandingServiceTime.merge(selected.getId(), estimatedServiceTime(selected, cloudletLength), Double::sum);
        return selected;
    }

    @Override
    public void onRequestComplete(Vm vm, double responseTimeSeconds) {
        long id = vm.getId();

        // Update EMA response time
        double prevEma = emaResponseTime.getOrDefault(id, INITIAL_EMA_MS);
        double newEma  = ALPHA * (responseTimeSeconds * 1000.0) + (1.0 - ALPHA) * prevEma;
        emaResponseTime.put(id, newEma);

        // Decrement active connections
        activeConnections.computeIfPresent(id, (k, v) -> v > 0 ? v - 1 : 0);
    }

    @Override
    public void onRequestComplete(Cloudlet cloudlet, double responseTimeSeconds) {
        onRequestComplete(cloudlet.getVm(), responseTimeSeconds);
        long id = cloudlet.getVm().getId();
        double completedServiceTime = estimatedServiceTime(cloudlet.getVm(), cloudlet.getLength());
        outstandingServiceTime.compute(id, (k, v) -> Math.max(0, (v == null ? 0 : v) - completedServiceTime));
    }

    private double score(Vm vm, long cloudletLength) {
        double queuedServiceTime = outstandingServiceTime.getOrDefault(vm.getId(), 0.0);
        return queuedServiceTime + estimatedServiceTime(vm, cloudletLength);
    }

    private double estimatedServiceTime(Vm vm, long cloudletLength) {
        if (cloudletLength <= 0) {
            return emaResponseTime.getOrDefault(vm.getId(), INITIAL_EMA_MS) / 1000.0;
        }
        double mips = Math.max(1.0, vm.getProcessor().getMips());
        return cloudletLength / mips;
    }

    @Override
    public String getName() { return "Least Response Time"; }

    @Override
    public void reset() {
        activeConnections.clear();
        emaResponseTime.clear();
        outstandingServiceTime.clear();
        tieBreaker = 0;
    }
}
