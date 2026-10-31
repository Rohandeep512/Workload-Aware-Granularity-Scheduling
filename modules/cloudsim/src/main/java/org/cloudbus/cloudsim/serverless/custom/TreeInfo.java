package org.cloudbus.cloudsim.serverless.custom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreeInfo {
    public static int warm, tepid, cold;
    private static List<Function> fns;
    private static Map<String, String> groupOf = new HashMap<>();
    private static Map<String, List<String>> depsOf = new HashMap<>();
    private static Map<String, Integer> hits = new HashMap<>();
    private static int total;

    static {
        init();
    }

    public static List<Function> catalog() {
        List<Function> f = new ArrayList<>();
        f.add(new Function("F1", Arrays.asList("Python", "NumPy")));
        f.add(new Function("F2", Arrays.asList("Python", "Flask")));
        f.add(new Function("F3", Arrays.asList("Python", "Matplotlib")));
        f.add(new Function("F4", Arrays.asList("Python", "Flask", "Matplotlib")));
        f.add(new Function("F5", Arrays.asList("Python", "NumPy", "Matplotlib", "Flask")));
        f.add(new Function("F6", Arrays.asList("Python", "NumPy", "Matplotlib", "Flask")));
        f.add(new Function("F7", Arrays.asList("NodeJS", "Express")));
        f.add(new Function("F8", Arrays.asList("NodeJS", "Mongoose")));
        f.add(new Function("F9", Arrays.asList("NodeJS", "React")));
        f.add(new Function("F10", Arrays.asList("NodeJS", "Mongoose", "React")));
        f.add(new Function("F11", Arrays.asList("NodeJS", "Express", "React", "Mongoose")));
        f.add(new Function("F12", Arrays.asList("NodeJS", "Express", "React", "Mongoose")));
        f.add(new Function("F13", Arrays.asList("Go", "Gin")));
        f.add(new Function("F14", Arrays.asList("Go", "GORM")));
        f.add(new Function("F15", Arrays.asList("Go", "Viper")));
        f.add(new Function("F16", Arrays.asList("Go", "GORM", "Viper")));
        f.add(new Function("F17", Arrays.asList("Go", "Gin", "Viper", "GORM")));
        f.add(new Function("F18", Arrays.asList("Go", "Gin", "Viper", "GORM")));
        f.add(new Function("F19", Arrays.asList("Rust", "Diesel")));
        f.add(new Function("F20", Arrays.asList("Rust", "Tokio")));
        f.add(new Function("F21", Arrays.asList("Rust", "serde")));
        f.add(new Function("F22", Arrays.asList("Rust", "Tokio", "serde")));
        f.add(new Function("F23", Arrays.asList("Rust", "Diesel", "serde", "Tokio")));
        f.add(new Function("F24", Arrays.asList("Rust", "Diesel", "serde", "Tokio")));
        return f;
    }

    private static void init() {
        fns = catalog();
        GranularityTreeNode root = new GranularityTreeBuilder().buildTree(fns);
        for (GranularityTreeNode g : root.getChildren()) {
            mapGroup(g, g.getGroupLabel());
        }
        for (int i = 0; i < fns.size(); i++) {
            depsOf.put(String.valueOf(i + 1), fns.get(i).getDependencies());
        }
    }

    private static void mapGroup(GranularityTreeNode node, String label) {
        for (Function f : node.getFunctions()) {
            groupOf.put(String.valueOf(fns.indexOf(f) + 1), label);
        }
        for (GranularityTreeNode c : node.getChildren()) {
            mapGroup(c, label);
        }
    }

    public static boolean compatible(String a, String b) {
        String ga = groupOf.get(a);
        return ga != null && ga.equals(groupOf.get(b));
    }

    public static int missing(String want, String have) {
        List<String> w = depsOf.get(want);
        List<String> h = depsOf.get(have);
        if (w == null || h == null) return 99;
        int m = 0;
        for (String d : w) {
            if (!h.contains(d)) m++;
        }
        return m;
    }

    public static double tepidDelay(int missing) {
        return Math.min(0.4, 0.05 + 0.1 * missing);
    }

    public static void record(String fn) {
        hits.merge(fn, 1, Integer::sum);
        total++;
    }

    public static double freq(String fn) {
        if (total == 0) return 0;
        return (double) hits.getOrDefault(fn, 0) / total;
    }

    public static void reset() {
        warm = 0;
        tepid = 0;
        cold = 0;
        total = 0;
        hits.clear();
    }
}