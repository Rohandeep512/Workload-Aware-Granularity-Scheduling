package org.cloudbus.cloudsim.serverless.custom;

public class SchedulerBaseline {

    public void schedule(GranularityTreeNode root) {
        System.out.println("=== Baseline Scheduling ===");
        for (GranularityTreeNode group : root.getChildren()) {
            if (group.getFunctions().size() > 1) {
                System.out.println(group.getGroupLabel() + " -> deploy together");
            } else {
                System.out.println(group.getGroupLabel() + " -> deploy separately");
            }
        }
    }
}