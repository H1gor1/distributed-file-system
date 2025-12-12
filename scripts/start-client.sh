#!/bin/bash

GATEWAY_HOST=${1:-localhost}
GATEWAY_PORT=${2:-8080}

cd "$(dirname "$0")/.."

echo "════════════════════════════════════════════════════"
echo "  Distributed File System - Client"
echo "════════════════════════════════════════════════════"
echo "Gateway: http://$GATEWAY_HOST:$GATEWAY_PORT"
echo ""

java -Dgateway.host=$GATEWAY_HOST -Dgateway.port=$GATEWAY_PORT \
  -jar fileserver-client/target/fileserver-client-1.0-SNAPSHOT.jar
