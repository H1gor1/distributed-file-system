#!/bin/bash

if [ -z "$1" ]; then
    echo "Uso: $0 <serverName> [registryHost] [registryPort]"
    echo "Exemplo: $0 ControlServer-1 localhost 1099"
    exit 1
fi

SERVER_NAME=$1
REGISTRY_HOST=${2:-localhost}
REGISTRY_PORT=${3:-1099}

cd "$(dirname "$0")/.."

echo "Iniciando $SERVER_NAME..."
echo "Registry: $REGISTRY_HOST:$REGISTRY_PORT"

java -cp fileserver-core/target/fileserver-core-1.0-SNAPSHOT.jar \
  br.ifmg.sd.control.ControlServerMain "$SERVER_NAME" "$REGISTRY_HOST" "$REGISTRY_PORT"
