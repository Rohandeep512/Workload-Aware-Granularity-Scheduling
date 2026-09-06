package org.cloudbus.cloudsim.serverless.custom;

public class WorkloadAwareScheduler {

    public void schedule(GranularityTreeNode root) {
        // will use Function.getInvocationCount() here next
        // idea: high calls = keep warm, low calls = release even if grouped
        System.out.println("=== Workload-Aware Scheduling (WIP) ===");
    }
}