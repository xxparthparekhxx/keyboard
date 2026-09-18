#!/bin/bash
# Tune, evaluate, and report after the encoder run finishes.
#
# Usage: run_after_training.sh [--pid <training-pid>] [--procs N] [--ckpt PATH]
#
# Previously this hardcoded --procs 30 and busy-waited on `pgrep -f
# swipe.train` (which can match unrelated processes, including itself on some
# platforms). Now it waits on an explicit PID when given, sizes the worker
# pool from nproc by default, and fails loudly instead of hanging forever.
set -u
cd "$(dirname "$0")"

PID=""
PROCS="$(nproc 2>/dev/null || echo 8)"
CKPT="runs/encoder/best.pt"
TIMEOUT_S=86400  # 24h; training that overruns this is stuck, not slow

while [ $# -gt 0 ]; do
    case "$1" in
        --pid) PID="${2:?--pid needs a value}"; shift 2 ;;
        --procs) PROCS="${2:?--procs needs a value}"; shift 2 ;;
        --ckpt) CKPT="${2:?--ckpt needs a value}"; shift 2 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

if [ -n "$PID" ]; then
    echo "waiting on training pid $PID (timeout ${TIMEOUT_S}s)..."
    waited=0
    while kill -0 "$PID" 2>/dev/null; do
        if [ "$waited" -ge "$TIMEOUT_S" ]; then
            echo "timed out waiting for pid $PID" >&2
            exit 1
        fi
        sleep 20
        waited=$((waited + 20))
    done
else
    echo "no --pid given; waiting for runs/encoder/best.pt to appear..."
    waited=0
    while [ ! -f "$CKPT" ]; do
        if [ "$waited" -ge "$TIMEOUT_S" ]; then
            echo "timed out waiting for $CKPT" >&2
            exit 1
        fi
        sleep 20
        waited=$((waited + 20))
    done
fi

echo "=== TRAINING DONE ==="
tail -3 runs/encoder.log 2>/dev/null || true

echo; echo "=== STAGE 1+2: tuning pruning and scoring on dev ==="
./.venv/bin/python tools/tune_scoring.py --ckpt "$CKPT" \
  --n 12000 --prune-trials 25 --score-trials 2500 --procs "$PROCS" 2>&1 | tail -12

echo; echo "=== FULL EVAL: test split ==="
./.venv/bin/python tools/eval_neural.py --split test --extend --beam 100 2>&1 | tail -4
./.venv/bin/python tools/eval_neural.py --split test --beam 100 2>&1 | tail -4
echo "PIPELINE DONE"
