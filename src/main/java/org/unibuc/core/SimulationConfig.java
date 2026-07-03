package org.unibuc.core;

public final class SimulationConfig {

    private SimulationConfig() {
    }

    public static final boolean USE_DIRECT_POLICY_INFORMATION = false;

    public static final int HOST_COUNT = 16;
    public static final int HOST_PES = 32;
    public static final long HOST_MIPS = 20_000;
    public static final long HOST_RAM_MB = 65_536;
    public static final long HOST_STORAGE_MB = 500_000;
    public static final long HOST_BW_MBPS = 10_000;

    public static final int VM_COUNT = 120;
    public static final int VM_PES = 2;
    public static final long VM_MIPS = 6_000;
    public static final long[] VM_MIPS_PER_VM = {
            12_000,
            9_000,
            6_000,
            3_000,
            1_500
    };
    public static final long VM_RAM_MB = 8_192;
    public static final long VM_STORAGE_MB = 40_000;
    public static final long VM_BW_MBPS = 1_000;

    public static final int CLOUDLET_COUNT = 1_000;
    public static final int CLOUDLET_PES = 1;
    public static final long CLOUDLET_LENGTH_MIN_MI = 9_000;
    public static final long CLOUDLET_LENGTH_MAX_MI = 150_000;
    public static final long CLOUDLET_FILE_SIZE = 1_000;
    public static final long CLOUDLET_OUTPUT_SIZE = 1_000;
    public static final int UNIQUE_SOURCE_IPS = 500;
    public static final double FIRST_REQUEST_TIME = 1.0;
    public static final double MEAN_INTERARRIVAL_TIME_SECONDS = 0.20;

    public static final String OUTPUT_DIR = "results";
    public static final int RUNS_PER_ALGORITHM = 1;
    public static final long DEFAULT_POLICY_SEED = 25_616L;
    public static final int[] WRR_WEIGHTS = {8, 6, 4, 2, 1};
    public static final int P2C_LRT_CHOICES = 2;

    public static final int PROGRESS_INTERVAL = 1_000;
    public static final long PROGRESS_HEARTBEAT_SECONDS = 30;
    public static final long MAX_NO_PROGRESS_SECONDS = 90;
}
