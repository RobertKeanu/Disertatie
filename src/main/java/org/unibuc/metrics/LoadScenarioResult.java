package org.unibuc.metrics;

public record LoadScenarioResult(
        String loadLabel,
        double loadPercentage,
        double targetRequestsPerSecond,
        double meanInterarrivalTimeSeconds,
        AggregatedMetrics metrics) {
}
