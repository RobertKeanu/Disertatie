package org.unibuc.core;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.List;
import java.util.Map;

public interface LoadBalancingPolicy {
    double DEFAULT_OBSERVED_LATENCY_SECONDS = 1.0;
    double MIN_OBSERVED_LATENCY_SECONDS = 1.0e-6;

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

    static void ensureObservedVms(
            List<Vm> vms,
            Map<Long, Integer> activeRequests,
            Map<Long, Double> latencySums,
            Map<Long, Long> completedRequests) {
        for (Vm vm : vms) {
            long id = vm.getId();
            activeRequests.putIfAbsent(id, 0);
            latencySums.putIfAbsent(id, 0.0);
            completedRequests.putIfAbsent(id, 0L);
        }
    }

    static void observedRequestStarted(
            Vm vm,
            Map<Long, Integer> activeRequests) {
        activeRequests.merge(vm.getId(), 1, Integer::sum);
    }

    static void observedRequestCompleted(
            Vm vm,
            double responseTimeSeconds,
            Map<Long, Integer> activeRequests,
            Map<Long, Double> latencySums,
            Map<Long, Long> completedRequests) {
        long id = vm.getId();
        activeRequests.compute(id, (key, value) ->
                value == null || value <= 0 ? 0 : value - 1);

        if (Double.isFinite(responseTimeSeconds) && responseTimeSeconds >= 0) {
            latencySums.merge(
                    id,
                    Math.max(MIN_OBSERVED_LATENCY_SECONDS, responseTimeSeconds),
                    Double::sum
            );
            completedRequests.merge(id, 1L, Long::sum);
        }
    }

    static double observedScore(
            Vm vm,
            Map<Long, Integer> activeRequests,
            Map<Long, Double> latencySums,
            Map<Long, Long> completedRequests) {
        return averageObservedLatency(vm, latencySums, completedRequests)
                * (activeRequests.getOrDefault(vm.getId(), 0) + 1.0);
    }

    static void clearObservedStats(Map<?, ?>... maps) {
        for (Map<?, ?> map : maps) {
            map.clear();
        }
    }

    private static double averageObservedLatency(
            Vm vm,
            Map<Long, Double> latencySums,
            Map<Long, Long> completedRequests) {
        long completed = completedRequests.getOrDefault(vm.getId(), 0L);
        if (completed > 0) {
            return latencySums.getOrDefault(vm.getId(), 0.0) / completed;
        }
        return globalAverageObservedLatency(latencySums, completedRequests);
    }

    private static double globalAverageObservedLatency(
            Map<Long, Double> latencySums,
            Map<Long, Long> completedRequests) {
        double totalLatency = latencySums.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        long totalCompleted = completedRequests.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        return totalCompleted == 0
                ? DEFAULT_OBSERVED_LATENCY_SECONDS
                : totalLatency / totalCompleted;
    }
}
