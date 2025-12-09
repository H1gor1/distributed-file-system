#!/bin/bash

# Script para iniciar todos os componentes do sistema distribuído
# 1 cliente, 1 gateway, 2 fileserver-core e 3 fileserver-data

cd "$(dirname "$0")"

# Detectar terminal disponível
TERMINAL=""
if command -v gnome-terminal &> /dev/null; then
    TERMINAL="gnome-terminal"
elif command -v konsole &> /dev/null; then
    TERMINAL="konsole"
elif command -v xfce4-terminal &> /dev/null; then
    TERMINAL="xfce4-terminal"
elif command -v xterm &> /dev/null; then
    TERMINAL="xterm"
else
    echo "❌ Nenhum emulador de terminal encontrado!"
    echo "Instale: gnome-terminal, konsole, xfce4-terminal ou xterm"
    exit 1
fi

echo "🚀 Iniciando sistema distribuído..."
echo "📟 Terminal detectado: $TERMINAL"
echo ""

# Função para abrir terminal com comando
open_terminal() {
    local title=$1
    local command=$2
    
    case $TERMINAL in
        gnome-terminal)
            gnome-terminal --title="$title" -- bash -c "$command; exec bash" &
            ;;
        xterm)
            xterm -title "$title" -e bash -c "$command; exec bash" &
            ;;
        konsole)
            konsole --new-tab -p tabtitle="$title" -e bash -c "$command; exec bash" &
            ;;
        xfce4-terminal)
            xfce4-terminal --title="$title" -e "bash -c '$command; exec bash'" &
            ;;
    esac
    
    sleep 0.5
}

# 1. Iniciar 3 Data Servers
echo "📦 Iniciando Data Servers..."
open_terminal "DataServer-1" "cd '$(pwd)' && java -cp fileserver-data/target/fileserver-data-1.0-SNAPSHOT.jar br.ifmg.sd.data.DataServerMain DataServer-1 localhost 1099"
sleep 2
open_terminal "DataServer-2" "cd '$(pwd)' && java -cp fileserver-data/target/fileserver-data-1.0-SNAPSHOT.jar br.ifmg.sd.data.DataServerMain DataServer-2 localhost 1099"
sleep 1
open_terminal "DataServer-3" "cd '$(pwd)' && java -cp fileserver-data/target/fileserver-data-1.0-SNAPSHOT.jar br.ifmg.sd.data.DataServerMain DataServer-3 localhost 1099"
sleep 2

# 2. Iniciar 2 Control Servers (fileserver-core)
echo "🎮 Iniciando Control Servers..."
open_terminal "ControlServer-1" "cd '$(pwd)' && java -cp fileserver-core/target/fileserver-core-1.0-SNAPSHOT.jar br.ifmg.sd.control.ControlServerMain ControlServer-1"
sleep 1
open_terminal "ControlServer-2" "cd '$(pwd)' && java -cp fileserver-core/target/fileserver-core-1.0-SNAPSHOT.jar br.ifmg.sd.control.ControlServerMain ControlServer-2"
sleep 2

# 3. Iniciar Gateway
echo "🌐 Iniciando Gateway..."
open_terminal "Gateway" "cd '$(pwd)' && mvn -pl fileserver-gateway compile exec:java -Dexec.mainClass='br.ifmg.sd.gateway.core.HttpGateway'"
sleep 3

# 4. Iniciar Cliente
echo "💻 Iniciando Cliente..."
open_terminal "Client" "cd '$(pwd)' && mvn -pl fileserver-client compile exec:java -Dexec.mainClass='br.ifmg.fileserver.client.ClientMain'"

echo ""
echo "✅ Todos os componentes foram iniciados!"
echo ""
echo "📋 Componentes:"
echo "  • 3x Data Servers (DataServer-1, DataServer-2, DataServer-3)"
echo "  • 2x Control Servers (ControlServer-1, ControlServer-2)"
echo "  • 1x Gateway"
echo "  • 1x Client"
echo ""
echo "💡 Para encerrar todos os processos:"
echo "   pkill -f 'DataServerMain|ControlServerMain|fileserver-gateway|fileserver-client'"
