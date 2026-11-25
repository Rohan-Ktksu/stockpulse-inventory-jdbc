#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
mkdir -p out
find src -name "*.java" > sources.txt
javac -encoding UTF-8 -d out @sources.txt
rm -f sources.txt
echo "[OK] Compiled successfully into out/"
