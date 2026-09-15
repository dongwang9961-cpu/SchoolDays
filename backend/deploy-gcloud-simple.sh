#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")"

# Optional: run the full test suite first.
# mvn clean test

gcloud run deploy schooldays \
  --source . \
  --region us-central1
