package org.cloudbus.cloudsim.serverless.custom;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Experiment {

    private static void collect(GranularityTreeNode node, List<Function> out) {
        for (Function f : node.getFunctions()) {
            if (!out.contains(f)) out.add(f);
        }
        for (GranularityTreeNode c : node.getChildren()) {
            collect(c, out);
        }
    }

    private static void add(double[] a, Metrics m) {
        a[0] += m.coldRate();
        a[1] += m.avgLatency();
        a[2] += m.throughput();
        a[3] += m.containerSeconds;
    }

    public static void run(GranularityTreeNode root) {
        List<Function> fns = new ArrayList<>();
        collect(root, fns);

        SchedulerBaseline base = new SchedulerBaseline();
        WorkloadAwareScheduler wa = new WorkloadAwareScheduler();
        Simulator sim = new Simulator();
        WorkloadGenerator gen = new WorkloadGenerator();

        String[] names = {"Low", "Medium", "High"};
        double[] rates = {0.1, 0.5, 3};
        int runs = 5;

        System.out.println("\n=== Experiment Results (avg of " + runs + " runs) ===");
        System.out.printf("%-8s %-10s %10s %12s %12s %14s%n",
                "Load", "Scheduler", "Cold%", "AvgLat(s)", "Throughput", "ContainerSec");

        StringBuilder csv = new StringBuilder("Load,Scheduler,ColdPct,AvgLatency,Throughput,ContainerSec\n");

        for (int i = 0; i < names.length; i++) {
            double[] b = new double[4];
            double[] w = new double[4];
            for (int seed = 1; seed <= runs; seed++) {
                List<WorkloadGenerator.Request> reqs = gen.generate(fns, rates[i], 2000, seed);
                for (Function f : fns) f.reset();
                add(b, sim.run(root, reqs, base::keepAlive, false));
                for (Function f : fns) f.reset();
                add(w, sim.run(root, reqs, wa::keepAlive, true));
            }
            for (int k = 0; k < 4; k++) {
                b[k] /= runs;
                w[k] /= runs;
            }
            System.out.printf("%-8s %-10s %10.2f %12.4f %12.4f %14.1f%n", names[i], "Baseline", b[0], b[1], b[2], b[3]);
            System.out.printf("%-8s %-10s %10.2f %12.4f %12.4f %14.1f%n", names[i], "Aware", w[0], w[1], w[2], w[3]);
            csv.append(String.format("%s,Baseline,%.2f,%.4f,%.4f,%.1f%n", names[i], b[0], b[1], b[2], b[3]));
            csv.append(String.format("%s,Aware,%.2f,%.4f,%.4f,%.1f%n", names[i], w[0], w[1], w[2], w[3]));
        }

        try (FileWriter fw = new FileWriter("results.csv")) {
            fw.write(csv.toString());
            System.out.println("\nSaved results.csv");
        } catch (IOException e) {
            System.out.println("Could not write results.csv");
        }
    }
}