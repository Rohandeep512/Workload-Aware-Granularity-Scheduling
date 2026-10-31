package org.cloudbus.cloudsim.serverless;

import java.util.HashMap;
import java.util.Map;

public class KeepAlive {
    public static boolean aware = false;
    private static Map<String, Integer> counts = new HashMap<>();
    private static int total = 0;

    public static void reset() {
        counts.clear();
        total = 0;
    }

    public static void record(String fn) {
        counts.merge(fn, 1, Integer::sum);
        total++;
    }

    public static double idleTime(String fn) {
        if (!aware || total < 20) return 30;
        double share = (double) counts.getOrDefault(fn, 0) / total;
        if (share >= 0.08) return 120;
        if (share >= 0.03) return 45;
        return 30;
    }
}