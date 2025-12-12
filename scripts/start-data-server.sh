#!/bin/bash

if [ -z "$1" ]; then
    echo "Uso: $0 <serverName> [registryHost] [registryPort]"
    echo "Exemplo: $0 DataServer-1 localhost 1099"
    exit 1
fi

SERVER_NAME=$1
REGISTRY_HOST=${2:-localhost}
REGISTRY_PORT=${3:-1099}

JAR_PATH="../fileserver-data/target/fileserver-data-1.0-SNAPSHOT.jar"

echo "Iniciando $SERVER_NAME..."
echo "Registry: $REGISTRY_HOST:$REGISTRY_PORT"

java -Djava.rmi.server.hostname=$REGISTRY_HOST -jar "$JAR_PATH" "$SERVER_NAME" "$REGISTRY_HOST" "$REGISTRY_PORT"
