package org.unibuc.core;

import java.util.List;

/**
 * Configuration for the final progressive-load experiment.
 * The original SimulationConfig remains the source of infrastructure values.
 */
public final class FinalLoadExperimentConfig {
    public static final int CLOUDLET_COUNT = 10_000;
    public static final int RUNS_PER_ALGORITHM = 5;
    public static final String OUTPUT_DIR = "results/final_load_test";
    public static final long BASE_WORKLOAD_SEED = 20_000L;
    public static final long BASE_POLICY_SEED = 30_000L;

    /*
     * Pessimistic sensitivity scenario for a centralized load balancer that
     * queries VM state sequentially from a remote monitoring layer. This is
     * deliberately not the cost of scanning an in-memory Java collection.
     *
     * selection overhead =
     * base + inspected VMs * state read + comparisons * comparison cost
     */
    public static final double SELECTION_BASE_OVERHEAD_MICROSECONDS = 100.0;
    public static final double VM_STATE_READ_OVERHEAD_MICROSECONDS = 100_000.0;
    public static final double VM_COMPARISON_OVERHEAD_MICROSECONDS = 50.0;

    public static final List<LoadLevel> LOAD_LEVELS = List.of(
            new LoadLevel("25%", 0.25),
            new LoadLevel("50%", 0.50),
            new LoadLevel("75%", 0.75),
            new LoadLevel("90%", 0.90),
            new LoadLevel("110%", 1.10)
    );

    private FinalLoadExperimentConfig() {
    }

    public static double averageCloudletLengthMi() {
        return (SimulationConfig.CLOUDLET_LENGTH_MIN_MI
                + SimulationConfig.CLOUDLET_LENGTH_MAX_MI) / 2.0;
    }

    public static double totalVmCapacityMips() {
        double capacity = 0;
        for (int vmIndex = 0; vmIndex < SimulationConfig.VM_COUNT; vmIndex++) {
            long mipsPerPe = SimulationConfig.VM_MIPS_PER_VM[
                    vmIndex % SimulationConfig.VM_MIPS_PER_VM.length
            ];
            capacity += mipsPerPe * SimulationConfig.VM_PES;
        }
        return capacity;
    }

    public static double theoreticalCapacityRequestsPerSecond() {
        return totalVmCapacityMips() / averageCloudletLengthMi();
    }

    public static int estimatedInspectedVmCount(String algorithmName) {
        return switch (algorithmName) {
            case "Weighted Round Robin",
                 "Adaptive Weighted Round Robin",
                 "Least Connections",
                 "Least Response Time" -> SimulationConfig.VM_COUNT;
            case "Power of Two Choices",
                 "P2C-LRT Hybrid" -> Math.min(
                    SimulationConfig.P2C_LRT_CHOICES,
                    SimulationConfig.VM_COUNT
            );
            default -> 0;
        };
    }

    public static double estimatedSelectionServiceTimeSeconds(String algorithmName) {
        int inspectedVms = estimatedInspectedVmCount(algorithmName);
        if (inspectedVms == 0) {
            return 0;
        }

        int comparisons = Math.max(0, inspectedVms - 1);
        double overheadMicroseconds =
                SELECTION_BASE_OVERHEAD_MICROSECONDS
                        + inspectedVms * VM_STATE_READ_OVERHEAD_MICROSECONDS
                        + comparisons * VM_COMPARISON_OVERHEAD_MICROSECONDS;
        return overheadMicroseconds / 1_000_000.0;
    }

    /**
     * Estimates total dispatcher latency using an M/D/1 queue:
     * service time + waiting time caused by serialized selection decisions.
     */
    public static double estimatedSelectionLatencySeconds(
            String algorithmName,
            double requestsPerSecond) {
        double serviceTime = estimatedSelectionServiceTimeSeconds(algorithmName);
        if (serviceTime == 0 || requestsPerSecond <= 0) {
            return serviceTime;
        }

        double utilization = requestsPerSecond * serviceTime;
        if (utilization >= 1.0) {
            return Double.POSITIVE_INFINITY;
        }

        double queueWait =
                requestsPerSecond * serviceTime * serviceTime
                        / (2.0 * (1.0 - utilization));
        return serviceTime + queueWait;
    }

    public record LoadLevel(String label, double fraction) {
        public LoadLevel {
            if (fraction <= 0) {
                throw new IllegalArgumentException("Load fraction must be positive");
            }
        }

        public double targetRequestsPerSecond() {
            return theoreticalCapacityRequestsPerSecond() * fraction;
        }

        public double meanInterarrivalTimeSeconds() {
            return 1.0 / targetRequestsPerSecond();
        }

        public double percentage() {
            return fraction * 100.0;
        }
    }
}
