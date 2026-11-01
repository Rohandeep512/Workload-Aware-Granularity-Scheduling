# Workload Aware Granularity Scheduling for Serverless Cold Starts

A CloudSimSC based project that extends the granularity tree scheduler from Hanaforoosh, Azgomi and Ashtiani (Future Generation Computer Systems, vol. 164, 2025) with invocation frequency as a second scheduling factor.

## Idea

A cold start happens when a serverless function is called and no ready container exists. The base paper builds a granularity tree from function package dependencies, so an idle container that already has some of the needed packages can be reused (a tepid start) instead of starting from scratch.

That scheduler ignores how often each function is called. This project adds frequency awareness on top of the tree:

* Popular functions keep their idle containers longer (120 s for the hottest, 45 s for mid popularity, 30 s baseline for the rest).
* When a tepid container has to be picked, ties go to the container whose function is least popular, so hot functions keep their warm containers.
* Requests are served in three stages: warm (same function), tepid (same language group, fewest missing packages), cold (new container).

## Two layers

1. Standalone simulator (`custom` package): quick experiments with the tree, a capped container pool and Poisson traffic.
2. CloudSimSC integration: per function keep alive, tepid reuse inside the request load balancer, and a runner that executes every load and scheduler combination.

## Datasets

* Dependency data: the 24 function package sets of Dataset 1 from the base paper (Python, NodeJS, Go, Rust), listed in `TreeInfo.java`.
* Workloads: synthetic Poisson traces with skewed popularity at three load levels (Low 0.1 req/s, Medium 0.5 req/s, High 3 req/s). Request counts are 200, 1000 and 300 (High is capped because of simulator memory).
* The real trace files that ship with CloudSimSC are not used yet.

## Results (CloudSimSC, seed 1)

| Load | Scheduler | Cold % | Tepid % | Warm % | Avg latency (s) | Throughput (req/s) | Container seconds |
|---|---|---|---|---|---|---|---|
| Low | Baseline | 67.00 | 23.00 | 10.00 | 5.0548 | 0.1045 | 5914.9 |
| Low | Aware | 60.50 | 23.50 | 16.00 | 5.0220 | 0.1045 | 12185.7 |
| Medium | Baseline | 54.20 | 26.70 | 19.10 | 4.9296 | 0.5309 | 24851.6 |
| Medium | Aware | 53.60 | 27.00 | 19.40 | 4.9285 | 0.5309 | 49411.8 |
| High | Baseline | 53.67 | 28.33 | 18.00 | 5.0266 | 3.0106 | 6717.9 |
| High | Aware | 54.33 | 27.33 | 18.33 | 5.0281 | 3.0106 | 15249.2 |

What this shows:

* A clear gain at Low load (cold starts 67 to 60.5 percent, warm hits 10 to 16 percent).
* Little or no gain at Medium and High load. The High difference is about 2 requests out of 300, which is noise.
* Latency barely changes because function execution time dominates the 0.5 s cold start.
* Throughput is identical, since the system is arrival bound.
* Frequency aware keep alive costs resources: container seconds roughly doubles.

The standalone simulator (24 functions, 5 seeds) agrees on the Low load gain and is saved in `results.csv`.

## Repository layout

```
modules/cloudsim/src/main/java/org/cloudbus/cloudsim/serverless/
    CloudSimSCExample1.java      simulation setup (from CloudSimSC)
    Constants.java               configuration, some fields made non final
    KeepAlive.java               per function keep alive tiers
    RequestLoadBalancer.java     warm, tepid, cold container selection
    ServerlessContainer.java     adds keepAlive field
    ServerlessDatacenter.java    per container idle destruction
    CloudSimRunner.java          runs all loads and modes, writes results_cloudsim.csv
    custom/
        Function, GranularityTreeNode, GranularityTreeBuilder
        SchedulerBaseline, WorkloadAwareScheduler
        Simulator, Metrics, WorkloadGenerator, Experiment
        TraceWriter, TreeInfo, Demo
modules/cloudsim/plot.py         charts from results_cloudsim.csv
results.csv, results_cloudsim.csv, results_cloudsim.png
```

## How to run

Requirements: JDK 1.8, Maven, Eclipse (or any Java IDE), Python 3 with `pandas` and `matplotlib`.

1. Import the project into Eclipse as a Maven project.
2. Run `custom/Demo.java`. It builds the tree, generates `trace_low.csv`, `trace_medium.csv` and `trace_high.csv`, and prints the standalone simulator results (also saved to `results.csv`).
3. Run `CloudSimRunner.java`. It starts one JVM per load and scheduler combination (2 GB heap each) and appends to `results_cloudsim.csv`. Logs go to `log_<Load>_<Mode>.txt`. Expect 15 to 30 minutes.
4. Draw the charts:

```
cd modules/cloudsim
pip install pandas matplotlib
python plot.py
```

This saves `results_cloudsim.png`.

Close `results.csv` and `results_cloudsim.csv` before a run, since Windows locks files open in Excel.

## Limitations

* Start delays are simulator assumptions: cold start 0.5 s, tepid start min(0.4 s, 0.05 s + 0.1 s per missing package).
* CloudSimSC results come from a single seed, with no confidence intervals yet.
* Container seconds is an estimate, not measured CPU or memory.
* Keep alive thresholds were set by hand.
* No real trace validation, no energy model, no SLA metrics.

## Future work

Multiple seeds with confidence intervals, validation on real traces, more keep alive baselines, adaptive thresholds, predictive pre warming, a flexible tree structure and an energy model.

## Credits

* Base paper: M. Hanaforoosh, M. A. Azgomi, M. Ashtiani, "Reducing the Cost of Cold Start Time in Serverless Function Executions Using Granularity Trees," Future Generation Computer Systems, vol. 164, 2025.
* CloudSimSC toolkit by Anupama Mampage, The University of Melbourne. The original license and notices of CloudSimSC apply to the unmodified parts of this repository.
  RESEARCH PAPER : https://docs.google.com/document/d/1H4__gREXgxnavnuGKqp6yPV16POStmLx/edit?usp=sharing&ouid=100130950539533269080&rtpof=true&sd=true
