#!/usr/bin/env bash
# SIGHT model setup — the COCO SSD MobileNet model is already bundled in
# app/src/main/assets/. This script re-downloads it from TensorFlow's
# official model zoo if you ever delete it.
set -euo pipefail
ASSETS="$(dirname "$0")/../app/src/main/assets"
mkdir -p "$ASSETS"
cd /tmp
curl -L -o coco_ssd.zip "https://storage.googleapis.com/download.tensorflow.org/models/tflite/coco_ssd_mobilenet_v1_1.0_quant_2018_06_29.zip"
unzip -o coco_ssd.zip
cp detect.tflite "$ASSETS/coco_ssd_mobilenet.tflite"
cp labelmap.txt "$ASSETS/labelmap.txt"
echo "Model installed to $ASSETS"
ls -lh "$ASSETS"
