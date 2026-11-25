#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
if [ ! -f out/com/stockpulse/Main.class ]; then
  ./compile.sh
fi
java -cp "out:lib/*" com.stockpulse.Main
