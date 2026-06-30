#!/usr/bin/env bash
# Fetch + verify the on-device embedding model BUNDLED into the Android APK.
#
# Only the small ONNX embedder is bundled (so memory/search works out of the box).
# The CHAT model is download-on-demand in-app (Settings → pick a model from the
# catalog), NOT bundled — so it is not fetched here.
#
# These files are deliberately NOT committed to git (.gitignore: *.onnx, *.task,
# **/models/). Run this once after cloning, before building the Android app, so the
# bundled embedder asset exists. Each file is verified against a pinned sha256.
#
#   ./scripts/fetch-bundled-models.sh
#
# Equivalent Gradle path: ./gradlew :androidApp:bundleModels
#
# Bundled (ungated, Apache-2.0):
#   - all-MiniLM-L6-v2  INT8 ONNX embedder (~22 MB) + bert-base-uncased vocab
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EMB="$ROOT/androidApp/src/main/assets/models/all-MiniLM-L6-v2"
mkdir -p "$EMB"

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

echo "Bundled embedder present + verified. (Chat model is download-on-demand in-app.)"
