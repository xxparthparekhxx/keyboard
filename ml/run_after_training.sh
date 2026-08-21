#!/bin/bash
cd /home/xxpp/code/xxpp/keyboard/ml
# wait for the encoder run to exit
while pgrep -f "swipe.train" > /dev/null; do sleep 20; done
echo "=== TRAINING DONE ==="
tail -3 runs/encoder.log

echo; echo "=== STAGE 1+2: tuning pruning and scoring on dev ==="
./.venv/bin/python tools/tune_scoring.py --ckpt runs/encoder/best.pt \
  --n 12000 --prune-trials 25 --score-trials 2500 --procs 30 2>&1 | tail -12

echo; echo "=== FULL EVAL: test split ==="
./.venv/bin/python tools/eval_neural.py --split test --extend --beam 100 2>&1 | tail -4
./.venv/bin/python tools/eval_neural.py --split test --beam 100 2>&1 | tail -4
echo "PIPELINE DONE"
