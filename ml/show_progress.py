#!/usr/bin/env python3
"""
Training & Pipeline Progress Monitor for Neural Swipe Decoder.

Usage:
    python ml/show_progress.py          # One-shot summary
    python ml/show_progress.py --watch  # Auto-refreshing live dashboard (Ctrl+C to exit)
    python ml/show_progress.py -w 5     # Refresh every 5 seconds
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
LOG_FILE = BASE_DIR / "runs" / "encoder.log"
HISTORY_FILE = BASE_DIR / "runs" / "encoder" / "history.json"
PIPELINE_LOG = BASE_DIR / "runs" / "pipeline.log"
ARGS_FILE = BASE_DIR / "runs" / "encoder" / "args.json"

STEP_RE = re.compile(
    r"step\s+(\d+)/(\d+)\s+ctc\s+([\d\.]+)\s+emit\s+([\d\.]+)\s+lr\s+([\deE\.\+-]+)\s+([\d\.]+)\s+it/s\s+([\d\.]+)\s+min"
)
VAL_RE = re.compile(r"\[val\]\s+step\s+(\d+)\s+greedy word acc\s+([\d\.]+)(\s+\*)?")


def get_process_status() -> tuple[bool, int | None, bool, int | None]:
    """Returns (train_running, train_pid, pipeline_running, pipeline_pid)"""
    train_running, train_pid = False, None
    pipeline_running, pipeline_pid = False, None
    try:
        out = subprocess.check_output(["pgrep", "-fa", "swipe.train|run_after_training"], text=True)
        for line in out.strip().split("\n"):
            if not line:
                continue
            parts = line.strip().split(maxsplit=1)
            if len(parts) < 2:
                continue
            pid, cmd = int(parts[0]), parts[1]
            if "swipe.train" in cmd:
                train_running, train_pid = True, pid
            elif "run_after_training" in cmd:
                pipeline_running, pipeline_pid = True, pid
    except (subprocess.CalledProcessError, FileNotFoundError):
        pass
    return train_running, train_pid, pipeline_running, pipeline_pid


def parse_log() -> dict:
    info = {
        "step": 0,
        "total": 105000,
        "ctc": None,
        "emit": None,
        "lr": None,
        "speed": None,
        "elapsed_min": None,
        "val_history": [],
        "best_acc": 0.0,
        "best_step": 0,
        "completed": False,
    }

    if ARGS_FILE.exists():
        try:
            with open(ARGS_FILE) as f:
                args = json.load(f)
                if "epochs" in args:
                    info["total"] = args.get("max_steps") or (args["epochs"] * 875)
        except Exception:
            pass

    if HISTORY_FILE.exists():
        try:
            with open(HISTORY_FILE) as f:
                hist = json.load(f)
                info["val_history"] = hist
                for h in hist:
                    if h.get("greedy_acc", 0.0) > info["best_acc"]:
                        info["best_acc"] = h["greedy_acc"]
                        info["best_step"] = h["step"]
        except Exception:
            pass

    if LOG_FILE.exists():
        try:
            with open(LOG_FILE, "r", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    m = STEP_RE.search(line)
                    if m:
                        info["step"] = int(m.group(1))
                        info["total"] = int(m.group(2))
                        info["ctc"] = float(m.group(3))
                        info["emit"] = float(m.group(4))
                        info["lr"] = float(m.group(5))
                        info["speed"] = float(m.group(6))
                        info["elapsed_min"] = float(m.group(7))
                    if "done in" in line:
                        info["completed"] = True
        except Exception:
            pass

    return info


def render_bar(current: int, total: int, width: int = 35) -> str:
    if total <= 0:
        return ""
    frac = min(max(current / total, 0.0), 1.0)
    filled = int(round(frac * width))
    bar = "█" * filled + "░" * (width - filled)
    pct = frac * 100
    return f"[{bar}] {pct:5.1f}% ({current:,}/{total:,})"


def render_sparkline(values: list[float], width: int = 24) -> str:
    if not values:
        return ""
    ticks = " ▂▃▄▅▆▇█"
    min_v, max_v = min(values), max(values)
    rng = max_v - min_v if max_v > min_v else 1.0
    if len(values) > width:
        step = len(values) / width
        sampled = [values[int(i * step)] for i in range(width)]
    else:
        sampled = values

    chars = []
    for v in sampled:
        idx = int(round(((v - min_v) / rng) * (len(ticks) - 1)))
        idx = min(max(idx, 0), len(ticks) - 1)
        chars.append(ticks[idx])
    return "".join(chars)


def get_gpu_info() -> str:
    try:
        out = subprocess.check_output(
            ["nvidia-smi", "--query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu", "--format=csv,noheader,nounits"],
            text=True,
            stderr=subprocess.DEVNULL
        ).strip()
        parts = [p.strip() for p in out.split(",")]
        if len(parts) >= 4:
            return f"GPU Util: {parts[0]}% | VRAM: {parts[1]}/{parts[2]} MiB | Temp: {parts[3]}°C"
    except Exception:
        pass
    return ""


def format_duration(minutes: float) -> str:
    if minutes < 0:
        return "0s"
    total_sec = int(minutes * 60)
    h = total_sec // 3600
    m = (total_sec % 3600) // 60
    s = total_sec % 60
    if h > 0:
        return f"{h}h {m:02d}m {s:02d}s"
    return f"{m}m {s:02d}s"


def print_dashboard():
    train_running, train_pid, pipe_running, pipe_pid = get_process_status()
    info = parse_log()

    CYAN = "\033[96m"
    GREEN = "\033[92m"
    YELLOW = "\033[93m"
    MAGENTA = "\033[95m"
    BOLD = "\033[1m"
    DIM = "\033[2m"
    RESET = "\033[0m"

    print(f"\n{BOLD}{CYAN}═════════════════════════════════════════════════════════════════════{RESET}")
    print(f"{BOLD}{CYAN}            🧠 NEURAL SWIPE DECODER — TRAINING PROGRESS            {RESET}")
    print(f"{BOLD}{CYAN}═════════════════════════════════════════════════════════════════════{RESET}\n")

    # 1. Process Status
    if train_running:
        status_str = f"{GREEN}● RUNNING{RESET} (PID {train_pid})"
    elif info["completed"] or (info["step"] >= info["total"] and info["total"] > 0):
        status_str = f"{GREEN}✔ COMPLETED{RESET}"
    else:
        status_str = f"{YELLOW}■ STOPPED / IDLE{RESET}"

    pipe_str = ""
    if pipe_running:
        pipe_str = f"  |  Chained Pipeline: {GREEN}● ACTIVE{RESET} (PID {pipe_pid})"
    elif PIPELINE_LOG.exists() and PIPELINE_LOG.stat().st_size > 0:
        pipe_str = f"  |  Chained Pipeline: {CYAN}● LOGGED{RESET}"

    print(f" {BOLD}Status:{RESET} {status_str}{pipe_str}")

    gpu_info = get_gpu_info()
    if gpu_info:
        print(f" {DIM}{gpu_info}{RESET}")
    print()

    # 2. Progress Bar & ETA
    step = info["step"]
    total = info["total"]
    speed = info["speed"] or 0.0
    elapsed_min = info["elapsed_min"] or 0.0

    bar = render_bar(step, total, width=32)
    print(f" {BOLD}Progress:{RESET}  {bar}")

    if train_running and speed > 0 and step < total:
        remaining_steps = total - step
        remaining_sec = remaining_steps / speed
        eta_min = remaining_sec / 60
        print(f" {BOLD}Time:{RESET}      Elapsed: {format_duration(elapsed_min)}  |  {BOLD}ETA:{RESET} {YELLOW}{format_duration(eta_min)}{RESET}  ({speed:.1f} it/s)")
    else:
        print(f" {BOLD}Time:{RESET}      Elapsed: {format_duration(elapsed_min)}  |  Speed: {speed:.1f} it/s")

    print()

    # 3. Current Training Metrics
    ctc = f"{info['ctc']:.4f}" if info["ctc"] is not None else "N/A"
    emit = f"{info['emit']:.4f}" if info["emit"] is not None else "N/A"
    lr = f"{info['lr']:.2e}" if info["lr"] is not None else "N/A"

    print(f" {BOLD}Metrics:{RESET}   CTC Loss: {MAGENTA}{ctc}{RESET}  |  Emit Penalty: {MAGENTA}{emit}{RESET}  |  LR: {lr}")

    # 4. Validation History
    history = info["val_history"]
    if history:
        accs = [h.get("greedy_acc", 0.0) for h in history]
        spark = render_sparkline(accs, width=20)
        best_acc_pct = info["best_acc"] * 100
        latest_acc_pct = accs[-1] * 100 if accs else 0.0

        print(f" {BOLD}Val Acc:{RESET}   Latest: {BOLD}{latest_acc_pct:.2f}%{RESET} (step {history[-1]['step']:,})  |  Best: {GREEN}{BOLD}{best_acc_pct:.2f}%{RESET} (step {info['best_step']:,})")
        print(f" {BOLD}Trend:{RESET}     [{spark}] ({min(accs)*100:.1f}% → {max(accs)*100:.1f}%)")
        print(f" {DIM}           (Greedy exact match on 20k dev samples without beam/lexicon){RESET}")

        # Table of last 6 evaluations
        print(f"\n {DIM}Recent Evaluations:{RESET}")
        print(f"   {DIM}{'Step':>8}  {'Greedy Acc':>12}  {'CTC Loss':>10}  {''}{RESET}")
        print(f"   {DIM}{'─'*8}  {'─'*12}  {'─'*10}  {'─'*3}{RESET}")
        for entry in history[-6:]:
            s = entry.get("step", 0)
            acc = entry.get("greedy_acc", 0.0) * 100
            loss = entry.get("ctc", 0.0)
            is_best = (s == info["best_step"])
            star = f"{GREEN}★ BEST{RESET}" if is_best else ""
            print(f"   {s:8d}  {acc:11.2f}%  {loss:10.4f}  {star}")

    # 5. Pipeline Output if any
    if PIPELINE_LOG.exists() and PIPELINE_LOG.stat().st_size > 0:
        try:
            content = PIPELINE_LOG.read_text().strip()
            if content:
                print(f"\n {BOLD}{CYAN}Pipeline / Eval Log:{RESET}")
                for line in content.splitlines()[-10:]:
                    print(f"   {DIM}│{RESET} {line}")
        except Exception:
            pass

    print(f"\n{BOLD}{CYAN}═════════════════════════════════════════════════════════════════════{RESET}\n")


def main():
    parser = argparse.ArgumentParser(description="Show Neural Swipe Decoder training progress.")
    parser.add_argument("-w", "--watch", nargs="?", const=3, type=int, help="Live refresh interval in seconds (default: 3)")
    args = parser.parse_args()

    if args.watch is not None:
        interval = max(args.watch, 1)
        try:
            while True:
                print("\033[H\033[J", end="")
                print_dashboard()
                print(f"\033[2mRefreshing every {interval}s (Press Ctrl+C to exit)...\033[0m")
                time.sleep(interval)
        except KeyboardInterrupt:
            print("\nExited monitor.")
    else:
        print_dashboard()


if __name__ == "__main__":
    main()
