package org.cloudbus.cloudsim.serverless.custom;

import java.util.*;

public class GranularityTreeBuilder {

    public GranularityTreeNode buildTree(List<Function> functions) {
        GranularityTreeNode root = new GranularityTreeNode("ROOT");
        Map<String, GranularityTreeNode> groups = new LinkedHashMap<>();

        for (Function f : functions) {
            String key = f.getDependencies().isEmpty() ? "no-deps" : f.getDependencies().get(0);
            groups.putIfAbsent(key, new GranularityTreeNode("group:" + key));
            groups.get(key).addFunction(f);
        }

        for (GranularityTreeNode groupNode : groups.values()) {
            if (groupNode.getFunctions().size() > 1) {
                Map<String, GranularityTreeNode> subs = new LinkedHashMap<>();
                for (Function f : groupNode.getFunctions()) {
                    String sub = String.join("+", f.getDependencies());
                    subs.putIfAbsent(sub, new GranularityTreeNode("deps:" + sub));
                    subs.get(sub).addFunction(f);
                }
                if (subs.size() > 1) {
                    for (GranularityTreeNode s : subs.values()) {
                        groupNode.addChild(s);
                    }
                }
            }
            root.addChild(groupNode);
        }

        return root;
    }
}