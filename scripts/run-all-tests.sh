#!/bin/sh
# Runs every test of the prototype in one go (see tests/run_all.py for the options).
cd "$(dirname "$0")/.." && exec python3 tests/run_all.py "$@"
