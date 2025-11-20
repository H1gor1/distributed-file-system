# Sistema de Arquivos Distribuído - Comunicação via JGroups RPC

## Arquitetura Simplificada

O sistema foi refatorado para usar **JGroups RpcDispatcher** ao invés de gRPC, simplificando drasticamente a comunicação entre componentes.

### Componentes

1. **ControlServer** - Servidor de controle (autenticação e sessões)
   - Usa JGroups para formar um cluster replicado
   - **Implementa interface `ControlService`** e expõe métodos RPC:
     - `login(username, password)` -> AuthResponse
     - `register(username, email, password)` -> AuthResponse
     - `validateToken(token)` -> boolean
     - `logout(token)` -> AuthResponse
     - `getUserIdFromToken(token)` -> String
   - Conecta ao canal `control-cluster` **como servidor RPC**

2. **Gateway** - Ponto de entrada para clientes (proxy/load balancer)
   - Conecta-se ao cluster `control-cluster` **como CLIENTE** (não implementa métodos RPC)
   - Usa RpcDispatcher para chamar métodos remotos **nos ControlServers**
   - **Duas versões disponíveis:**
     - **Gateway** (TCP): Interface de socket TCP simples (porta 9090)
     - **HttpGateway** (HTTP): API REST JSON (porta 8080) ⭐ Recomendado para testes
   - **NÃO faz parte do cluster de servidores**, apenas consome serviços

3. **GatewayClient** - Cliente de linha de comando
   - Conecta ao Gateway via socket TCP
   - Comandos interativos: register, login, logout, validate

### Fluxo de Comunicação

```
Cliente → Gateway (TCP) → RPC → ControlServer (cluster)
                                      ↓
                                 Replicação JGroups
                                      ↓
                              Outros ControlServers
```

### Protocolo do Gateway (TCP)

**Comandos:**
```
LOGIN username:password
REGISTER username:email:password
LOGOUT token
VALIDATE token
```

**Respostas:**
```
SUCCESS: <dados>
ERROR: <mensagem>
```

## Como Usar

### 1. Compilar o projeto
```bash
mvn clean package -DskipTests
```

### 2. Iniciar Servidor(es) de Controle

Terminal 1:
```bash
./scripts/start-control.sh ControlServer-1
```

Terminal 2 (opcional - cluster):
```bash
./scripts/start-control.sh ControlServer-2
```

### 3. Iniciar Gateway

Terminal 3 - Opção A (TCP):
```bash
./scripts/start-gateway.sh 9090
```

Terminal 3 - Opção B (HTTP/REST - Recomendado):
```bash
./scripts/start-http-gateway.sh 8080
```

### 4. Testar o Sistema

**Opção A - Cliente Interativo (TCP):**
```bash
./scripts/start-client.sh
```

**Opção B - curl (HTTP) - Mais fácil:**
```bash
# Registrar
curl -X POST http://localhost:8080/api/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"senha123"}'

# Login
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"senha123"}'

# Ou use o script automatizado
./scripts/test-http.sh
```

**Opção C - netcat (TCP):**
```bash
echo "REGISTER alice alice@example.com senha123" | nc localhost 9090
echo "LOGIN alice senha123" | nc localhost 9090
```

📖 Veja mais exemplos em [TESTING.md](TESTING.md)

## Exemplo de Uso

```bash
$ ./scripts/start-client.sh
=== Cliente Gateway ===
Comandos disponíveis:
  1. register <username> <email> <password>
  2. login <username> <password>
  3. logout
  4. validate
  5. exit

> register alice alice@example.com senha123
Registro bem-sucedido! Token: eyJhbGciOiJIUzI1NiJ9...

> login alice senha123
Login bem-sucedido! Token: eyJhbGciOiJIUzI1NiJ9...

> validate
SUCCESS: Token is valid

> logout
Logout bem-sucedido!

> exit
Saindo...
```

## Vantagens sobre gRPC

1. **Mais simples** - Sem arquivos .proto, sem geração de código
2. **Menos dependências** - Apenas JGroups (já usado para clustering)
3. **Protocolo direto** - Interface de texto fácil de testar
4. **Descoberta automática** - JGroups gerencia membros do cluster
5. **Failover transparente** - RPC automaticamente escolhe servidor disponível
6. **Separação clara** - Gateway é cliente, ControlServer é servidor

## Arquitetura de Rede

```
┌─────────────┐
│   Cliente   │
└──────┬──────┘
       │ TCP (porta 9090)
       │
┌──────▼──────┐
│   Gateway   │ (Cliente RPC)
└──────┬──────┘
       │ JGroups RPC
       │ (control-cluster)
       ▼
┌─────────────────────────────────┐
│  Cluster ControlServer          │
│  ┌──────────┐    ┌──────────┐  │
│  │ Control1 │◄──►│ Control2 │  │ (Servidores RPC)
│  └──────────┘    └──────────┘  │
│         ▲              ▲         │
│         └──────┬───────┘         │
│           Replicação             │
└─────────────────────────────────┘
```

O **Gateway** apenas **chama métodos** nos ControlServers, ele **não responde** a chamadas RPC.

## Estrutura de Classes

```
fileserver-common/
├── br.ifmg.sd.models/
│   ├── User.java
│   ├── Session.java
│   └── SessionUpdate.java
└── br.ifmg.sd.rpc/
    ├── ControlService.java (interface)
    ├── AuthRequest.java
    └── AuthResponse.java

fileserver-core/
└── br.ifmg.sd.control/
    ├── ControlServer.java (implements ControlService + Receiver)
    └── ControlServerMain.java

fileserver-gateway/
└── br.ifmg.sd.gateway/
    └── Gateway.java (implements Receiver)

fileserver-client/
└── br.ifmg.sd.client/
    └── GatewayClient.java
```

## Configuração JGroups

O sistema usa `udp.xml` para configuração do JGroups (multicast UDP para descoberta).

**Importante:** Para ambientes onde multicast não funciona, ajuste `udp.xml` para usar TCP ou TCPPING.
