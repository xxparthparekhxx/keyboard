#!/bin/bash
# upload_to_hf.sh — Upload model weights and lexicon to Hugging Face
#
# Prerequisites:
#   Install the hf CLI: https://huggingface.co/docs/huggingface_hub/guides/cli
#   hf auth login
#
# Usage:
#   ./ml/upload_to_hf.sh [REPO_ID]
#
# Example:
#   ./ml/upload_to_hf.sh xxparthparekhxx/compose-keyboard-swipe-encoder

set -euo pipefail

REPO_ID="${1:-xxparthparekhxx/compose-keyboard-swipe-encoder}"
ASSETS_DIR="app/src/main/assets"
MODEL_CARD="ml/MODEL_CARD.md"

echo "╔════════════════════════════════════════════════════════╗"
echo "║  Compose Keyboard — Hugging Face Model Upload         ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""
echo "Repository: $REPO_ID"
echo ""

# Check prerequisites
if ! command -v huggingface-cli &> /dev/null; then
    echo "❌ huggingface-cli not found. Install with: pip install huggingface_hub"
    exit 1
fi

# Check that the files exist
if [ ! -f "$ASSETS_DIR/swipe_encoder.bin" ]; then
    echo "❌ $ASSETS_DIR/swipe_encoder.bin not found."
    echo "   Run the export first: python ml/tools/export_weights.py"
    exit 1
fi

if [ ! -f "$ASSETS_DIR/swipe_words.txt" ]; then
    echo "❌ $ASSETS_DIR/swipe_words.txt not found."
    exit 1
fi

# Create repo if it doesn't exist (ignore error if it already exists)
echo "📦 Creating repository (if needed)..."
huggingface-cli repo create "$(basename "$REPO_ID")" --type model 2>/dev/null || true

# Upload the model card as README.md
echo "📄 Uploading model card..."
huggingface-cli upload "$REPO_ID" "$MODEL_CARD" README.md --repo-type model

# Upload the encoder weights
echo "🧠 Uploading swipe_encoder.bin ($(du -h "$ASSETS_DIR/swipe_encoder.bin" | cut -f1))..."
huggingface-cli upload "$REPO_ID" "$ASSETS_DIR/swipe_encoder.bin" swipe_encoder.bin --repo-type model

# Upload the lexicon
echo "📖 Uploading swipe_words.txt ($(du -h "$ASSETS_DIR/swipe_words.txt" | cut -f1))..."
huggingface-cli upload "$REPO_ID" "$ASSETS_DIR/swipe_words.txt" swipe_words.txt --repo-type model

echo ""
echo "✅ Upload complete!"
echo ""
echo "🔗 View your model: https://huggingface.co/$REPO_ID"
echo ""
echo "📥 Download commands:"
echo "   curl -L https://huggingface.co/$REPO_ID/resolve/main/swipe_encoder.bin -o swipe_encoder.bin"
echo "   curl -L https://huggingface.co/$REPO_ID/resolve/main/swipe_words.txt -o swipe_words.txt"
