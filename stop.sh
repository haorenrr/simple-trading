#!/bin/bash

echo "Stopping services..."

SERVICE1="./sequence-engine/target/sequence-engine-1.0.0-SNAPSHOT-exec.jar"
SERVICE2="./web/target/web-1.0.0-SNAPSHOT.jar"

pids=$(ps -ef | grep java | grep -E "$SERVICE1|$SEVICE2" | grep -v grep | awk '{print $2}')

if [ -z "$pids" ]; then
  echo "No matching services found."
fi

echo "Found pids: $pids"
kill $pids

sleep 2

# 如果还没停，强杀
pids_left=$(ps -ef | grep java | grep -E "$SERVICE1|$SERVICE2" | grep -v grep | awk '{print $2}')
if [ -n "$pids_left" ]; then
  kill -9 $pids_left
fi

echo "Services stopped."

