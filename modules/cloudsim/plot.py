import pandas as pd
import matplotlib.pyplot as plt

df = pd.read_csv("results_cloudsim.csv")
loads = ["Low", "Medium", "High"]
base = df[df.Scheduler == "Baseline"].set_index("Load").reindex(loads)
aware = df[df.Scheduler == "Aware"].set_index("Load").reindex(loads)

fig, axes = plt.subplots(2, 3, figsize=(15, 8))
x = list(range(len(loads)))

panels = [
    ("ColdPct", "Cold start rate (%)"),
    ("AvgLatency", "Average response time (s)"),
    ("Throughput", "Throughput (req/s)"),
    ("ContainerSec", "Container-seconds (resource use)"),
]

for ax, (col, title) in zip(axes.flat[:4], panels):
    ax.bar([i - 0.2 for i in x], base[col], 0.4, label="Baseline")
    ax.bar([i + 0.2 for i in x], aware[col], 0.4, label="Workload-aware")
    ax.set_xticks(x)
    ax.set_xticklabels(loads)
    ax.set_title(title)
    ax.legend()

ax = axes.flat[4]
colors = {"ColdPct": "#d9534f", "TepidPct": "#f0ad4e", "WarmPct": "#5cb85c"}
for off, d, name in [(-0.2, base, "B"), (0.2, aware, "A")]:
    bottom = [0, 0, 0]
    for col in ["ColdPct", "TepidPct", "WarmPct"]:
        ax.bar([i + off for i in x], d[col], 0.4, bottom=bottom, color=colors[col],
               label=col[:-3] if name == "B" else None)
        bottom = [b + v for b, v in zip(bottom, d[col])]
ax.set_xticks(x)
ax.set_xticklabels(loads)
ax.set_title("Start type mix (left bar Baseline, right bar Aware)")
ax.legend()

axes.flat[5].axis("off")

plt.tight_layout()
plt.savefig("results_cloudsim.png", dpi=200)
plt.show()