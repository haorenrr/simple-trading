#!/usr/bin/env bash

PID_FILE="logs/pids.txt"

if [ ! -f "$PID_FILE" ]; then
  echo "No pid file found."
  exit 0
fi

echo "Stopping services..."

while read -r PID; do
  if ps -p "$PID" > /dev/null 2>&1; then
    kill "$PID"
    echo "Stopped pid=$PID"
  fi
done < "$PID_FILE"

rm -f "$PID_FILE"
echo "All services stopped."

