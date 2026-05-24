package org.unibuc.algorithms;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;
import org.unibuc.core.SimulationConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class P2CLRTPolicy implements LoadBalancingPolicy {
    private final Random random = new Random();
    private long seed;
    private static final double ALPHA = 0.5;
    private static final double INITIAL_EMA_MS = 100.0;
    private final int D;
    private final Map<Long, Integer> activeConnections = new ConcurrentHashMap<>();
    private final Map<Long, Double>  emaResponseTime   = new ConcurrentHashMap<>();
    private final Map<Long, Double>  outstandingServiceTime = new ConcurrentHashMap<>();
    public P2CLRTPolicy(long seed) {
        this(SimulationConfig.P2C_LRT_CHOICES, seed);
    }

    public P2CLRTPolicy(int choices, long seed) {
        this.D = Math.max(2, choices);
        reset(seed);
    }

    public P2CLRTPolicy() {
        this(SimulationConfig.P2C_LRT_CHOICES, SimulationConfig.DEFAULT_POLICY_SEED);
    }

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
        List<Vm> selectedVms = sampleDistinct(availableVms);
        Vm selected = selectedVms.getFirst();
        double minScore = score(selected, cloudletLength);
        for (Vm vm : selectedVms) {
            double vmScore = score(vm, cloudletLength);
            if (vmScore < minScore) {
                minScore = vmScore;
                selected = vm;
            }
        }
        activeConnections.merge(selected.getId(), 1, Integer::sum);
        outstandingServiceTime.merge(selected.getId(), estimatedServiceTime(selected, cloudletLength), Double::sum);
        return selected;
    }

    @Override
    public void onRequestComplete(Vm vm, double responseTimeSeconds) {
        long id = vm.getId();
        double prev   = emaResponseTime.getOrDefault(id, INITIAL_EMA_MS);
        double newEma = ALPHA * (responseTimeSeconds * 1000.0) + (1.0 - ALPHA) * prev;
        emaResponseTime.put(id, newEma);
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
    public String getName() { return "P2C-LRT Hybrid"; }

    @Override
    public void reset() {
        activeConnections.clear();
        emaResponseTime.clear();
        outstandingServiceTime.clear();
        random.setSeed(seed);
    }

    @Override
    public void reset(long seed) {
        this.seed = seed;
        activeConnections.clear();
        emaResponseTime.clear();
        outstandingServiceTime.clear();
        random.setSeed(seed);
    }

    private List<Vm> sampleDistinct(List<Vm> availableVms) {
        int choices = Math.min(D, availableVms.size());
        List<Vm> sample = new ArrayList<>(availableVms);
        Collections.shuffle(sample, random);
        return sample.subList(0, choices);
    }
}
