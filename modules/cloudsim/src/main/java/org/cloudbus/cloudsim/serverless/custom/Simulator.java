package org.cloudbus.cloudsim.serverless.custom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class Simulator {
    static final double COLD = 0.8, TEPID = 0.3, WARM = 0.05, EXEC = 0.1;
    static final int MAX = 6;

    static class Container {
        String group;
        Function fn;
        double busyUntil, lastUsed, created, ka;
    }

    private void mapGroup(GranularityTreeNode node, String label, Map<Function, String> map) {
        for (Function f : node.getFunctions()) {
            map.put(f, label);
        }
        for (GranularityTreeNode c : node.getChildren()) {
            mapGroup(c, label, map);
        }
    }

    public Metrics run(GranularityTreeNode root, List<WorkloadGenerator.Request> reqs,
                       BiFunction<Function, Integer, Double> policy, boolean freqEvict) {
        Map<Function, String> groupOf = new HashMap<>();
        for (GranularityTreeNode g : root.getChildren()) {
            mapGroup(g, g.getGroupLabel(), groupOf);
        }

        List<Container> pool = new ArrayList<>();
        Metrics m = new Metrics();
        int total = 0;

        for (WorkloadGenerator.Request req : reqs) {
            double t = req.time;
            Function f = req.fn;
            String group = groupOf.getOrDefault(f, "root");

            Iterator<Container> it = pool.iterator();
            while (it.hasNext()) {
                Container c = it.next();
                if (t - c.lastUsed > c.ka) {
                    m.containerSeconds += c.lastUsed + c.ka - c.created;
                    it.remove();
                }
            }

            f.recordInvocation();
            total++;

            Container chosen = null;
            double startCost = COLD;
            double wait = 0;

            for (Container c : pool) {
                if (c.busyUntil <= t && c.fn == f) {
                    chosen = c;
                    startCost = WARM;
                    break;
                }
            }
            if (chosen == null) {
                for (Container c : pool) {
                    if (c.busyUntil <= t && c.group.equals(group)) {
                        chosen = c;
                        startCost = TEPID;
                        break;
                    }
                }
            }
            if (chosen == null) {
                if (pool.size() >= MAX) {
                    Container victim = null;
                    for (Container c : pool) {
                        if (c.busyUntil <= t) {
                            if (victim == null) victim = c;
                            else if (freqEvict && c.fn.getInvocationCount() < victim.fn.getInvocationCount()) victim = c;
                            else if (!freqEvict && c.lastUsed < victim.lastUsed) victim = c;
                        }
                    }
                    if (victim == null) {
                        for (Container c : pool) {
                            if (victim == null || c.busyUntil < victim.busyUntil) victim = c;
                        }
                        wait = victim.busyUntil - t;
                    }
                    m.containerSeconds += t + wait - victim.created;
                    pool.remove(victim);
                }
                chosen = new Container();
                chosen.group = group;
                chosen.created = t + wait;
                pool.add(chosen);
            }

            if (startCost == WARM) m.warm++;
            else if (startCost == TEPID) m.tepid++;
            else m.cold++;

            double latency = wait + startCost + EXEC;
            chosen.fn = f;
            chosen.busyUntil = t + latency;
            chosen.lastUsed = chosen.busyUntil;
            chosen.ka = policy.apply(f, total);

            m.requests++;
            m.totalLatency += latency;
            if (m.firstArrival < 0) m.firstArrival = t;
            m.lastDone = Math.max(m.lastDone, chosen.busyUntil);
        }

        for (Container c : pool) {
            m.containerSeconds += c.lastUsed + c.ka - c.created;
        }
        return m;
    }
}