#!/bin/bash

PORT=${1:-1099}

echo "Iniciando RMI Registry na porta $PORT..."
rmiregistry $PORT &

RMI_PID=$!
echo "RMI Registry rodando (PID: $RMI_PID) na porta $PORT"
echo "Para parar: kill $RMI_PID"
