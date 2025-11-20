#!/bin/bash

PORT=${1:-8080}

cd "$(dirname "$0")/.."

echo "Iniciando HTTP Gateway na porta $PORT..."

java -cp fileserver-gateway/target/fileserver-gateway-1.0-SNAPSHOT.jar \
  br.ifmg.sd.gateway.HttpGateway "$PORT"
