package org.unibuc.metrics;

public record LoadExperimentFailure(
        String loadLabel,
        double loadPercentage,
        String algorithm,
        int run,
        String status,
        int completedRequests,
        int expectedRequests,
        double wallTimeSeconds) {
}
