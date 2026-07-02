package org.unibuc.util;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.unibuc.core.SimulationConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Workload {

    private final List<Request> requests;

    public Workload(List<Request> requests) {
        this.requests = List.copyOf(requests);
    }

    public int size() {
        return requests.size();
    }

    public Request getRequest(int index) {
        return requests.get(index);
    }

    public List<Request> getRequests() {
        return Collections.unmodifiableList(requests);
    }

    public List<Cloudlet> createCloudlets() {
        List<Cloudlet> cloudlets = new ArrayList<>(requests.size());
        for (Request request : requests) {
            Cloudlet cloudlet = new CloudletSimple(
                    request.cloudletLength(),
                    SimulationConfig.CLOUDLET_PES
            );
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudlet.setUtilizationModelRam(UtilizationModel.NULL);
            cloudlet.setUtilizationModelBw(UtilizationModel.NULL);

            /*
             * cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.01));
             * cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.01));
             */
            cloudlet.setFileSize(SimulationConfig.CLOUDLET_FILE_SIZE);
            cloudlet.setOutputSize(SimulationConfig.CLOUDLET_OUTPUT_SIZE);
            cloudlet.setJobId(request.index());
            cloudlet.setSubmissionDelay(0);
            cloudlets.add(cloudlet);
        }
        return cloudlets;
    }

    public record Request(int index, long cloudletLength, String sourceIp, double arrivalTime) {}
}
