#!/usr/bin/env python3

import argparse
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple

import pandas as pd

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


@dataclass(frozen=True)
class ReportRef:
    build_type: str
    run_id: str
    path: Path


def _clean_event_name(name: str) -> str:
    s = name.strip().strip("`")
    if s in ("-", "", "N/A"):
        return ""
    s = re.sub(r"\s+", " ", s)
    s = re.sub(r"\s*\(.*?\)\s*$", "", s).strip()
    return s


def _parse_seconds(token: str) -> Optional[float]:
    t = token.strip().strip("`")
    if t in ("-", "", "N/A"):
        return None
    t = t.replace("s", "").strip()
    try:
        return float(t)
    except ValueError:
        return None


def _iter_markdown_table_rows(lines: List[str]) -> Iterable[List[str]]:
    for line in lines:
        stripped = line.strip("\n")
        if not stripped.strip().startswith("|"):
            continue
        if "---" in stripped:
            continue
        parts = [p.strip() for p in stripped.split("|")]
        parts = [p for p in parts if p != ""]
        if not parts:
            continue
        yield parts


def parse_merged_boot_report(path: Path) -> List[Tuple[str, float, int]]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    lines = text.splitlines()

    section_idx = None
    for i, line in enumerate(lines):
        if line.strip() == "## Merged Timeline Summary":
            section_idx = i
            break

    if section_idx is None:
        return []

    table_start = None
    for i in range(section_idx + 1, len(lines)):
        if lines[i].lstrip().startswith("|") and "TimeStamp" in lines[i] and "Delta(A)" in lines[i]:
            table_start = i
            break

    if table_start is None:
        return []

    table_lines: List[str] = []
    for i in range(table_start, len(lines)):
        line = lines[i]
        if i > table_start and not line.strip():
            break
        if line.lstrip().startswith("|"):
            table_lines.append(line)

    if not table_lines:
        return []

    header = [h.strip() for h in table_lines[0].split("|")]
    header = [h for h in header if h]
    cols: Dict[str, int] = {name: idx for idx, name in enumerate(header)}

    def idx(col: str) -> Optional[int]:
        return cols.get(col)

    i_log = idx("Log Event")
    i_vis = idx("Visual Event")
    i_delta_a = idx("Delta(A)")

    if i_delta_a is None or (i_log is None and i_vis is None):
        return []

    out: List[Tuple[str, float, int]] = []

    for row_idx, parts in enumerate(_iter_markdown_table_rows(table_lines[1:])):
        if len(parts) <= i_delta_a:
            continue

        log_event = parts[i_log] if i_log is not None and len(parts) > i_log else ""
        vis_event = parts[i_vis] if i_vis is not None and len(parts) > i_vis else ""

        vis_clean = _clean_event_name(vis_event)
        log_clean = _clean_event_name(log_event)
        event = vis_clean if vis_clean else log_clean
        if not event:
            continue

        t = _parse_seconds(parts[i_delta_a])
        if t is None:
            continue

        out.append((event, t, row_idx))

    return out


def find_reports(root: Path, build_type: str) -> List[ReportRef]:
    out: List[ReportRef] = []
    if not root.exists() or not root.is_dir():
        return out

    # Only look into first-level subdirectories
    for p in root.iterdir():
        if p.is_dir():
            report_file = p / "merged_boot_report.md"
            if report_file.exists():
                run_id = p.name
                out.append(ReportRef(build_type=build_type, run_id=run_id, path=report_file))

    out.sort(key=lambda r: (r.run_id, str(r.path)))
    return out


def build_dataframe(reports: List[ReportRef]) -> pd.DataFrame:
    rows: List[Dict[str, object]] = []

    for ref in reports:
        try:
            events = parse_merged_boot_report(ref.path)
        except Exception as e:
            print(f"[warn] failed to parse {ref.path}: {e}", file=sys.stderr)
            continue

        for event, t, raw_idx in events:
            rows.append(
                {
                    "build_type": ref.build_type,
                    "run_id": ref.run_id,
                    "event": event,
                    "time": float(t),
                    "raw_idx": raw_idx,
                }
            )

    df = pd.DataFrame(rows)
    if df.empty:
        return df

    df = df.dropna(subset=["build_type", "run_id", "event", "time"])
    df["event"] = df["event"].astype(str)
    df["event_key"] = df["event"].str.lower().str.strip()

    df = (
        df.sort_values(["build_type", "run_id", "event_key", "time"], ascending=True)
        .groupby(["build_type", "run_id", "event_key"], as_index=False)
        .first()
    )

    return df


def safe_to_markdown(df: pd.DataFrame) -> str:
    if df.empty:
        return "(no data)"
    try:
        return df.to_markdown(index=False)
    except Exception:
        cols = list(df.columns)
        rows = df.values.tolist()
        header = "| " + " | ".join(str(c) for c in cols) + " |\n"
        sep = "|" + "|".join(["---"] * len(cols)) + "|\n"
        body = "".join("| " + " | ".join(str(v) for v in r) + " |\n" for r in rows)
        return header + sep + body


def _format_float(v: object, ndigits: int = 3) -> str:
    try:
        if pd.isna(v):
            return "N/A"
        return f"{float(v):.{ndigits}f}"
    except Exception:
        return "N/A"


def _top_changes(df: pd.DataFrame, abs_col: str, label_col: str, n: int = 5) -> Tuple[pd.DataFrame, pd.DataFrame]:
    if df.empty or abs_col not in df.columns:
        return pd.DataFrame(), pd.DataFrame()

    tmp = df.copy()
    tmp[abs_col] = pd.to_numeric(tmp[abs_col], errors="coerce")
    tmp = tmp.dropna(subset=[abs_col])
    if tmp.empty:
        return pd.DataFrame(), pd.DataFrame()

    improved = tmp[tmp[abs_col] > 0].sort_values(abs_col, ascending=False).head(n)
    regressed = tmp[tmp[abs_col] < 0].sort_values(abs_col, ascending=True).head(n)

    cols = [c for c in [label_col, abs_col] if c in tmp.columns]
    return improved[cols], regressed[cols]


def generate_markdown_report(
    *,
    out_path: Path,
    a_name: str,
    b_name: str,
    a_dir: Path,
    b_dir: Path,
    a_reports: List[ReportRef],
    b_reports: List[ReportRef],
    df: pd.DataFrame,
    all_events_avg: pd.DataFrame,
    milestones_df: pd.DataFrame,
    phases_df: pd.DataFrame,
    milestones_png: Path,
    phases_png: Path,
) -> None:
    total_runs = df[["build_type", "run_id"]].drop_duplicates().shape[0] if not df.empty else 0
    runs_by_build = (
        df[["build_type", "run_id"]].drop_duplicates().groupby("build_type").size().to_dict() if not df.empty else {}
    )
    events_by_build = df.groupby("build_type").size().to_dict() if not df.empty else {}

    missing_events = pd.DataFrame()
    if not df.empty:
        cover = (
            df.groupby(["build_type", "event_key"], as_index=False)
            .size()
            .rename(columns={"size": "count"})
        )
        cover = cover.pivot(index="event_key", columns="build_type", values="count").fillna(0).astype(int)
        cover = cover.reset_index()
        cover = cover.rename(columns={"event_key": "event_id"})
        missing_events = cover[(cover.get(a_name, 0) == 0) | (cover.get(b_name, 0) == 0)].copy()

    top_ms_improve, top_ms_regress = _top_changes(milestones_df, "abs_improvement", "milestone", n=5)
    top_ph_improve, top_ph_regress = _top_changes(phases_df, "abs_improvement", "phase", n=5)

    with out_path.open("w", encoding="utf-8") as f:
        f.write(f"# Boot Time A/B Comparison Report\n\n")
        f.write(f"This report compares boot-time events between **{a_name}** (A) and **{b_name}** (B).\n\n")

        f.write("## 1. Inputs\n\n")
        f.write(f"- **A**: `{a_name}` @ `{a_dir}`\n")
        f.write(f"- **B**: `{b_name}` @ `{b_dir}`\n")
        f.write(f"- **Found reports**: A={len(a_reports)}, B={len(b_reports)}\n")
        f.write("\n")

        f.write("## 2. Data Coverage\n\n")
        f.write(f"- **Parsed runs**: {total_runs}\n")
        f.write(f"- **Runs by build**: A={runs_by_build.get(a_name, 0)}, B={runs_by_build.get(b_name, 0)}\n")
        f.write(f"- **Parsed event rows**: A={events_by_build.get(a_name, 0)}, B={events_by_build.get(b_name, 0)}\n\n")
        if not missing_events.empty:
            f.write("### Events missing in either build (coverage=0)\n\n")
            f.write(safe_to_markdown(missing_events))
            f.write("\n")

        f.write("## 3. Average Time for Each Event (All Events)\n\n")
        f.write(safe_to_markdown(all_events_avg))
        f.write("\n")

        f.write("## 4. Key Milestones\n\n")
        f.write(safe_to_markdown(milestones_df))
        f.write("\n")
        if milestones_png.exists():
            f.write("### Milestones Chart\n\n")
            f.write(f"![]({milestones_png.name})\n\n")

        f.write("## 5. Boot Phases\n\n")
        f.write(safe_to_markdown(phases_df))
        f.write("\n")
        if phases_png.exists():
            f.write("### Phases Chart\n\n")
            f.write(f"![]({phases_png.name})\n\n")

        f.write("## 6. Observations\n\n")
        f.write(f"- **Improvement definition**: `abs_improvement = avg_{b_name} - avg_{a_name}`. Positive means A is faster than B.\n")

        if not top_ms_improve.empty:
            f.write("\n### Top milestone improvements (A faster)\n\n")
            f.write(safe_to_markdown(top_ms_improve))
            f.write("\n")
        if not top_ms_regress.empty:
            f.write("\n### Top milestone regressions (A slower)\n\n")
            f.write(safe_to_markdown(top_ms_regress))
            f.write("\n")

        if not top_ph_improve.empty:
            f.write("\n### Top phase improvements (A faster)\n\n")
            f.write(safe_to_markdown(top_ph_improve))
            f.write("\n")
        if not top_ph_regress.empty:
            f.write("\n### Top phase regressions (A slower)\n\n")
            f.write(safe_to_markdown(top_ph_regress))
            f.write("\n")


def _pick_time_for_event(run_df: pd.DataFrame, event_key: str) -> Optional[float]:
    row = run_df.loc[run_df["event_key"] == event_key]
    if row.empty:
        return None
    return float(row.iloc[0]["time"])


def _normalize_event_keys(spec: object) -> List[str]:
    if spec is None:
        return []
    if isinstance(spec, str):
        return [spec]
    if isinstance(spec, (list, tuple)):
        out: List[str] = []
        for x in spec:
            if isinstance(x, str) and x.strip():
                out.append(x)
        return out
    return []


def _pick_time_for_any_event(run_df: pd.DataFrame, event_keys: object) -> Optional[float]:
    for k in _normalize_event_keys(event_keys):
        t = _pick_time_for_event(run_df, k)
        if t is not None:
            return t
    return None


def compute_phase_durations(df: pd.DataFrame, phases: Dict[str, Tuple[object, Optional[object]]]) -> pd.DataFrame:
    rows: List[Dict[str, object]] = []

    for (build_type, run_id), run_df in df.groupby(["build_type", "run_id"]):
        for phase_name, (end_keys, start_keys) in phases.items():
            end_t = _pick_time_for_any_event(run_df, end_keys)
            start_t = 0.0 if start_keys is None else _pick_time_for_any_event(run_df, start_keys)
            if end_t is None or start_t is None:
                continue
            duration = end_t - start_t
            if duration < 0:
                continue
            rows.append(
                {
                    "build_type": build_type,
                    "run_id": run_id,
                    "phase": phase_name,
                    "duration": duration,
                }
            )

    out = pd.DataFrame(rows)
    if out.empty:
        return out
    out["phase"] = out["phase"].astype(str)
    return out


def milestone_summary(df: pd.DataFrame, milestones: List[str], build_types: List[str]) -> pd.DataFrame:
    if df.empty:
        return df

    wanted = [m.lower().strip() for m in milestones]
    sub = df[df["event_key"].isin(wanted)].copy()
    if sub.empty:
        return sub

    avg = (
        sub.groupby(["build_type", "event_key"], as_index=False)
        .agg({"time": "mean", "raw_idx": "mean"})
        .rename(columns={"event_key": "event"})
    )

    avg["event"] = avg["event"].astype(str)
    avg["time"] = avg["time"].astype(float)

    pivot = avg.pivot(index="event", columns="build_type", values="time")

    a, b = build_types[0], build_types[1]
    if a not in pivot.columns:
        pivot[a] = pd.NA
    if b not in pivot.columns:
        pivot[b] = pd.NA

    pivot = pivot[[a, b]]
    pivot = pivot.reset_index()
    pivot = pivot.rename(columns={"event": "milestone", a: f"avg_{a}", b: f"avg_{b}"})

    # sort by average raw_idx to keep original chronological order
    order_map = avg.groupby("event")["raw_idx"].mean().to_dict()
    pivot["sort_key"] = pivot["milestone"].str.lower().str.strip().map(order_map)
    # If some milestones are not in order_map, they will be NaN in sort_key
    # We can fill them with a large value to put them at the end, or just sort
    pivot = pivot.sort_values("sort_key").drop(columns=["sort_key"])

    pivot["abs_improvement"] = pivot[f"avg_{b}"] - pivot[f"avg_{a}"]
    denom = pd.to_numeric(pivot[f"avg_{b}"], errors="coerce")
    pivot["pct_improvement"] = (pivot["abs_improvement"] / denom) * 100.0
    pivot.loc[denom.isna() | (denom <= 0), "pct_improvement"] = pd.NA

    return pivot


def event_average_summary(df: pd.DataFrame, build_types: List[str]) -> pd.DataFrame:
    if df.empty:
        return df

    a, b = build_types[0], build_types[1]

    def _mode(series: pd.Series) -> str:
        s = series.dropna().astype(str)
        if s.empty:
            return ""
        return s.value_counts().index[0]

    display = df.groupby("event_key")["event"].apply(_mode).reset_index(name="event")
    avg = df.groupby(["build_type", "event_key"], as_index=False).agg({"time": "mean", "raw_idx": "mean"})

    pivot = avg.pivot(index="event_key", columns="build_type", values="time")
    if a not in pivot.columns:
        pivot[a] = pd.NA
    if b not in pivot.columns:
        pivot[b] = pd.NA

    pivot = pivot[[a, b]].reset_index()
    pivot = pivot.rename(columns={a: f"avg_{a}", b: f"avg_{b}"})
    pivot = pivot.merge(display, on="event_key", how="left")

    # sort by average raw_idx to keep original chronological order
    order_map = avg.groupby("event_key")["raw_idx"].mean().sort_values().index.tolist()
    pivot["event_key"] = pd.Categorical(pivot["event_key"], categories=order_map, ordered=True)
    pivot = pivot.sort_values("event_key")
    pivot = pivot[["event", "event_key", f"avg_{a}", f"avg_{b}"]]
    pivot["event_key"] = pivot["event_key"].astype(str) # Convert back to string to avoid categorical issues later
    pivot = pivot.rename(columns={"event_key": "event_id"})
    return pivot


def phase_summary(phase_df: pd.DataFrame, build_types: List[str], phases: Dict[str, object]) -> pd.DataFrame:
    if phase_df.empty:
        return phase_df

    avg = (
        phase_df.groupby(["build_type", "phase"], as_index=False)["duration"]
        .mean()
        .rename(columns={"duration": "avg_duration"})
    )

    pivot = avg.pivot(index="phase", columns="build_type", values="avg_duration")
    a, b = build_types[0], build_types[1]
    if a not in pivot.columns:
        pivot[a] = pd.NA
    if b not in pivot.columns:
        pivot[b] = pd.NA
    pivot = pivot[[a, b]].reset_index()
    pivot = pivot.rename(columns={a: f"avg_{a}", b: f"avg_{b}"})

    # since phases are defined in a dictionary, they follow the defined sequence.
    # We should respect the dictionary order for the final table and stacked chart.
    phase_order = list(phases.keys())
    pivot["phase"] = pd.Categorical(pivot["phase"], categories=phase_order, ordered=True)
    pivot = pivot.sort_values("phase")
    pivot["phase"] = pivot["phase"].astype(str) # Convert back to string to avoid categorical issues later

    pivot["abs_improvement"] = pivot[f"avg_{b}"] - pivot[f"avg_{a}"]
    denom = pd.to_numeric(pivot[f"avg_{b}"], errors="coerce")
    pivot["pct_improvement"] = (pivot["abs_improvement"] / denom) * 100.0
    pivot.loc[denom.isna() | (denom <= 0), "pct_improvement"] = pd.NA
    return pivot


def plot_milestones(milestone_df: pd.DataFrame, a: str, b: str, out_path: Path) -> None:
    if milestone_df.empty:
        return

    labels = milestone_df["milestone"].astype(str).tolist()
    y = list(range(len(labels)))

    a_vals = pd.to_numeric(milestone_df[f"avg_{a}"], errors="coerce").fillna(0.0)
    b_vals = pd.to_numeric(milestone_df[f"avg_{b}"], errors="coerce").fillna(0.0)

    height = 0.35

    fig, ax = plt.subplots(figsize=(10, max(3.5, 0.5 * len(labels))))
    ax.barh([yy - height / 2 for yy in y], a_vals, height=height, label=a)
    ax.barh([yy + height / 2 for yy in y], b_vals, height=height, label=b)

    ax.set_yticks(y)
    ax.set_yticklabels(labels)
    ax.invert_yaxis()
    ax.set_xlabel("Average time (s)")
    ax.set_title(f"Boot milestones comparison: {a} vs {b}")
    ax.legend()
    fig.tight_layout()
    fig.savefig(out_path, dpi=150)
    plt.close(fig)


def plot_phases(phase_df: pd.DataFrame, a: str, b: str, out_path: Path) -> None:
    if phase_df.empty:
        return

    phases = phase_df["phase"].astype(str).tolist()

    avg_a = pd.to_numeric(phase_df[f"avg_{a}"], errors="coerce").fillna(0.0).tolist()
    avg_b = pd.to_numeric(phase_df[f"avg_{b}"], errors="coerce").fillna(0.0).tolist()

    fig, ax = plt.subplots(figsize=(10, 4.5))

    bottoms_a = 0.0
    bottoms_b = 0.0

    cmap = plt.get_cmap("tab20")
    for i, ph in enumerate(phases):
        color = cmap(i % 20)
        ax.bar([a, b], [avg_a[i], avg_b[i]], bottom=[bottoms_a, bottoms_b], color=color, label=ph)
        bottoms_a += avg_a[i]
        bottoms_b += avg_b[i]

    ax.set_ylabel("Average duration (s)")
    ax.set_title(f"Boot phases (stacked): {a} vs {b}")

    ax.legend(bbox_to_anchor=(1.02, 1), loc="upper left")

    fig.tight_layout()
    fig.savefig(out_path, dpi=150)
    plt.close(fig)


def main(argv: Optional[List[str]] = None) -> int:
    p = argparse.ArgumentParser(prog="boot_analysis.py")
    p.add_argument("--a-name", required=True, help="Name of build A (baseline)")
    p.add_argument("--a-dir", required=True, help="Directory of build A (will be searched recursively)")
    p.add_argument("--b-name", required=True, help="Name of build B (comparison)")
    p.add_argument("--b-dir", required=True, help="Directory of build B (will be searched recursively)")
    p.add_argument("--out-dir", default=".", help="Output directory for PNG charts")
    p.add_argument("--report-name", default="boot_ab_compare_report.md", help="Markdown report filename under out-dir")
    p.add_argument(
        "--milestone",
        action="append",
        dest="milestones",
        help="Milestone event name (can be repeated). Default: Boot Logo, bootanim start, bootanim end, Launcher start, Launcher loaded",
    )

    args = p.parse_args(argv)

    a_name = args.a_name
    b_name = args.b_name
    a_dir = Path(args.a_dir)
    b_dir = Path(args.b_dir)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    report_name = args.report_name

    milestones = args.milestones or [
        "Boot Logo",
        "bootanim start",
        "bootanim end",
        "Launcher start",
        "Launcher loaded",
    ]

    phases: Dict[str, Tuple[object, Optional[object]]] = {
        "Boot Logo": ("boot logo", None),
        "Animation Start": ("bootanim start", "boot logo"),
        "Animation Duration": ("bootanim end", "bootanim start"),
        "Launcher Loading": (["launcher loaded", "launcher start"], "bootanim end"),
    }

    a_reports = find_reports(a_dir, a_name)
    b_reports = find_reports(b_dir, b_name)

    print(f"[info] A: name={a_name} dir={a_dir} reports={len(a_reports)}")
    print(f"[info] B: name={b_name} dir={b_dir} reports={len(b_reports)}")

    df = build_dataframe(a_reports + b_reports)
    if df.empty:
        print("[error] no events parsed", file=sys.stderr)
        return 2

    print("\n## Average time for each event (all events)\n")
    ev = event_average_summary(df, build_types=[a_name, b_name])
    ev_display = ev.copy()
    if not ev_display.empty:
        for c in [f"avg_{a_name}", f"avg_{b_name}"]:
            if c in ev_display.columns:
                ev_display[c] = pd.to_numeric(ev_display[c], errors="coerce").round(3)
    print(safe_to_markdown(ev_display))

    print("\n## Average milestone timings\n")
    ms = milestone_summary(df, milestones, build_types=[a_name, b_name])
    ms_display = ms.copy()
    if not ms_display.empty:
        for c in [f"avg_{a_name}", f"avg_{b_name}", "abs_improvement", "pct_improvement"]:
            if c in ms_display.columns:
                ms_display[c] = pd.to_numeric(ms_display[c], errors="coerce").round(3)
    print(safe_to_markdown(ms_display))

    phase_df = compute_phase_durations(df, phases)
    print("\n## Average phase durations\n")
    ph = phase_summary(phase_df, build_types=[a_name, b_name], phases=phases)
    ph_display = ph.copy()
    if not ph_display.empty:
        for c in [f"avg_{a_name}", f"avg_{b_name}", "abs_improvement", "pct_improvement"]:
            if c in ph_display.columns:
                ph_display[c] = pd.to_numeric(ph_display[c], errors="coerce").round(3)
    print(safe_to_markdown(ph_display))

    milestones_png = out_dir / "boot_milestones_comparison_v2.png"
    if not ms.empty:
        plot_milestones(ms, a_name, b_name, milestones_png)
        print(f"\n[info] saved: {milestones_png}")

    phases_png = out_dir / "boot_phases_comparison_v2.png"
    if not ph.empty:
        plot_phases(ph, a_name, b_name, phases_png)
        print(f"[info] saved: {phases_png}")

    report_path = out_dir / report_name
    try:
        generate_markdown_report(
            out_path=report_path,
            a_name=a_name,
            b_name=b_name,
            a_dir=a_dir,
            b_dir=b_dir,
            a_reports=a_reports,
            b_reports=b_reports,
            df=df,
            all_events_avg=ev_display,
            milestones_df=ms_display,
            phases_df=ph_display,
            milestones_png=milestones_png,
            phases_png=phases_png,
        )
        print(f"[info] saved: {report_path}")
    except Exception as e:
        print(f"[warn] failed to write markdown report: {e}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
