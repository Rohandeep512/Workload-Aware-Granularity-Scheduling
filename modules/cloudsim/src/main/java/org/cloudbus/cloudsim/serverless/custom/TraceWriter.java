package org.cloudbus.cloudsim.serverless.custom;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class TraceWriter {

    public static void write(List<Function> fns, double rate, long seed, String path) {
        long[] len = {653, 2275, 3842, 7446};
        int[] mem = {64, 128, 192, 256};
        long[] c5 = {242, 484, 725, 967};
        int n = (int) Math.min(rate * 2000, rate >= 3 ? 300 : 1000);
        List<WorkloadGenerator.Request> reqs = new WorkloadGenerator().generate(fns, rate, n, seed);
        try (FileWriter fw = new FileWriter(path)) {
            for (WorkloadGenerator.Request r : reqs) {
                int id = fns.indexOf(r.fn);
                int k = id % 4;
                fw.write(String.format(Locale.US, "%.2f,%d,%d,1,%d,%d,1,1%n", r.time, id + 1, len[k], mem[k], c5[k]));
            }
            System.out.println("Wrote " + path + " (" + n + " requests)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}