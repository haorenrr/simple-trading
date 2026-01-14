#!/bin/bash


SERVICE1="./sequence-engine/target/sequence-engine-1.0.0-SNAPSHOT-exec.jar"
SERVICE2="./web/target/web-1.0.0-SNAPSHOT.jar"


echo "Starting services..."

nohup java -jar "$SERVICE1" > /dev/null 2>&1 &
echo "$SERVICE1 started, pid=$!"

nohup java -jar "$SERVICE2" > /dev/null 2>&1 &
echo "$SERVICE2 started, pid=$!"

echo "All services started."

