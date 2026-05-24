package org.unibuc.util;

import org.unibuc.core.SimulationConfig;

import java.util.*;

public class WorkloadGenerator {

    private final Random random;
    public WorkloadGenerator(long seed) {
        this.random = new Random(seed);
    }

    public Workload generateWorkload(int count, int uniqueIps) {
        List<String> sourceIps = generateSourceIps(count, uniqueIps);
        List<Long> cloudletLengths = generateCloudletLengths(count);
        List<Workload.Request> requests = new ArrayList<>(count);

        double arrivalTime = SimulationConfig.FIRST_REQUEST_TIME;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                arrivalTime += randomExponential(SimulationConfig.MEAN_INTERARRIVAL_TIME_SECONDS);
            }
            requests.add(new Workload.Request(
                    i,
                    cloudletLengths.get(i),
                    sourceIps.get(i),
                    arrivalTime
            ));
        }

        return new Workload(requests);
    }

    public List<Long> generateCloudletLengths(int count) {
        List<Long> lengths = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lengths.add(randomLong(
                    SimulationConfig.CLOUDLET_LENGTH_MIN_MI,
                    SimulationConfig.CLOUDLET_LENGTH_MAX_MI
            ));
        }
        return lengths;
    }

    public List<String> generateSourceIps(int count, int uniqueIps) {
        List<String> pool = new ArrayList<>(uniqueIps);
        for (int i = 0; i < uniqueIps; i++) {
            pool.add("10.0." + (i / 256) + "." + (i % 256));
        }
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int poolIndex = random.nextDouble() < 0.6
                    ? random.nextInt(Math.max(1, uniqueIps / 5))
                    : random.nextInt(uniqueIps);
            result.add(pool.get(poolIndex));
        }
        return result;
    }

    private long randomLong(long min, long max) {
        return min + (long) (random.nextDouble() * (max - min));
    }

    private double randomExponential(double mean) {
        if (mean <= 0) {
            return 0;
        }
        return -mean * Math.log(1.0 - random.nextDouble());
    }
}
