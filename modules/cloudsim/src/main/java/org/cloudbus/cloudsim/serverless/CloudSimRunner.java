package org.cloudbus.cloudsim.serverless;

import org.cloudbus.cloudsim.serverless.custom.TreeInfo;

import java.io.*;
import java.util.*;

public class CloudSimRunner {

    static final String[] LOADS = {"High"};

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            driver();
        } else {
            single(args[0], args[1], args[2]);
        }
    }

    static void driver() throws Exception {
        String[] modes = {"Baseline", "Aware"};
        File csv = new File("results_cloudsim.csv");
        if (!csv.exists()) {
            try (FileWriter fw = new FileWriter(csv)) {
                fw.write("Load,Scheduler,ColdPct,TepidPct,WarmPct,AvgLatency,Throughput,ContainerSec\n");
            }
        }
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String cp = System.getProperty("java.class.path");
        for (String l : LOADS) {
            for (String m : modes) {
                ProcessBuilder pb = new ProcessBuilder(java, "-Xmx2g", "-cp", cp,
                        "org.cloudbus.cloudsim.serverless.CloudSimRunner",
                        "trace_" + l.toLowerCase() + ".csv", m, l);
                pb.redirectErrorStream(true);
                pb.redirectOutput(new File("log_" + l + "_" + m + ".txt"));
                int code = pb.start().waitFor();
                System.out.println(l + " " + m + " finished, exit code " + code);
            }
        }
        System.out.println("Done, see results_cloudsim.csv");
    }

    static void single(String trace, String mode, String load) throws Exception {
        new File("Result.csv").delete();
        Constants.FUNCTION_REQUESTS_FILENAME = trace;
        KeepAlive.aware = mode.equals("Aware");
        KeepAlive.reset();
        TreeInfo.reset();
        CloudSimSCExample1.main(new String[0]);
        parse(load, mode);
    }

    static void parse(String load, String mode) throws Exception {
        Map<String, double[]> cont = new HashMap<>();
        Map<String, String> fnOf = new HashMap<>();
        int n = 0;
        double lat = 0, minArr = Double.MAX_VALUE, maxFin = 0;

        try (BufferedReader br = new BufferedReader(new FileReader("Result.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.replace("\"", "").split(",");
                if (p.length < 10 || !p[2].equals("SUCCESS")) continue;
                double st = Double.parseDouble(p[7]);
                double fin = Double.parseDouble(p[8]);
                double resp = Double.parseDouble(p[9]);
                n++;
                lat += resp;
                minArr = Math.min(minArr, fin - resp);
                maxFin = Math.max(maxFin, fin);
                double[] t = cont.get(p[3]);
                if (t == null) {
                    cont.put(p[3], new double[]{st - Constants.CONTAINER_STARTTUP_DELAY, fin});
                } else {
                    t[0] = Math.min(t[0], st);
                    t[1] = Math.max(t[1], fin);
                }
                fnOf.put(p[3], p[1]);
            }
        }

        double cs = 0;
        for (Map.Entry<String, double[]> e : cont.entrySet()) {
            double[] t = e.getValue();
            cs += (t[1] - t[0]) + KeepAlive.idleTime(fnOf.get(e.getKey()));
        }

        double all = TreeInfo.cold + TreeInfo.tepid + TreeInfo.warm;
        double coldPct = 100.0 * TreeInfo.cold / all;
        double tepidPct = 100.0 * TreeInfo.tepid / all;
        double warmPct = 100.0 * TreeInfo.warm / all;
        double avgLat = lat / n;
        double thr = n / (maxFin - minArr);

        try (FileWriter fw = new FileWriter("results_cloudsim.csv", true)) {
            fw.write(String.format(Locale.US, "%s,%s,%.2f,%.2f,%.2f,%.4f,%.4f,%.1f%n",
                    load, mode, coldPct, tepidPct, warmPct, avgLat, thr, cs));
        }
    }
}