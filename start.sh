#!/bin/bash

mkdir -p logs 
PID_FILE="logs/pids.txt"
> "$PID_FILE"

SERVICES=(
  "java -jar ./registry-server/target/registry-server-1.0.0-SNAPSHOT.jar"
  "java -jar ./sequence-engine/target/sequence-engine-1.0.0-SNAPSHOT-exec.jar"
  "java -jar ./web/target/web-1.0.0-SNAPSHOT.jar"
)

echo "Starting services..."

for CMD in "${SERVICES[@]}"; do
  nohup $CMD >/dev/null 2>&1 &
  PID=$!
  echo "$PID" >> "$PID_FILE"
  echo "[$CMD] started, pid=$PID"
  sleep 15
done

echo "All services started."
