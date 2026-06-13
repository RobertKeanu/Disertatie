package org.unibuc.core;

public final class SimulationConfig {

//    private SimulationConfig() {}
//    public static final int HOST_COUNT = 5;
//    public static final int HOST_PES = 8;
//    public static final long HOST_MIPS = 10_000;
//    public static final long HOST_RAM_MB = 16_384;   // 16 GB
//    public static final long HOST_STORAGE_MB = 100_000;
//    public static final long HOST_BW_MBPS = 1_000;
//    public static final int VM_COUNT = 10;
//    public static final int VM_PES = 2;
//    public static final long VM_MIPS = 4_000;
//    public static final long[] VM_MIPS_PER_VM = {8000, 6000, 4000, 2000, 1000};
//    public static final long VM_RAM_MB = 8_192;    // 8 GB
//    public static final long VM_STORAGE_MB = 10_000;
//    public static final long VM_BW_MBPS = 500;
//    public static final int CLOUDLET_COUNT = 300;
//    public static final int CLOUDLET_PES = 1;
//    public static final long CLOUDLET_LENGTH_MIN_MI = 5_000;
//    public static final long CLOUDLET_LENGTH_MAX_MI = 40_000;
//    public static final long CLOUDLET_FILE_SIZE = 300;
//    public static final long CLOUDLET_OUTPUT_SIZE = 300;
//    public static final String OUTPUT_DIR = "results";
//    public static final int RUNS_PER_ALGORITHM = 5;
//    public static final int UNIQUE_SOURCE_IPS = 50;
//    public static final double FIRST_REQUEST_TIME = 1.0;
//    public static final double MEAN_INTERARRIVAL_TIME_SECONDS = 0.25;
//    public static final long DEFAULT_POLICY_SEED = 25616L;
//    public static final int[] WRR_WEIGHTS = {8, 6, 4, 2, 1};
//    public static final int P2C_LRT_CHOICES = 2;

    /*
     * true: reproduce the capacity-aware/oracle experiments. WRR uses the
     * configured weights, while LRT and P2C-LRT receive cloudlet MI and VM MIPS.
     * false: policies use only observed latency and active requests.
     */
    public static final boolean USE_DIRECT_POLICY_INFORMATION = false;
//
        private SimulationConfig() {}

        // Physical infrastructure
        // 32 hosts provide enough RAM and PEs for all 250 VMs.
        public static final int HOST_COUNT = 32;
        public static final int HOST_PES = 32;
        public static final long HOST_MIPS = 20_000;
        public static final long HOST_RAM_MB = 65_536;       // 64 GB simulated
        public static final long HOST_STORAGE_MB = 500_000;
        public static final long HOST_BW_MBPS = 10_000;

        // Virtual machines
        public static final int VM_COUNT = 250;
        public static final int VM_PES = 2;

        // Kept for compatibility; VM_MIPS_PER_VM is used by SimulationRunner
        public static final long VM_MIPS = 6_000;

        public static final long[] VM_MIPS_PER_VM = {
                12_000, // high performance
                9_000,
                6_000,
                3_000,
                1_500  // low performance
        };

        public static final long VM_RAM_MB = 8_192;          // 8 GB simulated
        public static final long VM_STORAGE_MB = 40_000;
        public static final long VM_BW_MBPS = 1_000;

        // Workload
        public static final int CLOUDLET_COUNT = 4_000;
        public static final int CLOUDLET_PES = 1;
        public static final long CLOUDLET_LENGTH_MIN_MI = 5_000;
        public static final long CLOUDLET_LENGTH_MAX_MI = 150_000;
        public static final long CLOUDLET_FILE_SIZE = 1_000;
        public static final long CLOUDLET_OUTPUT_SIZE = 1_000;

        // Experiments
        public static final String OUTPUT_DIR = "results";
        public static final int RUNS_PER_ALGORITHM = 10;
        public static final int UNIQUE_SOURCE_IPS = 500;
        public static final int PROGRESS_INTERVAL = 1_000;
        public static final long PROGRESS_HEARTBEAT_SECONDS = 30;
        public static final long MAX_NO_PROGRESS_SECONDS = 90;

        // Poisson arrival process: approximately 5 requests/second
        public static final double FIRST_REQUEST_TIME = 1.0;
        public static final double MEAN_INTERARRIVAL_TIME_SECONDS = 0.20;

        // Reproducibility
        public static final long DEFAULT_POLICY_SEED = 25616L;

        // Previous capacity-aware WRR weights, retained for reference.
        // The adaptive WRR now learns weights from observed latency and active requests.
        public static final int[] WRR_WEIGHTS = {8, 6, 4, 2, 1};

        public static final int P2C_LRT_CHOICES = 2;

}
