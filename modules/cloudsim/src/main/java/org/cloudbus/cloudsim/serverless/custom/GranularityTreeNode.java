package org.cloudbus.cloudsim.serverless.custom;

import java.util.ArrayList;
import java.util.List;

public class GranularityTreeNode {
    private String groupLabel;
    private List<Function> functions;
    private List<GranularityTreeNode> children;

    public GranularityTreeNode(String groupLabel) {
        this.groupLabel = groupLabel;
        this.functions = new ArrayList<>();
        this.children = new ArrayList<>();
    }

    public void addFunction(Function f) {
        functions.add(f);
    }

    public void addChild(GranularityTreeNode child) {
        children.add(child);
    }

    public String getGroupLabel() {
        return groupLabel;
    }

    public List<Function> getFunctions() {
        return functions;
    }

    public List<GranularityTreeNode> getChildren() {
        return children;
    }

    public void printTree(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        String indent = sb.toString();

        System.out.println(indent + "- " + groupLabel + " -> " + functions);
        for (GranularityTreeNode child : children) {
            child.printTree(depth + 1);
        }
    }
}