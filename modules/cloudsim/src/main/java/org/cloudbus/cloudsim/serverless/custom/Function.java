package org.cloudbus.cloudsim.serverless.custom;

import java.util.List;

public class Function {
    private String name;
    private List<String> dependencies;
    private int invocationCount;

    public Function(String name, List<String> dependencies) {
        this.name = name;
        this.dependencies = dependencies;
        this.invocationCount = 0;
    }

    public String getName() {
        return name;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public int getInvocationCount() {
        return invocationCount;
    }

    public void recordInvocation() {
        invocationCount++;
    }

    @Override
    public String toString() {
        return name + " deps=" + dependencies + " calls=" + invocationCount;
    }
}