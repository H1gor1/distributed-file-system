#!/bin/bash

SERVER_NAME=${1:-ControlServer-1}

cd "$(dirname "$0")/.."

echo "Iniciando $SERVER_NAME..."

java -cp fileserver-core/target/fileserver-core-1.0-SNAPSHOT.jar \
  br.ifmg.sd.control.ControlServerMain "$SERVER_NAME"
