#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")"

mvn test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.schooldays.codegen.GenerateJooqClasses
