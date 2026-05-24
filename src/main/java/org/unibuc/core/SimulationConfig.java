package org.unibuc.core;

public final class SimulationConfig {

    private SimulationConfig() {}
    public static final int HOST_COUNT = 5;
    public static final int HOST_PES = 8;
    public static final long HOST_MIPS = 10_000;
    public static final long HOST_RAM_MB = 16_384;   // 16 GB
    public static final long HOST_STORAGE_MB = 100_000;
    public static final long HOST_BW_MBPS = 1_000;
    public static final int VM_COUNT = 10;
    public static final int VM_PES = 2;
    public static final long VM_MIPS = 4_000;
    public static final long[] VM_MIPS_PER_VM = {8000, 6000, 4000, 2000, 1000};
    public static final long VM_RAM_MB = 8_192;    // 8 GB
    public static final long VM_STORAGE_MB = 10_000;
    public static final long VM_BW_MBPS = 500;
    public static final int CLOUDLET_COUNT = 300;
    public static final int CLOUDLET_PES = 1;
    public static final long CLOUDLET_LENGTH_MIN_MI = 5_000;
    public static final long CLOUDLET_LENGTH_MAX_MI = 40_000;
    public static final long CLOUDLET_FILE_SIZE = 300;
    public static final long CLOUDLET_OUTPUT_SIZE = 300;
    public static final String OUTPUT_DIR = "results";
    public static final int RUNS_PER_ALGORITHM = 5;
    public static final int UNIQUE_SOURCE_IPS = 50;
    public static final double FIRST_REQUEST_TIME = 1.0;
    public static final double MEAN_INTERARRIVAL_TIME_SECONDS = 0.25;
    public static final long DEFAULT_POLICY_SEED = 25616L;
    public static final int[] WRR_WEIGHTS = {8, 6, 4, 2, 1};
    public static final int P2C_LRT_CHOICES = 2;
}
