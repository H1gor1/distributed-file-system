#!/bin/bash
mvn -pl fileserver-gateway exec:java \
  -Dexec.mainClass="br.ifmg.fileserver.gateway.GatewayMain"
