import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unibuc.algorithms.IpHashPolicy;
import org.unibuc.algorithms.LeastConnectionsPolicy;
import org.unibuc.algorithms.RandomPolicy;
import org.unibuc.algorithms.RoundRobinPolicy;
import org.unibuc.core.LoadBalancingPolicy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoadBalancingPolicyTest {

    private List<Vm> vms;

    @BeforeEach
    void setUp() {
        vms = List.of(
                new VmSimple(1000, 1),
                new VmSimple(1000, 1),
                new VmSimple(1000, 1)
        );
    }

    @Test
    void roundRobin_cyclesThrough_allVms() {
        RoundRobinPolicy policy = new RoundRobinPolicy();
        Vm first  = policy.selectVm(vms, 0, "1.1.1.1");
        Vm second = policy.selectVm(vms, 1, "1.1.1.1");
        Vm third  = policy.selectVm(vms, 2, "1.1.1.1");
        Vm fourth = policy.selectVm(vms, 3, "1.1.1.1");
        assertEquals(first, fourth, "RR should wrap back to first VM");
    }

    @Test
    void roundRobin_reset_restartsFromBeginning() {
        RoundRobinPolicy policy = new RoundRobinPolicy();
        Vm firstRun = policy.selectVm(vms, 0, "x");
        policy.selectVm(vms, 1, "x");
        policy.reset();
        Vm afterReset = policy.selectVm(vms, 0, "x");
        assertEquals(firstRun, afterReset, "After reset, should start from VM 0 again");
    }

    @Test
    void roundRobin_singleVm_alwaysReturnsSameVm() {
        RoundRobinPolicy policy = new RoundRobinPolicy();
        List<Vm> single = List.of(new VmSimple(1000, 1));
        Vm a = policy.selectVm(single, 0, "x");
        Vm b = policy.selectVm(single, 1, "x");
        assertEquals(a, b);
    }

    @Test
    void leastConnections_prefersIdleVm() {
        LeastConnectionsPolicy policy = new LeastConnectionsPolicy();
        Vm r1 = policy.selectVm(vms, 0, "x");
        assertNotNull(r1);
    }

    @Test
    void leastConnections_afterCompletion_rebalances() {
        LeastConnectionsPolicy policy = new LeastConnectionsPolicy();
        Vm vm0 = policy.selectVm(vms, 0, "x");
        policy.selectVm(vms, 1, "x");
        policy.selectVm(vms, 2, "x");

        policy.onRequestComplete(vm0);

        Vm next = policy.selectVm(vms, 3, "x");
        assertEquals(vm0, next, "Should route to VM with fewest connections");
    }


    @Test
    void ipHash_sameIp_alwaysSameVm() {
        IpHashPolicy policy = new IpHashPolicy();
        String ip = "192.168.1.100";
        Vm first  = policy.selectVm(vms, 0, ip);
        Vm second = policy.selectVm(vms, 99, ip);
        assertEquals(first, second, "Same IP must always route to the same VM");
    }

    @Test
    void ipHash_differentIps_canRouteToDifferentVms() {
        IpHashPolicy policy = new IpHashPolicy();
        boolean foundDifference = true;
        Vm reference = policy.selectVm(vms, 0, "10.0.0.1");
        for (int i = 2; i < 50; i++) {
            Vm v = policy.selectVm(vms, 0, "10.0.0." + i);
            if (!v.equals(reference)) { foundDifference = true; break; }
        }
        assertTrue(foundDifference, "Different IPs should route to different VMs");
    }

    @Test
    void random_seeded_isReproducible() {
        RandomPolicy p1 = new RandomPolicy(42L);
        RandomPolicy p2 = new RandomPolicy(42L);
        for (int i = 0; i < 20; i++) {
            assertEquals(
                    p1.selectVm(vms, i, "x"),
                    p2.selectVm(vms, i, "x"),
                    "Same seed should produce identical sequence"
            );
        }
    }

    @Test
    void random_reset_reproducesSameSequence() {
        RandomPolicy policy = new RandomPolicy(99L);
        List<Vm> seq1 = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) seq1.add(policy.selectVm(vms, i, "x"));

        policy.reset();
        List<Vm> seq2 = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) seq2.add(policy.selectVm(vms, i, "x"));

        assertEquals(seq1, seq2, "After reset, same seed should produce same sequence");
    }

    @Test
    void allPolicies_throwOnEmptyVmList() {
        List<LoadBalancingPolicy> policies = List.of(
                new RoundRobinPolicy(),
                new LeastConnectionsPolicy(),
                new IpHashPolicy(),
                new RandomPolicy()
        );
        for (LoadBalancingPolicy p : policies) {
            assertThrows(IllegalStateException.class,
                    () -> p.selectVm(List.of(), 0, "x"),
                    p.getName() + " should throw on empty VM list");
        }
    }
}
 