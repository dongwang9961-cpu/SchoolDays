#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")"

# Optional: run the full test suite first.
# mvn clean test

gcloud run deploy schooldays \
  --source . \
  --region us-central1 \
  --allow-unauthenticated \
  --env-vars-file env.yaml \
  --set-secrets="SCHOOLDAYS_GMAIL_CLIENT_SECRET=SCHOOLDAYS_GMAIL_CLIENT_SECRET:latest,SCHOOLDAYS_JWT_SECRET=SCHOOLDAYS_JWT_SECRET:latest,SCHOOLDAYS_SYSTEM_EMAIL_API_KEY=SCHOOLDAYS_SYSTEM_EMAIL_API_KEY:latest,SPRING_DATASOURCE_PASSWORD=SPRING_DATASOURCE_PASSWORD:latest"
