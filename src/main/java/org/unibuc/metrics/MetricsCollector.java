package org.unibuc.metrics;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.*;
public class MetricsCollector {

    private final String algorithmName;
    private final List<Cloudlet> finishedCloudlets;

    private final Map<Long, Integer> requestsPerVm = new HashMap<>();
    private final Map<Long, Long> workPerVm = new HashMap<>();
    private final Map<Long, Double> serviceTimePerVm = new HashMap<>();

    public MetricsCollector(String algorithmName, List<Cloudlet> finishedCloudlets) {
        this(algorithmName, finishedCloudlets, List.of());
    }

    public MetricsCollector(String algorithmName, List<Cloudlet> finishedCloudlets, List<Vm> vms) {
        this.algorithmName = algorithmName;
        this.finishedCloudlets = new ArrayList<>(finishedCloudlets);
        buildPerVmCounts(vms);
    }

    private void buildPerVmCounts(List<Vm> vms) {
        for (Vm vm : vms) {
            requestsPerVm.put(vm.getId(), 0);
            workPerVm.put(vm.getId(), 0L);
            serviceTimePerVm.put(vm.getId(), 0.0);
        }

        for (Cloudlet c : finishedCloudlets) {
            long vmId = c.getVm().getId();
            requestsPerVm.merge(vmId, 1, Integer::sum);
            workPerVm.merge(vmId, c.getLength(), Long::sum);
            serviceTimePerVm.merge(vmId, getEstimatedServiceTime(c), Double::sum);
        }
    }

    public double getMakespan() {
        double firstArrival = getFirstArrivalTime();
        double lastFinish = finishedCloudlets.stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max()
                .orElse(0);
        return Math.max(0, lastFinish - firstArrival);
    }

    public double getThroughput() {
        double makespan = getMakespan();
        return makespan > 0 ? finishedCloudlets.size() / makespan : 0;
    }

    public double getAverageLatency() {
        return finishedCloudlets.stream()
                .mapToDouble(this::getResponseTime)
                .average()
                .orElse(0);
    }

    public double getP95Latency() {
        return getPercentileLatency(95);
    }

    public double getP99Latency() {
        return getPercentileLatency(99);
    }

    private double getPercentileLatency(double percentile) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (Cloudlet c : finishedCloudlets) {
            stats.addValue(getResponseTime(c));
        }
        return stats.getPercentile(percentile);
    }

    public double getAverageQueueWaitTime() {
        return finishedCloudlets.stream()
                .mapToDouble(this::getQueueWaitTime)
                .average()
                .orElse(0);
    }

    public double getLoadImbalance() {
        return getRequestImbalance();
    }

    public double getRequestImbalance() {
        if (requestsPerVm.isEmpty()) return 0;
        DescriptiveStatistics stats = new DescriptiveStatistics();
        requestsPerVm.values().forEach(v -> stats.addValue(v));
        return stats.getStandardDeviation();
    }

    public double getNormalizedLoadImbalance() {
        if (serviceTimePerVm.isEmpty()) return 0;
        DescriptiveStatistics stats = new DescriptiveStatistics();
        serviceTimePerVm.values().forEach(v -> stats.addValue(v));
        return stats.getStandardDeviation();
    }

    public double getWorkImbalance() {
        if (workPerVm.isEmpty()) return 0;
        DescriptiveStatistics stats = new DescriptiveStatistics();
        workPerVm.values().forEach(v -> stats.addValue(v));
        return stats.getStandardDeviation();
    }

    public Map<Long, Integer> getRequestsPerVm() {
        return Collections.unmodifiableMap(requestsPerVm);
    }

    public Map<Long, Long> getWorkPerVm() {
        return Collections.unmodifiableMap(workPerVm);
    }

    public Map<Long, Double> getServiceTimePerVm() {
        return Collections.unmodifiableMap(serviceTimePerVm);
    }

    public int getTotalRequests() {
        return finishedCloudlets.size();
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public MetricsCollector withLabel(String label) {
        return new MetricsCollector(label, this.finishedCloudlets);
    }

    private double getFirstArrivalTime() {
        return finishedCloudlets.stream()
                .mapToDouble(this::getArrivalTime)
                .min()
                .orElse(0);
    }

    private double getResponseTime(Cloudlet cloudlet) {
        return Math.max(0, cloudlet.getFinishTime() - getArrivalTime(cloudlet));
    }

    private double getQueueWaitTime(Cloudlet cloudlet) {
        return Math.max(0, getResponseTime(cloudlet) - getEstimatedServiceTime(cloudlet));
    }

    private double getEstimatedServiceTime(Cloudlet cloudlet) {
        double mipsPerPe = cloudlet.getVm().getProcessor().getMips();
        long pes = Math.max(1, cloudlet.getPesNumber());
        double allocatedMips = Math.max(1.0, mipsPerPe * pes);
        return cloudlet.getTotalLength() / allocatedMips;
    }

    private double getArrivalTime(Cloudlet cloudlet) {
        double arrival = cloudlet.getDcArrivalTime();
        if (arrival < 0) {
            arrival = cloudlet.getBrokerArrivalTime();
        }
        if (arrival < 0) {
            arrival = cloudlet.getStartTime();
        }
        return Math.max(0, arrival);
    }

    @Override
    public String toString() {
        return String.format(
                """
                ┌─ %s ──────────────────────────────
                │  Requests completed : %d
                │  Makespan           : %.2f s
                │  Throughput         : %.2f req/s
                │  Avg latency        : %.2f s
                │  Avg queue wait     : %.2f s
                │  p95 latency        : %.2f s
                │  p99 latency        : %.2f s
                │  Load imbalance    : %.2f s
                │  Request imbalance : %.2f
                │  Raw work imbalance : %.2f MI
                └────────────────────────────────────────
                """,
                algorithmName,
                getTotalRequests(),
                getMakespan(),
                getThroughput(),
                getAverageLatency(),
                getAverageQueueWaitTime(),
                getP95Latency(),
                getP99Latency(),
                getNormalizedLoadImbalance(),
                getRequestImbalance(),
                getWorkImbalance()
        );
    }
}
