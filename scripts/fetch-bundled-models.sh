#!/usr/bin/env bash
# Fetch + verify the on-device model weights BUNDLED into the Android APK.
#
# These files are deliberately NOT committed to git (.gitignore: *.onnx, *.task,
# **/models/). Run this once after cloning, before building the Android app, so the
# bundled-model assets exist. Each file is verified against a pinned sha256.
#
#   ./scripts/fetch-bundled-models.sh
#
# Equivalent Gradle path: ./gradlew :androidApp:bundleModels
#
# Models (both ungated, Apache-2.0):
#   - all-MiniLM-L6-v2  INT8 ONNX embedder (~22 MB) + bert-base-uncased vocab
#   - SmolLM-135M-Instruct  MediaPipe .task chat model, q8 (~159 MB)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EMB="$ROOT/androidApp/src/main/assets/models/all-MiniLM-L6-v2"
LLM="$ROOT/androidApp/src/main/assets/models/llm"
mkdir -p "$EMB" "$LLM"

fetch() { # url dest sha256 label
  local url="$1" dest="$2" sha="$3" label="$4"
  if [ ! -s "$dest" ]; then
    echo "↓ downloading $label …"
    curl -fSL -m 1200 -o "$dest" "$url"
  fi
  local got
  got="$(sha256sum "$dest" | awk '{print $1}')"
  if [ "$got" != "$sha" ]; then
    echo "✗ $label sha256 mismatch:" >&2
    echo "    expected $sha" >&2
    echo "    got      $got" >&2
    exit 1
  fi
  echo "✔ $label ok ($(stat -c%s "$dest") bytes, sha256 verified)"
}

fetch "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx" \
  "$EMB/model.onnx" \
  "afdb6f1a0e45b715d0bb9b11772f032c399babd23bfc31fed1c170afc848bdb1" \
  "all-MiniLM-L6-v2 model.onnx (int8)"

fetch "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/vocab.txt" \
  "$EMB/vocab.txt" \
  "07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3" \
  "all-MiniLM-L6-v2 vocab.txt"

fetch "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task" \
  "$LLM/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task" \
  "6987dce5ac4f71032b070cf13412a5de0e49c04d271a053fc7d9d59a0dc104e9" \
  "SmolLM-135M-Instruct .task (q8)"

echo "All bundled models present + verified."
