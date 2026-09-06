package org.cloudbus.cloudsim.serverless.custom;

import java.util.Arrays;
import java.util.List;

public class Demo {

    public static void main(String[] args) {
        Function f1 = new Function("FunctionA", Arrays.asList("numpy", "requests"));
        Function f2 = new Function("FunctionB", Arrays.asList("numpy"));
        Function f3 = new Function("FunctionC", Arrays.asList("pandas"));

        f1.recordInvocation();
        f1.recordInvocation();
        f1.recordInvocation();
        f3.recordInvocation();

        List<Function> functions = Arrays.asList(f1, f2, f3);

        GranularityTreeBuilder builder = new GranularityTreeBuilder();
        GranularityTreeNode tree = builder.buildTree(functions);

        System.out.println("=== Granularity Tree ===");
        tree.printTree(0);

        new SchedulerBaseline().schedule(tree);
        new WorkloadAwareScheduler().schedule(tree);
    }
}