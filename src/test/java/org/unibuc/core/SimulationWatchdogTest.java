package org.unibuc.core;

import org.cloudsimplus.vms.Vm;
import org.junit.jupiter.api.Test;
import org.unibuc.metrics.MetricsCollector;
import org.unibuc.util.Workload;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationWatchdogTest {

    @Test
    void abortsSimulationThatMakesNoCompletionProgress() {
        System.setProperty("simulation.progressHeartbeatSeconds", "1");
        System.setProperty("simulation.maxNoProgressSeconds", "1");
        try {
            Workload workload = new Workload(List.of(
                    new Workload.Request(0, 10_000, "10.0.0.1", 1.0)
            ));
            LoadBalancingPolicy stalledPolicy = new LoadBalancingPolicy() {
                @Override
                public Vm selectVm(
                        List<Vm> availableVms,
                        int requestIndex,
                        String sourceIp) {
                    try {
                        Thread.sleep(2_500);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return availableVms.getFirst();
                }

                @Override
                public String getName() {
                    return "Stalled Test Policy";
                }
            };

            long start = System.nanoTime();
            MetricsCollector metrics =
                    SimulationRunner.runSimulationForExperiment(stalledPolicy, workload);
            double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;

            assertEquals(0, metrics.getTotalRequests());
            assertTrue(elapsedSeconds < 5.0);
        } finally {
            System.clearProperty("simulation.progressHeartbeatSeconds");
            System.clearProperty("simulation.maxNoProgressSeconds");
        }
    }
}
