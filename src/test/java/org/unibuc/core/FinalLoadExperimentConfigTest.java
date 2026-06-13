package org.unibuc.core;

import org.junit.jupiter.api.Test;
import org.unibuc.util.Workload;
import org.unibuc.util.WorkloadGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalLoadExperimentConfigTest {

    @Test
    void computesCapacityFromAllConfiguredVmsAndPes() {
        double expectedCapacityMips = 0;
        for (int vmIndex = 0; vmIndex < SimulationConfig.VM_COUNT; vmIndex++) {
            expectedCapacityMips += SimulationConfig.VM_MIPS_PER_VM[
                    vmIndex % SimulationConfig.VM_MIPS_PER_VM.length
            ] * SimulationConfig.VM_PES;
        }

        assertEquals(
                expectedCapacityMips,
                FinalLoadExperimentConfig.totalVmCapacityMips(),
                0.0001
        );
        assertEquals(
                expectedCapacityMips
                        / FinalLoadExperimentConfig.averageCloudletLengthMi(),
                FinalLoadExperimentConfig.theoreticalCapacityRequestsPerSecond(),
                0.0001
        );
    }

    @Test
    void higherLoadProducesShorterMeanInterarrivalTime() {
        double previousInterval = Double.POSITIVE_INFINITY;

        for (FinalLoadExperimentConfig.LoadLevel level
                : FinalLoadExperimentConfig.LOAD_LEVELS) {
            assertTrue(level.meanInterarrivalTimeSeconds() < previousInterval);
            assertEquals(
                    1.0 / level.targetRequestsPerSecond(),
                    level.meanInterarrivalTimeSeconds(),
                    1.0e-12
            );
            previousInterval = level.meanInterarrivalTimeSeconds();
        }
    }

    @Test
    void sameSeedKeepsRequestsIdenticalAndOnlyScalesArrivalGaps() {
        double slowMean = 0.40;
        double fastMean = 0.10;
        Workload slow = new WorkloadGenerator(1234L)
                .generateWorkload(100, 20, slowMean);
        Workload fast = new WorkloadGenerator(1234L)
                .generateWorkload(100, 20, fastMean);

        for (int i = 0; i < slow.size(); i++) {
            Workload.Request slowRequest = slow.getRequest(i);
            Workload.Request fastRequest = fast.getRequest(i);
            assertEquals(slowRequest.cloudletLength(), fastRequest.cloudletLength());
            assertEquals(slowRequest.sourceIp(), fastRequest.sourceIp());

            if (i > 0) {
                double slowGap = slowRequest.arrivalTime()
                        - slow.getRequest(i - 1).arrivalTime();
                double fastGap = fastRequest.arrivalTime()
                        - fast.getRequest(i - 1).arrivalTime();
                assertEquals(slowMean / fastMean, slowGap / fastGap, 1.0e-9);
            }
        }
    }

    @Test
    void estimatesSelectionOverheadFromInspectedVmCount() {
        assertEquals(
                SimulationConfig.VM_COUNT,
                FinalLoadExperimentConfig.estimatedInspectedVmCount(
                        "Least Response Time"
                )
        );
        assertEquals(
                Math.min(
                        SimulationConfig.P2C_LRT_CHOICES,
                        SimulationConfig.VM_COUNT
                ),
                FinalLoadExperimentConfig.estimatedInspectedVmCount(
                        "P2C-LRT Hybrid"
                )
        );
        assertEquals(
                0,
                FinalLoadExperimentConfig.estimatedInspectedVmCount(
                        "Round Robin"
                )
        );

        double fullScanExpectedMicroseconds =
                FinalLoadExperimentConfig.SELECTION_BASE_OVERHEAD_MICROSECONDS
                        + SimulationConfig.VM_COUNT
                        * FinalLoadExperimentConfig.VM_STATE_READ_OVERHEAD_MICROSECONDS
                        + Math.max(0, SimulationConfig.VM_COUNT - 1)
                        * FinalLoadExperimentConfig.VM_COMPARISON_OVERHEAD_MICROSECONDS;
        assertEquals(
                fullScanExpectedMicroseconds / 1_000_000.0,
                FinalLoadExperimentConfig.estimatedSelectionServiceTimeSeconds(
                        "Least Response Time"
                ),
                1.0e-12
        );
        assertTrue(
                FinalLoadExperimentConfig.estimatedSelectionLatencySeconds(
                        "Least Response Time",
                        0.1
                ) > FinalLoadExperimentConfig.estimatedSelectionServiceTimeSeconds(
                        "Least Response Time"
                )
        );
        assertTrue(
                FinalLoadExperimentConfig.estimatedSelectionLatencySeconds(
                        "Least Response Time",
                        0.1
                ) > FinalLoadExperimentConfig.estimatedSelectionLatencySeconds(
                        "P2C-LRT Hybrid",
                        0.1
                )
        );
    }
}
