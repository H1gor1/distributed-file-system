# 📦 Sistema de Arquivos Distribuído

Sistema de armazenamento de arquivos distribuído com replicação automática, utilizando JGroups para comunicação cluster e RMI para comunicação entre camadas.

## 📋 Índice

- [Visão Geral](#visão-geral)
- [O Que o Sistema Faz](#o-que-o-sistema-faz)
- [Arquitetura](#arquitetura)
- [Configurações JGroups](#configurações-jgroups)
- [Como Executar](#como-executar)
- [Tecnologias](#tecnologias)

---

## 🎯 Visão Geral

Este é um sistema de arquivos distribuído que permite:
- ✅ Upload, download, atualização e deleção de arquivos
- ✅ Replicação automática em múltiplos servidores
- ✅ Autenticação de usuários com JWT
- ✅ Consistência forte com locks distribuídos
- ✅ Busca de arquivos por nome
- ✅ Recuperação automática de falhas

---

## 🚀 O Que o Sistema Faz

O sistema expõe uma API REST através do **Gateway HTTP** que permite aos clientes realizarem as seguintes operações:

### 🔐 Autenticação (`/api/...`)

| Endpoint | Método | Descrição |
|----------|--------|-----------|
| `/api/register` | POST | Registra novo usuário no sistema |
| `/api/login` | POST | Autentica usuário e retorna token JWT |
| `/api/logout` | POST | Invalida sessão do usuário |
| `/api/validate` | POST | Valida se um token JWT é válido |

**Funcionalidade**: Gerencia autenticação e sessões de usuários. Os tokens são replicados entre os servidores de controle usando JGroups para garantir que qualquer servidor possa validar uma sessão.

---

### 📁 Operações de Arquivos (`/api/files/...`)

| Endpoint | Método | Descrição |
|----------|--------|-----------|
| `/api/files/upload` | POST | Faz upload de um arquivo |
| `/api/files/download` | GET | Baixa um arquivo (com lock distribuído) |
| `/api/files/update` | POST | Atualiza conteúdo de um arquivo existente |
| `/api/files/delete` | POST | Remove um arquivo do sistema |
| `/api/files/list` | GET | Lista todos os arquivos do usuário |
| `/api/files/search` | GET | Busca arquivos por nome (todos os usuários) |

**Funcionalidades Especiais**:

- **Upload**: Arquivo é salvo no coordenador do cluster de dados e automaticamente replicado para todos os outros DataServers
- **Download**: Usa lock distribuído JGroups para garantir que ninguém está editando o arquivo
- **Update**: Mantém o UUID original, adquire lock, aguarda replicação em TODOS os servidores antes de confirmar sucesso
- **Delete**: Pede confirmação e replica a deleção para todo o cluster
- **Search**: Retorna metadados completos (criador, datas, tamanho) de todos os arquivos com aquele nome
- **List**: Lista apenas os arquivos do usuário autenticado

---

## 🏗️ Arquitetura

O sistema é composto por **3 camadas distribuídas**:

```
┌─────────────────────────────────────────────────┐
│              Cliente HTTP                        │
│       (Interface CLI ou Web)                     │
└──────────────────┬──────────────────────────────┘
                   │ HTTP REST API
                   ▼
┌─────────────────────────────────────────────────┐
│           Gateway HTTP (HttpGateway)             │
│  • Porta 8080                                    │
│  • Recebe requisições HTTP dos clientes          │
│  • Conecta ao control-cluster via JGroups        │
│  • Faz RPC para ControlServers                   │
└──────────────────┬──────────────────────────────┘
                   │ RPC via JGroups (control-cluster)
                   ▼
┌─────────────────────────────────────────────────┐
│        Control Cluster (ControlServer)           │
│  • Gerencia autenticação e sessões               │
│  • Cluster JGroups: "control-cluster"            │
│  • Cache replicado de sessões JWT                │
│  • Comunicação via RPC com DataServers           │
└──────────────────┬──────────────────────────────┘
                   │ RMI Registry Lookup
                   ▼
┌─────────────────────────────────────────────────┐
│         RMI Registry (NameServer)                │
│  • localhost:1099                                │
│  • Serviço: "data-service" → DataServer          │
└──────────────────┬──────────────────────────────┘
                   │ RMI Remote Call
                   ▼
┌─────────────────────────────────────────────────┐
│          Data Cluster (DataServer)               │
│  • Armazena arquivos com replicação              │
│  • Cluster JGroups: "data-cluster"               │
│  • SQLite local + File System                    │
│  • Coordenador registra-se no RMI Registry       │
│  • Lock distribuído para edições/downloads       │
└─────────────────────────────────────────────────┘
```

### Comunicação Entre Camadas

1. **Cliente → Gateway**: HTTP REST
2. **Gateway → ControlServer**: RPC via JGroups (RpcDispatcher)
3. **ControlServer → DataServer**: RMI (Remote Method Invocation)
4. **Dentro de cada cluster**: Mensagens JGroups (replicação, state transfer, locks)

---

## ⚙️ Configurações JGroups

O sistema utiliza **3 arquivos XML** de configuração do JGroups, cada um otimizado para seu propósito específico.

### 📄 1. `fileserver-core/src/main/resources/udp.xml`

**Usado por**: ControlServer (cluster de controle)

**Propósito**: Gerenciar o cluster de servidores que controlam autenticação e sessões.

```xml
<config xmlns="urn:org:jgroups">
    <UDP />
    <PING />
    <MERGE3 max_interval="30000" min_interval="10000"/>
    <FD_SOCK />
    <FD_ALL timeout="60000" interval="15000" />
    <VERIFY_SUSPECT timeout="5000" />
    <pbcast.NAKACK2 use_mcast_xmit="true" />
    <UNICAST3 />
    <pbcast.STABLE desired_avg_gossip="50000" max_bytes="4M" />
    <pbcast.GMS print_local_addr="true" join_timeout="3000" />
    <pbcast.STATE_TRANSFER />
    <UFC max_credits="2M" min_threshold="0.4" />
    <MFC max_credits="2M" min_threshold="0.4" />
    <FRAG2 frag_size="60K" />
</config>
```

#### 📝 Explicação dos Protocolos:

| Protocolo | Configuração | Por Que É Necessário |
|-----------|--------------|----------------------|
| **UDP** | - | Protocolo de transporte base. Usa multicast para descoberta e comunicação eficiente entre membros do cluster. |
| **PING** | - | Descobre novos membros na rede via multicast. Permite que novos ControlServers encontrem o cluster automaticamente. |
| **MERGE3** | `max_interval="30000"`<br>`min_interval="10000"` | Detecta e resolve partições de rede (split-brain). Se dois subclusters se formarem, este protocolo os reúne. Tenta merge a cada 10-30 segundos. |
| **FD_SOCK** | - | Detecta falhas via socket TCP. Cada membro mantém conexão TCP com vizinhos para detectar crashes rapidamente. |
| **FD_ALL** | `timeout="60000"`<br>`interval="15000"` | Failure detection baseado em heartbeat. Envia pings a cada 15s, considera membro morto após 60s sem resposta. |
| **VERIFY_SUSPECT** | `timeout="5000"` | Verifica suspeitas de falha antes de remover membro. Evita falsos positivos (ex: lag temporário de rede). Dá 5s para membro responder. |
| **NAKACK2** | `use_mcast_xmit="true"` | Garante entrega confiável e ordenada de mensagens via multicast. Retransmite mensagens perdidas (negative acknowledgment). Crítico para replicação de sessões. |
| **UNICAST3** | - | Comunicação unicast confiável (ponto-a-ponto). Usado para mensagens diretas entre dois membros específicos. |
| **STABLE** | `desired_avg_gossip="50000"`<br>`max_bytes="4M"` | Garbage collection de mensagens antigas. Remove mensagens já recebidas por todos após ~50s ou 4MB acumulados. Evita memory leak. |
| **GMS** | `print_local_addr="true"`<br>`join_timeout="3000"` | Group Membership Service. Gerencia entrada/saída de membros, elege coordenador, mantém view do cluster. Timeout de 3s para join. |
| **STATE_TRANSFER** | - | Transfere estado (cache de sessões) para novos membros. Quando novo ControlServer entra, recebe todas as sessões ativas dos existentes. |
| **UFC** | `max_credits="2M"`<br>`min_threshold="0.4"` | Unicast Flow Control. Previne que remetente sobrecarregue receptor. Bloqueia após 2MB não-confirmados, libera quando atinge 40% (800KB). |
| **MFC** | `max_credits="2M"`<br>`min_threshold="0.4"` | Multicast Flow Control. Mesmo que UFC mas para mensagens multicast. Protege contra inundação de mensagens broadcast. |
| **FRAG2** | `frag_size="60K"` | Fragmenta mensagens grandes em pedaços de 60KB. UDP tem limite de ~64KB, este protocolo permite enviar objetos maiores (ex: arquivos). |

**Por Que Este Stack Para ControlServer?**

- ✅ **Replicação de sessões**: NAKACK2 garante que todas as sessões JWT sejam replicadas
- ✅ **Recuperação de estado**: STATE_TRANSFER permite que novos servidores recebam cache completo
- ✅ **Alta disponibilidade**: FD_SOCK + FD_ALL + VERIFY_SUSPECT detectam falhas rapidamente
- ✅ **Resiliência a partições**: MERGE3 reúne clusters separados
- ✅ **Controle de fluxo**: UFC/MFC evitam sobrecarga de rede
- ❌ **Sem CENTRAL_LOCK**: Sessões são read-heavy, não precisam de locking

---

### 📄 2. `fileserver-gateway/src/main/resources/udp.xml`

**Usado por**: HttpGateway (gateway HTTP)

**Propósito**: Conectar ao cluster de controle para fazer RPC calls aos ControlServers.

```xml
<config xmlns="urn:org:jgroups">
    <UDP />
    <PING />
    <MERGE3 max_interval="30000" min_interval="10000"/>
    <FD_SOCK />
    <FD_ALL timeout="60000" interval="15000" />
    <VERIFY_SUSPECT timeout="5000" />
    <pbcast.NAKACK2 use_mcast_xmit="true" />
    <UNICAST3 />
    <pbcast.STABLE desired_avg_gossip="50000" max_bytes="4M" />
    <pbcast.GMS print_local_addr="true" join_timeout="3000" />
    <pbcast.STATE_TRANSFER />
    <UFC max_credits="2M" min_threshold="0.4" />
    <MFC max_credits="2M" min_threshold="0.4" />
    <FRAG2 frag_size="60K" />
</config>
```

#### 📝 Por Que É Idêntico ao ControlServer?

**Resposta**: O Gateway **participa do mesmo cluster** (`control-cluster`) mas com papel diferente:

| Aspecto | Gateway | ControlServer |
|---------|---------|---------------|
| **Cluster** | `control-cluster` | `control-cluster` |
| **Recebe estado?** | ❌ Não | ✅ Sim (cache de sessões) |
| **Envia mensagens?** | ❌ Não | ✅ Sim (replicação) |
| **Faz RPC?** | ✅ Sim (chama métodos) | ✅ Sim (responde métodos) |
| **View do cluster** | ✅ Sim (vê todos membros) | ✅ Sim (vê todos membros) |

**Funcionalidade do Gateway**:

```java
// Gateway obtém lista de ControlServers disponíveis
List<Address> getControlServers() {
    return channel.getView().getMembers()
        .stream()
        .filter(addr -> !addr.equals(channel.getAddress()))
        .collect(Collectors.toList());
}

// E faz RPC call balanceado
dispatcher.callRemoteMethod(
    randomControlServer,  // Address obtido da view
    "login",              // Método remoto
    new Object[]{username, password},
    ...
);
```

**Por Que Precisa do Stack Completo?**

- ✅ **Descoberta dinâmica**: PING encontra ControlServers disponíveis
- ✅ **Detecção de falhas**: FD_SOCK/FD_ALL sabe quando ControlServer cai
- ✅ **View atualizada**: GMS mantém lista de servidores vivos para load balancing
- ✅ **RPC confiável**: NAKACK2 + UNICAST3 garantem que chamadas chegam
- ❌ **Não precisa STATE_TRANSFER**: Gateway não armazena estado persistente

---

### 📄 3. `fileserver-data/src/main/resources/udp-data.xml`

**Usado por**: DataServer (cluster de dados)

**Propósito**: Gerenciar cluster de armazenamento com replicação de arquivos e locks distribuídos.

```xml
<config xmlns="urn:org:jgroups">
    <UDP />
    <PING />
    <MERGE3 max_interval="30000" min_interval="10000"/>
    <FD_SOCK />
    <FD_ALL timeout="60000" interval="15000" />
    <VERIFY_SUSPECT timeout="5000" />
    <pbcast.NAKACK2 use_mcast_xmit="true" />
    <UNICAST3 />
    <pbcast.STABLE desired_avg_gossip="50000" max_bytes="4M" />
    <pbcast.GMS print_local_addr="true" join_timeout="3000" />
    <CENTRAL_LOCK num_backups="1" />
    <pbcast.STATE_TRANSFER />
    <UFC max_credits="2M" min_threshold="0.4" />
    <MFC max_credits="2M" min_threshold="0.4" />
    <FRAG2 frag_size="60K" />
</config>
```

#### 📝 Diferenças do Control Cluster:

| Protocolo | Diferença | Por Quê |
|-----------|-----------|---------|
| **CENTRAL_LOCK** | ✅ **PRESENTE** (único que tem) | DataServers precisam de **locks distribuídos** para edição e download de arquivos. Previne condições de corrida. |
| `num_backups="1"` | Mantém 1 backup do lock | Se coordenador de lock cai, backup assume. Garante que lock não é perdido. |

#### 🔒 Como CENTRAL_LOCK é Usado:

```java
// No DataServer.editFile()
Lock lock = lockService.getLock(userId + ":" + fileName);
lock.lock();  // Adquire lock distribuído
try {
    // 1. Atualiza arquivo localmente
    fileRepository.editFile(userId, fileName, newContent);
    
    // 2. Replica para todos os DataServers
    Message msg = new ObjectMessage(null, replication);
    channel.send(msg);
    
    // 3. Aguarda ACKs de todos
    replicationCoordinator.waitForCompletion(opId, 10, TimeUnit.SECONDS);
    
} finally {
    lock.unlock();  // Libera lock
}
```

**Cenário de Corrida Prevenido**:

```
Sem Lock:
  T0: User A inicia download de arquivo.txt
  T1: User B inicia edição de arquivo.txt
  T2: Download do A recebe versão antiga
  T3: Edição do B completa
  ❌ Problema: A baixou versão inconsistente

Com Lock:
  T0: User A inicia download → lock.lock()
  T1: User B tenta editar → lock.lock() bloqueia
  T2: Download do A completa → lock.unlock()
  T3: Lock do B é adquirido, edição prossegue
  ✅ Consistência: A baixou versão estável
```

#### 📝 Explicação Completa dos Protocolos DataServer:

| Protocolo | Por Que É Necessário No Data Cluster |
|-----------|--------------------------------------|
| **UDP** | Comunicação rápida para replicação de arquivos entre DataServers |
| **PING** | Descobre novos DataServers que entram no cluster |
| **MERGE3** | Resolve partições (ex: DataServer-1 e DataServer-2 perderam contato e se reunem) |
| **FD_SOCK + FD_ALL** | Detecta quando DataServer cai para acionar re-replicação |
| **VERIFY_SUSPECT** | Evita remover DataServer por lag de rede temporário |
| **NAKACK2** | **Crítico**: Garante que replicação de arquivos não perde mensagens |
| **UNICAST3** | Usado para ACKs de replicação (ponto-a-ponto) |
| **STABLE** | Limpa mensagens antigas de replicação para economizar memória |
| **GMS** | Elege coordenador (que registra no RMI), gerencia membership |
| **CENTRAL_LOCK** | **Exclusivo do DataServer**: Previne edições concorrentes e leitura de arquivo sendo editado |
| **STATE_TRANSFER** | Transfere banco SQLite e arquivos para novo DataServer que entra no cluster |
| **UFC/MFC** | Previne sobrecarga ao replicar arquivos grandes (ex: vídeos de 100MB) |
| **FRAG2** | Permite replicar arquivos maiores que 64KB (limite do UDP) |

**Por Que Este Stack Para DataServer?**

- ✅ **Replicação confiável**: NAKACK2 + ACKs customizados garantem 100% de replicação
- ✅ **Locks distribuídos**: CENTRAL_LOCK previne corrupção de dados
- ✅ **Recuperação de estado**: Novo DataServer recebe todos os arquivos via STATE_TRANSFER
- ✅ **Failover de coordenador**: Se coordenador RMI cai, GMS elege novo e re-registra
- ✅ **Fragmentação**: FRAG2 permite replicar arquivos grandes

---

## 📊 Comparação dos 3 Arquivos XML

| Característica | Control (udp.xml) | Gateway (udp.xml) | Data (udp-data.xml) |
|----------------|-------------------|-------------------|---------------------|
| **Cluster** | `control-cluster` | `control-cluster` | `data-cluster` |
| **CENTRAL_LOCK** | ❌ Não | ❌ Não | ✅ **Sim** |
| **STATE_TRANSFER** | ✅ Cache de sessões | ❌ Não usa | ✅ Arquivos + DB |
| **Papel** | Gerencia sessões | Faz RPC calls | Armazena arquivos |
| **Replicação** | Sessões JWT | - | Arquivos binários |
| **Tamanho mensagens** | Pequeno (~1KB) | Pequeno | Grande (até MBs) |
| **FRAG2 crítico?** | Não | Não | ✅ **Sim** (arquivos grandes) |

---

## 🔄 Fluxo de Dados Completo

### Exemplo: Upload de Arquivo

```
1. Cliente HTTP
   POST /api/files/upload
   Body: { token: "jwt...", fileName: "doc.pdf", content: [bytes] }
   ↓

2. HttpGateway
   • Recebe requisição HTTP
   • Obtém lista de ControlServers via channel.getView()
   • Faz RPC call via RpcDispatcher:
     clusterClient.uploadFile(token, fileName, content)
   ↓

3. ControlServer (aleatório do cluster)
   • Valida token no cache replicado
   • Extrai userId do token
   • Faz lookup do DataService no RMI Registry:
     dataService = registry.lookup("data-service")
   • Chama método RMI:
     dataService.saveFile(userId, fileName, content)
   ↓

4. DataServer Coordenador
   • Recebe chamada RMI
   • Salva arquivo no SQLite + File System
   • Cria mensagem de replicação: FileReplication(userId, fileName, content, SAVE)
   • Envia broadcast JGroups:
     channel.send(new ObjectMessage(null, replication))
   • Aguarda ACKs de todos os DataServers (timeout 10s)
   ↓

5. DataServers Réplicas
   • Recebem FileReplication via JGroups Receiver
   • Salvam arquivo no SQLite + File System local
   • Enviam ACK unicast de volta ao coordenador:
     channel.send(new ObjectMessage(coordenadorAddress, ack))
   ↓

6. Coordenador
   • Recebe todos os ACKs
   • Retorna true via RMI → ControlServer → Gateway → Cliente
   • Cliente recebe: { "success": true, "message": "File uploaded" }
```

### Exemplo: Download com Lock Distribuído

```
1. Cliente HTTP
   GET /api/files/download?fileName=doc.pdf
   Header: Authorization: Bearer jwt...
   ↓

2. Gateway → ControlServer (via RPC)
   downloadFile(token, fileName)
   ↓

3. ControlServer → DataServer (via RMI)
   dataService.downloadFile(userId, fileName)
   ↓

4. DataServer Coordenador
   Lock lock = lockService.getLock(userId + ":" + fileName);
   lock.lock();  // ← Adquire lock distribuído JGroups
   try {
       byte[] content = fileRepository.read(userId, fileName);
       return content;  // RMI retorna bytes
   } finally {
       lock.unlock();  // ← Libera lock
   }
   ↓

5. Retorno inverso
   DataServer → ControlServer → Gateway → Cliente
   • Cliente recebe bytes do arquivo
   • Durante todo o processo, nenhum outro cliente pode editar o arquivo
```

---

## 🚀 Como Executar

### Pré-requisitos

- Java 17+
- Maven 3.6+

### 1. Build do Projeto

```bash
mvn clean package
```

### 2. Iniciar DataServers (ordem importante)

```bash
# Terminal 1 - DataServer-1 (cria RMI Registry e vira coordenador)
cd scripts
./start-data-server.sh DataServer-1 localhost 1099

# Terminal 2 - DataServer-2
./start-data-server.sh DataServer-2 localhost 1099

# Terminal 3 - DataServer-3
./start-data-server.sh DataServer-3 localhost 1099
```

### 3. Iniciar ControlServers

```bash
# Terminal 4 - ControlServer-1
cd scripts
./start-control.sh ControlServer-1 localhost 1099

# Terminal 5 - ControlServer-2
./start-control.sh ControlServer-2 localhost 1099
```

### 4. Iniciar Gateway

```bash
# Terminal 6
cd scripts
./start-gateway.sh
```

**Gateway estará disponível em**: `http://localhost:8080`

### 5. Iniciar Cliente (opcional)

```bash
# Terminal 7
cd scripts
./start-client.sh
```

---

## 🧪 Testando o Sistema

### Via Cliente CLI

```bash
# Registrar usuário
register joao joao@email.com senha123

# Upload
upload /caminho/para/arquivo.pdf

# Listar
list

# Buscar
search arquivo.pdf

# Download
download arquivo.pdf

# Atualizar
update arquivo.pdf /caminho/para/arquivo-v2.pdf

# Deletar
delete arquivo.pdf
```

### Via cURL

```bash
# Registrar
curl -X POST http://localhost:8080/api/register \
  -H "Content-Type: application/json" \
  -d '{"username":"joao","email":"joao@email.com","password":"senha123"}'

# Login
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"joao@email.com","password":"senha123"}'

# Upload (exemplo simplificado)
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer SEU_TOKEN" \
  -F "file=@arquivo.pdf"
```

---

## 🛡️ Garantias do Sistema

### Consistência

- ✅ **Replicação síncrona**: Upload/Update só retorna sucesso após 100% dos DataServers confirmarem
- ✅ **Locks distribuídos**: Edit e Download usam CENTRAL_LOCK para prevenir condições de corrida
- ✅ **ACKs obrigatórios**: Sistema aguarda confirmação de todas as réplicas antes de confirmar operação

### Disponibilidade

- ✅ **Failover automático**: Se coordenador cai, próximo na view assume (GMS)
- ✅ **Descoberta dinâmica**: Novos servidores são descobertos automaticamente (PING)
- ✅ **Detecção de falhas**: FD_SOCK + FD_ALL detectam crashes em ~60s
- ✅ **Merge de partições**: MERGE3 reúne subclusters separados

### Durabilidade

- ✅ **Persistência local**: Cada DataServer mantém SQLite + File System
- ✅ **Replicação N-vias**: Arquivos existem em todos os DataServers
- ✅ **State Transfer**: Novos servidores recebem estado completo ao entrar

---

## 🛠️ Tecnologias

| Tecnologia | Versão | Uso |
|------------|--------|-----|
| **Java** | 17 | Linguagem principal |
| **JGroups** | 5.3.4 | Comunicação cluster, locks distribuídos |
| **RMI** | Java Native | Comunicação Control → Data |
| **SQLite** | JDBC | Persistência de metadata |
| **JWT** | jjwt 0.12.5 | Autenticação stateless |
| **Maven** | 3.6+ | Build e dependências |

---

## 📁 Estrutura do Projeto

```
distributed-file-system/
├── fileserver-gateway/          # Gateway HTTP (porta 8080)
│   └── src/main/resources/
│       └── udp.xml              # Config JGroups para control-cluster
├── fileserver-core/             # ControlServer (autenticação)
│   └── src/main/resources/
│       └── udp.xml              # Config JGroups para control-cluster
├── fileserver-data/             # DataServer (armazenamento)
│   └── src/main/resources/
│       └── udp-data.xml         # Config JGroups para data-cluster
├── fileserver-client/           # Cliente CLI
├── fileserver-common/           # Modelos e interfaces compartilhadas
├── scripts/                     # Scripts de inicialização
├── DataServer-1/                # Dados do DataServer-1
├── DataServer-2/                # Dados do DataServer-2
├── DataServer-3/                # Dados do DataServer-3
└── downloads/                   # Arquivos baixados pelo cliente
```

---

## 📚 Documentação Adicional

- [Arquitetura RMI](README-RMI-ARCHITECTURE.md) - Detalhes sobre RMI Registry e comunicação entre camadas
- [RPC com JGroups](README-RPC.md) - Como funciona RpcDispatcher
- [Funcionalidades](FUNCIONALIDADES.md) - Lista completa de features implementadas
- [Quick Start](QUICKSTART.md) - Guia rápido de uso

---

## 🤝 Contribuindo

1. Fork o projeto
2. Crie uma branch (`git checkout -b feature/nova-feature`)
3. Commit suas mudanças (`git commit -am 'Adiciona nova feature'`)
4. Push para a branch (`git push origin feature/nova-feature`)
5. Abra um Pull Request

---

## 📝 Licença

Este projeto é distribuído sob a licença MIT. Veja `LICENSE` para mais informações.

---

## 👥 Autores

- **Higor Lino** - Desenvolvimento inicial

---

## 🎓 Contexto Acadêmico

Este sistema foi desenvolvido como projeto da disciplina de Sistemas Distribuídos do IFMG, demonstrando conceitos de:

- Comunicação em cluster (JGroups)
- Replicação de dados
- Locks distribuídos
- RPC e RMI
- Tolerância a falhas
- Consistência forte
- State transfer
- Failure detection
