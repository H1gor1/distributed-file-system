#!/bin/bash
mvn -pl fileserver-client exec:java \
  -Dexec.mainClass="br.ifmg.fileserver.client.ClientMain"
