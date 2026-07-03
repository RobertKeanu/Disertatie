package org.unibuc.algorithms;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import org.unibuc.core.LoadBalancingPolicy;
import org.unibuc.core.SimulationConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class P2CLRTPolicy implements LoadBalancingPolicy {
    private final Random random = new Random();
    private final ObservedBackendStats stats = new ObservedBackendStats();
    private final Map<Long, Double> directOutstandingServiceTime =
            new HashMap<>();
    private final int choices;
    private boolean directModeActive;
    private long seed;

    public P2CLRTPolicy(long seed) {
        this(SimulationConfig.P2C_LRT_CHOICES, seed);
    }

    public P2CLRTPolicy(int choices, long seed) {
        this.choices = Math.max(2, choices);
        reset(seed);
    }

    public P2CLRTPolicy() {
        this(SimulationConfig.P2C_LRT_CHOICES, SimulationConfig.DEFAULT_POLICY_SEED);
    }

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp) {
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }

        stats.ensureVms(availableVms);
        List<Vm> sampledVms = sampleDistinct(availableVms);
        Vm selected = sampledVms.getFirst();
        double minScore = stats.score(selected);

        for (int i = 1; i < sampledVms.size(); i++) {
            Vm candidate = sampledVms.get(i);
            double candidateScore = stats.score(candidate);
            if (candidateScore < minScore) {
                minScore = candidateScore;
                selected = candidate;
            }
        }

        stats.requestStarted(selected);
        return selected;
    }

    @Override
    public Vm selectVm(List<Vm> availableVms, int requestIndex, String sourceIp,
                       long cloudletLength, double arrivalTime) {
        if (!SimulationConfig.USE_DIRECT_POLICY_INFORMATION) {
            return selectVm(availableVms, requestIndex, sourceIp);
        }
        directModeActive = true;
        if (availableVms.isEmpty()) {
            throw new IllegalStateException("No VMs available");
        }

        List<Vm> sampledVms = sampleDistinctDirect(availableVms);
        Vm selected = sampledVms.getFirst();
        double minScore = directScore(selected, cloudletLength);
        for (int i = 1; i < sampledVms.size(); i++) {
            Vm candidate = sampledVms.get(i);
            double candidateScore = directScore(candidate, cloudletLength);
            if (candidateScore < minScore) {
                minScore = candidateScore;
                selected = candidate;
            }
        }

        directRequestStarted(selected, cloudletLength);
        return selected;
    }

    @Override
    public void onRequestComplete(Vm vm, double responseTimeSeconds) {
        if (!directModeActive) {
            stats.requestCompleted(vm, responseTimeSeconds);
        }
    }

    @Override
    public void onRequestComplete(Cloudlet cloudlet, double responseTimeSeconds) {
        if (!directModeActive) {
            stats.requestCompleted(cloudlet.getVm(), responseTimeSeconds);
            return;
        }

        directRequestCompleted(cloudlet);
    }

    @Override
    public String getName() {
        return "P2C-LRT Hybrid";
    }

    @Override
    public void reset() {
        stats.reset();
        directOutstandingServiceTime.clear();
        directModeActive = false;
        random.setSeed(seed);
    }

    @Override
    public void reset(long seed) {
        this.seed = seed;
        stats.reset();
        directOutstandingServiceTime.clear();
        directModeActive = false;
        random.setSeed(seed);
    }

    private List<Vm> sampleDistinct(List<Vm> availableVms) {
        int sampleSize = Math.min(choices, availableVms.size());
        Set<Integer> selectedIndices = new LinkedHashSet<>(sampleSize);
        while (selectedIndices.size() < sampleSize) {
            selectedIndices.add(random.nextInt(availableVms.size()));
        }

        List<Vm> sample = new ArrayList<>(sampleSize);
        for (int index : selectedIndices) {
            sample.add(availableVms.get(index));
        }
        return sample;
    }

    private List<Vm> sampleDistinctDirect(List<Vm> availableVms) {
        int sampleSize = Math.min(choices, availableVms.size());
        List<Vm> sample = new ArrayList<>(availableVms);
        Collections.shuffle(sample, random);
        return sample.subList(0, sampleSize);
    }

    private double directScore(Vm vm, long cloudletLength) {
        return directOutstandingServiceTime.getOrDefault(vm.getId(), 0.0)
                + estimatedServiceTime(vm, cloudletLength);
    }

    private void directRequestStarted(Vm vm, long cloudletLength) {
        directOutstandingServiceTime.merge(
                vm.getId(),
                estimatedServiceTime(vm, cloudletLength),
                Double::sum
        );
    }

    private void directRequestCompleted(Cloudlet cloudlet) {
        long vmId = cloudlet.getVm().getId();
        double completedServiceTime =
                estimatedServiceTime(cloudlet.getVm(), cloudlet.getLength());
        directOutstandingServiceTime.compute(
                vmId,
                (key, value) -> Math.max(
                        0,
                        (value == null ? 0 : value) - completedServiceTime
                )
        );
    }

    private double estimatedServiceTime(Vm vm, long cloudletLength) {
        double mips = Math.max(1.0, vm.getProcessor().getMips());
        return Math.max(0, cloudletLength) / mips;
    }
}
