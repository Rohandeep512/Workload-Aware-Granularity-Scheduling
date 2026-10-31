package org.cloudbus.cloudsim.serverless.custom;

import java.util.List;

public class Demo {

    public static void main(String[] args) {
        List<Function> functions = TreeInfo.catalog();

        TraceWriter.write(functions, 0.1, 1, "trace_low.csv");
        TraceWriter.write(functions, 0.5, 1, "trace_medium.csv");
        TraceWriter.write(functions, 3, 1, "trace_high.csv");

        GranularityTreeBuilder builder = new GranularityTreeBuilder();
        GranularityTreeNode tree = builder.buildTree(functions);

        System.out.println("=== Granularity Tree ===");
        tree.printTree(0);

        new SchedulerBaseline().schedule(tree);
        new WorkloadAwareScheduler().schedule(tree);

        Experiment.run(tree);
    }
}