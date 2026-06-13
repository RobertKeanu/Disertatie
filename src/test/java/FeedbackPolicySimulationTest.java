import org.junit.jupiter.api.Test;
import org.unibuc.algorithms.LeastReponseTimePolicy;
import org.unibuc.algorithms.P2CLRTPolicy;
import org.unibuc.algorithms.WeightedRoundRobinPolicy;
import org.unibuc.core.LoadBalancingPolicy;
import org.unibuc.core.SimulationRunner;
import org.unibuc.metrics.MetricsCollector;
import org.unibuc.util.Workload;
import org.unibuc.util.WorkloadGenerator;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackPolicySimulationTest {

    @Test
    void feedbackPoliciesCompleteASmallCloudSimWorkload() throws Exception {
        int requestCount = 120;
        Workload workload = new WorkloadGenerator(1234L)
                .generateWorkload(requestCount, 20);

        Method runSimulation = SimulationRunner.class.getDeclaredMethod(
                "runSimulation", LoadBalancingPolicy.class, Workload.class);
        runSimulation.setAccessible(true);

        List<LoadBalancingPolicy> policies = List.of(
                new WeightedRoundRobinPolicy(),
                new LeastReponseTimePolicy(),
                new P2CLRTPolicy(2, 4321L)
        );

        for (LoadBalancingPolicy policy : policies) {
            policy.reset(4321L);
            MetricsCollector metrics =
                    (MetricsCollector) runSimulation.invoke(null, policy, workload);

            assertEquals(requestCount, metrics.getTotalRequests(), policy.getName());
            assertTrue(Double.isFinite(metrics.getAverageLatency()), policy.getName());
            assertTrue(metrics.getAverageLatency() > 0, policy.getName());
        }
    }
}
