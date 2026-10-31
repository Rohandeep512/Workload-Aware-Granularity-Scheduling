package org.cloudbus.cloudsim.serverless.custom;

public class WorkloadAwareScheduler {

    public void schedule(GranularityTreeNode root) {
        System.out.println("=== Workload-Aware Scheduling ===");
        for (GranularityTreeNode group : root.getChildren()) {
            int calls = 0;
            for (Function f : group.getFunctions()) {
                calls += f.getInvocationCount();
            }
            if (group.getFunctions().size() > 1 && calls > 0) {
                System.out.println(group.getGroupLabel() + " -> deploy together, keep warm (calls=" + calls + ")");
            } else if (calls > 0) {
                System.out.println(group.getGroupLabel() + " -> deploy separately (calls=" + calls + ")");
            } else {
                System.out.println(group.getGroupLabel() + " -> release (no calls)");
            }
        }
    }

    public double keepAlive(Function f, int total) {
        if (total < 20) return 30;
        double share = (double) f.getInvocationCount() / total;
        if (share >= 0.08) return 120;
        if (share >= 0.03) return 45;
        return 30;
    }
}