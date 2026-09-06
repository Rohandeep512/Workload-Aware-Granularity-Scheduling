package org.cloudbus.cloudsim.serverless.custom;

import java.util.*;

public class GranularityTreeBuilder {

    public GranularityTreeNode buildTree(List<Function> functions) {
        GranularityTreeNode root = new GranularityTreeNode("ROOT");
        Map<String, GranularityTreeNode> groups = new LinkedHashMap<>();

        // grouping by first dependency for now, will refine later
        for (Function f : functions) {
            String key = f.getDependencies().isEmpty() ? "no-deps" : f.getDependencies().get(0);
            groups.putIfAbsent(key, new GranularityTreeNode("group:" + key));
            groups.get(key).addFunction(f);
        }

        for (GranularityTreeNode groupNode : groups.values()) {
            root.addChild(groupNode);
        }

        return root;
    }
}