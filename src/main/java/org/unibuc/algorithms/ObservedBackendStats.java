package org.unibuc.algorithms;

import org.cloudsimplus.vms.Vm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ObservedBackendStats {
    private static final double DEFAULT_LATENCY_SECONDS = 1.0;
    private static final double MIN_LATENCY_SECONDS = 1.0e-6;

    private final Map<Long, Integer> activeRequests = new HashMap<>();
    private final Map<Long, Double> latencySums = new HashMap<>();
    private final Map<Long, Long> completedRequests = new HashMap<>();

    void ensureVms(List<Vm> vms) {
        for (Vm vm : vms) {
            long id = vm.getId();
            activeRequests.putIfAbsent(id, 0);
            latencySums.putIfAbsent(id, 0.0);
            completedRequests.putIfAbsent(id, 0L);
        }
    }

    void requestStarted(Vm vm) {
        activeRequests.merge(vm.getId(), 1, Integer::sum);
    }

    void requestCompleted(Vm vm, double responseTimeSeconds) {
        long id = vm.getId();
        activeRequests.compute(id, (key, value) ->
                value == null || value <= 0 ? 0 : value - 1);

        if (Double.isFinite(responseTimeSeconds) && responseTimeSeconds >= 0) {
            latencySums.merge(id, Math.max(MIN_LATENCY_SECONDS, responseTimeSeconds), Double::sum);
            completedRequests.merge(id, 1L, Long::sum);
        }
    }

    int activeRequests(Vm vm) {
        return activeRequests.getOrDefault(vm.getId(), 0);
    }

    double averageLatencySeconds(Vm vm) {
        long completed = completedRequests.getOrDefault(vm.getId(), 0L);
        if (completed > 0) {
            return latencySums.getOrDefault(vm.getId(), 0.0) / completed;
        }
        return globalAverageLatencySeconds();
    }

    double score(Vm vm) {
        return averageLatencySeconds(vm) * (activeRequests(vm) + 1.0);
    }

    void reset() {
        activeRequests.clear();
        latencySums.clear();
        completedRequests.clear();
    }

    private double globalAverageLatencySeconds() {
        double totalLatency = latencySums.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        long totalCompleted = completedRequests.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        return totalCompleted == 0
                ? DEFAULT_LATENCY_SECONDS
                : totalLatency / totalCompleted;
    }
}
