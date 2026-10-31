package org.cloudbus.cloudsim.serverless.custom;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorkloadGenerator {

    public static class Request {
        public double time;
        public Function fn;

        public Request(double time, Function fn) {
            this.time = time;
            this.fn = fn;
        }
    }

    public List<Request> generate(List<Function> fns, double rate, int n, long seed) {
        Random r = new Random(seed);
        double[] w = new double[fns.size()];
        double sum = 0;
        for (int i = 0; i < w.length; i++) {
            w[i] = 1.0 / (i + 1);
            sum += w[i];
        }
        List<Request> out = new ArrayList<>();
        double t = 0;
        for (int k = 0; k < n; k++) {
            t += -Math.log(1 - r.nextDouble()) / rate;
            double x = r.nextDouble() * sum;
            int idx = 0;
            while (idx < w.length - 1 && x > w[idx]) {
                x -= w[idx];
                idx++;
            }
            out.add(new Request(t, fns.get(idx)));
        }
        return out;
    }
}