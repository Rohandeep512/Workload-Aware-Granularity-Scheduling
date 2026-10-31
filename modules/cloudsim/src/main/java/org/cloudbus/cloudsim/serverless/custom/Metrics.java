package org.cloudbus.cloudsim.serverless.custom;

public class Metrics {
    public int cold, tepid, warm, requests;
    public double totalLatency, containerSeconds, firstArrival = -1, lastDone;

    public double coldRate() {
        return 100.0 * cold / requests;
    }

    public double avgLatency() {
        return totalLatency / requests;
    }

    public double throughput() {
        return requests / (lastDone - firstArrival);
    }
}