package org.unibuc.metrics;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.List;

public class AggregatedMetrics {

    private final String algorithmName;
    private final int    runs;

    private final DescriptiveStatistics makespan    = new DescriptiveStatistics();
    private final DescriptiveStatistics throughput  = new DescriptiveStatistics();
    private final DescriptiveStatistics avgLatency  = new DescriptiveStatistics();
    private final DescriptiveStatistics queueWait   = new DescriptiveStatistics();
    private final DescriptiveStatistics p95Latency  = new DescriptiveStatistics();
    private final DescriptiveStatistics p99Latency  = new DescriptiveStatistics();
    private final DescriptiveStatistics loadImbalance = new DescriptiveStatistics();
    private final DescriptiveStatistics requestImbalance = new DescriptiveStatistics();
    private final DescriptiveStatistics workImbalance = new DescriptiveStatistics();
    private final DescriptiveStatistics completedRequests = new DescriptiveStatistics();

    public AggregatedMetrics(String algorithmName, List<MetricsCollector> runResults) {
        this.algorithmName = algorithmName;
        this.runs = runResults.size();
        for (MetricsCollector m : runResults) {
            makespan  .addValue(m.getMakespan());
            throughput.addValue(m.getThroughput());
            avgLatency.addValue(m.getAverageLatency());
            queueWait .addValue(m.getAverageQueueWaitTime());
            p95Latency.addValue(m.getP95Latency());
            p99Latency.addValue(m.getP99Latency());
            loadImbalance.addValue(m.getNormalizedLoadImbalance());
            requestImbalance.addValue(m.getRequestImbalance());
            workImbalance.addValue(m.getWorkImbalance());
            completedRequests.addValue(m.getTotalRequests());
        }
    }


    public String getAlgorithmName() { return algorithmName; }
    public int    getRuns()          { return runs; }

    public double getMakespanMean()     { return makespan.getMean(); }
    public double getMakespanStdDev()   { return makespan.getStandardDeviation(); }

    public double getThroughputMean()   { return throughput.getMean(); }
    public double getThroughputStdDev() { return throughput.getStandardDeviation(); }

    public double getAvgLatencyMean()   { return avgLatency.getMean(); }
    public double getAvgLatencyStdDev() { return avgLatency.getStandardDeviation(); }

    public double getQueueWaitMean()    { return queueWait.getMean(); }
    public double getQueueWaitStdDev()  { return queueWait.getStandardDeviation(); }

    public double getP95LatencyMean()   { return p95Latency.getMean(); }
    public double getP95LatencyStdDev() { return p95Latency.getStandardDeviation(); }

    public double getP99LatencyMean()   { return p99Latency.getMean(); }
    public double getP99LatencyStdDev() { return p99Latency.getStandardDeviation(); }

    public double getImbalanceMean()    { return loadImbalance.getMean(); }
    public double getImbalanceStdDev()  { return loadImbalance.getStandardDeviation(); }

    public double getRequestImbalanceMean()   { return requestImbalance.getMean(); }
    public double getRequestImbalanceStdDev() { return requestImbalance.getStandardDeviation(); }

    public double getWorkImbalanceMean()   { return workImbalance.getMean(); }
    public double getWorkImbalanceStdDev() { return workImbalance.getStandardDeviation(); }

    public double getCompletedRequestsMean()   { return completedRequests.getMean(); }
    public double getCompletedRequestsStdDev() { return completedRequests.getStandardDeviation(); }
    public double getCompletedRequestsMin()    { return completedRequests.getMin(); }


    @Override
    public String toString() {
        return String.format(
                """
                ┌─ %s  (%d runs) ──────────────────────────
                │  Makespan      : %.3f ± %.3f s
                │  Throughput    : %.3f ± %.3f req/s
                │  Avg latency   : %.3f ± %.3f s
                │  Avg wait      : %.3f ± %.3f s
                │  p95 latency   : %.3f ± %.3f s
                │  p99 latency   : %.3f ± %.3f s
                │  Load imbalance: %.3f ± %.3f s
                │  Req imbalance : %.3f ± %.3f
                │  Work imbalance: %.3f ± %.3f MI
                └──────────────────────────────────────────────
                """,
                algorithmName, runs,
                getMakespanMean(),    getMakespanStdDev(),
                getThroughputMean(),  getThroughputStdDev(),
                getAvgLatencyMean(),  getAvgLatencyStdDev(),
                getQueueWaitMean(),   getQueueWaitStdDev(),
                getP95LatencyMean(),  getP95LatencyStdDev(),
                getP99LatencyMean(),  getP99LatencyStdDev(),
                getImbalanceMean(),   getImbalanceStdDev(),
                getRequestImbalanceMean(), getRequestImbalanceStdDev(),
                getWorkImbalanceMean(), getWorkImbalanceStdDev()
        );
    }
}
