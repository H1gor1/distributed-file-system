#!/bin/bash
mvn -pl fileserver-core exec:java \
  -Dexec.mainClass="br.ifmg.fileserver.core.ServerMain" \
  -Dexec.args="$1"
# uso: ./scripts/run-server.sh controle  ou  ./scripts/run-server.sh dados
