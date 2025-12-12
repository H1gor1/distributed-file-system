package br.ifmg.sd.data;

import br.ifmg.sd.data.repository.FileRepository;
import br.ifmg.sd.data.repository.UserRepository;
import br.ifmg.sd.models.File;
import br.ifmg.sd.models.FileReplication;
import br.ifmg.sd.models.ReplicationAck;
import br.ifmg.sd.models.UserReplication;
import br.ifmg.sd.rpc.DataService;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.jgroups.*;
import org.jgroups.blocks.locking.LockService;
import org.jgroups.util.Util;

public class DataServer
    extends UnicastRemoteObject
    implements DataService, Receiver {

    private final String serverName;
    private JChannel channel;
    private Connection dbConnection;
    private final String registryHost;
    private final int registryPort;
    private FileRepository fileRepository;
    private UserRepository userRepository;
    private ReplicationCoordinator replicationCoordinator;
    private LockService lockService;

    public DataServer(String serverName, String registryHost, int registryPort)
        throws RemoteException {
        super();
        this.serverName = serverName;
        this.registryHost = registryHost;
        this.registryPort = registryPort;
        this.replicationCoordinator = new ReplicationCoordinator();
    }

    public void start() throws Exception {
        System.out.println("Iniciando " + serverName);

        initDatabase();

        InputStream configStream = getClass()
            .getClassLoader()
            .getResourceAsStream("udp-data.xml");

        if (configStream == null) {
            throw new RuntimeException("Arquivo udp-data.xml não encontrado!");
        }

        channel = new JChannel(configStream);
        channel.setReceiver(this);
        channel.connect("data-cluster");
        
        lockService = new LockService(channel);
        
        System.out.println(serverName + " conectado ao data-cluster");
        System.out.println("Endereço: " + channel.getAddress());
        System.out.println("Coordenador? " + isCoordinator());
        
        if (!isCoordinator()) {
            System.out.println("Solicitando estado do coordenador...");
            try {
                channel.getState(null, 30000);
                System.out.println("Estado recebido com sucesso");
            } catch (org.jgroups.StateTransferException e) {
                System.out.println("Aviso: Não foi possível receber estado (timeout ou sem dados)");
                System.out.println("Servidor iniciará com estado vazio");
            }
        } else {
            System.out.println("Coordenador não precisa receber estado");
        }

        if (isCoordinator()) {
            registerInRMI();
        }
    }

    private void initDatabase() throws SQLException {
        java.io.File dir = new java.io.File(serverName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String dbPath = serverName + "/" + "fileserver_" + serverName + ".db";
        dbConnection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        String createFilesTable = """
                CREATE TABLE IF NOT EXISTS files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    user_name TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    disk_path TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    file_size INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(user_id, file_name)
                )
            """;

        String createUsersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    password TEXT NOT NULL,
                    email TEXT NOT NULL UNIQUE,
                    created_at INTEGER NOT NULL
                )
            """;

        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute(createFilesTable);
            stmt.execute(createUsersTable);
            System.out.println("Tabelas criadas/verificadas com sucesso");
        }

        try {
            fileRepository = new FileRepository(dbConnection, serverName);
        } catch (Exception e) {
            throw new SQLException("Erro ao inicializar FileRepository", e);
        }

        userRepository = new UserRepository(dbConnection);

        System.out.println("Banco de dados inicializado: " + dbPath);
    }

    private boolean isCoordinator() {
        return channel.getView().getCoord().equals(channel.getAddress());
    }

    private void registerInRMI() {
        try {
            Registry registry;

            try {
                System.out.println(
                    "Tentando criar RMI Registry na porta " +
                        registryPort +
                        "..."
                );
                registry = LocateRegistry.createRegistry(registryPort);
                System.out.println(
                    "RMI Registry criado na porta " + registryPort
                );
            } catch (java.rmi.server.ExportException e) {
                System.out.println("RMI Registry já existe, conectando...");
                registry = LocateRegistry.getRegistry(
                    registryHost,
                    registryPort
                );
                System.out.println("✓ Conectado ao RMI Registry existente");
            }

            registry.rebind("data-service", this);
            System.out.println(
                "DataService registrado no RMI Registry como 'data-service'"
            );
        } catch (Exception e) {
            System.err.println(
                "Erro ao registrar no RMI Registry: " + e.getMessage()
            );
            e.printStackTrace();
        }
    }

    @Override
    public void viewAccepted(View newView) {
        System.out.println("Nova view do data-cluster: " + newView);

        if (isCoordinator()) {
            System.out.println("Este servidor é o novo coordenador!");
            registerInRMI();
        }
    }

    // ==================== RMI Methods ====================

    @Override
    public boolean saveFile(String userId, String fileName, byte[] content)
        throws RemoteException {
        System.out.println(
            "RMI: Salvando arquivo " + fileName + " para usuário " + userId
        );

        try {
            String diskPath = fileRepository.save(userId, fileName, content);
            br.ifmg.sd.models.FileMetadata metadata = fileRepository.getMetadata(userId, fileName);

            FileReplication replication = new FileReplication(
                userId,
                metadata.getUserName(),
                fileName,
                content,
                FileReplication.OperationType.SAVE,
                diskPath,
                metadata.getCreatedAt(),
                metadata.getUpdatedAt()
            );

            int clusterSize = channel.getView().size();
            int expectedAcks = clusterSize - 1;
            
            if (expectedAcks > 0) {
                replicationCoordinator.startOperation(
                    replication.getOperationId(),
                    expectedAcks
                );
            }

            Message msg = new ObjectMessage(null, replication);
            channel.send(msg);

            if (expectedAcks > 0) {
                boolean success = replicationCoordinator.waitForCompletion(
                    replication.getOperationId(),
                    10,
                    TimeUnit.SECONDS
                );

                if (!success) {
                    System.err.println(
                        "Aviso: Nem todas as réplicas confirmaram a operação"
                    );
                }
            }

            System.out.println("Arquivo salvo e replicado com sucesso");
            return true;
        } catch (Exception e) {
            System.err.println("Erro ao salvar arquivo: " + e.getMessage());
            throw new RemoteException("Erro ao salvar arquivo", e);
        }
    }

    @Override
    public byte[] readFile(String userId, String fileName)
        throws RemoteException {
        System.out.println(
            "RMI: Lendo arquivo " + fileName + " do usuário " + userId
        );

        try {
            return fileRepository.findByUserIdAndFileName(userId, fileName);
        } catch (Exception e) {
            throw new RemoteException("Erro ao ler arquivo", e);
        }
    }

    @Override
    public byte[] recoverFile(String userId, String fileName)
        throws RemoteException {
        System.out.println(
            "RMI: Recuperando arquivo " + fileName + " do usuário " + userId
        );

        try {
            byte[] content = fileRepository.findByUserIdAndFileName(userId, fileName);
            
            if (content == null) {
                throw new RemoteException(
                    "Arquivo não encontrado: " + fileName + " (usuário: " + userId + ")"
                );
            }

            System.out.println(
                "Arquivo recuperado com sucesso: " + 
                fileName + 
                " (" + content.length + " bytes)"
            );
            
            return content;
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            System.err.println(
                "Erro ao recuperar arquivo " + fileName + ": " + e.getMessage()
            );
            throw new RemoteException("Erro ao recuperar arquivo", e);
        }
    }

    @Override
    public boolean deleteFile(String userId, String fileName)
        throws RemoteException {
        System.out.println(
            "RMI: Deletando arquivo " + fileName + " do usuário " + userId
        );

        try {
            fileRepository.delete(userId, fileName);

            FileReplication replication = new FileReplication(
                userId,
                "Unknown",
                fileName,
                null,
                FileReplication.OperationType.DELETE,
                null,
                0,
                0
            );

            int clusterSize = channel.getView().size();
            int expectedAcks = clusterSize - 1;
            
            if (expectedAcks > 0) {
                replicationCoordinator.startOperation(
                    replication.getOperationId(),
                    expectedAcks
                );
            }

            Message msg = new ObjectMessage(null, replication);
            channel.send(msg);

            if (expectedAcks > 0) {
                boolean success = replicationCoordinator.waitForCompletion(
                    replication.getOperationId(),
                    10,
                    TimeUnit.SECONDS
                );

                if (!success) {
                    System.err.println(
                        "Aviso: Nem todas as réplicas confirmaram a deleção"
                    );
                }
            }

            return true;
        } catch (Exception e) {
            throw new RemoteException("Erro ao deletar arquivo", e);
        }
    }

    @Override
    public boolean editFile(String userId, String fileName, byte[] newContent)
        throws RemoteException {
        System.out.println(
            "RMI: Editando arquivo " + fileName + " do usuário " + userId
        );

        try {
            Lock lock = lockService.getLock(userId + ":" + fileName);
            lock.lock();
            
            try {
                String diskPath = fileRepository.getDiskPath(userId, fileName);
                if (diskPath == null) {
                    throw new RemoteException("Arquivo não encontrado");
                }

                fileRepository.editFile(userId, fileName, newContent);
                br.ifmg.sd.models.FileMetadata metadata = fileRepository.getMetadata(userId, fileName);

                FileReplication replication = new FileReplication(
                    userId,
                    metadata.getUserName(),
                    fileName,
                    newContent,
                    FileReplication.OperationType.EDIT,
                    diskPath,
                    metadata.getCreatedAt(),
                    metadata.getUpdatedAt()
                );

            int clusterSize = channel.getView().size();
            int expectedAcks = clusterSize - 1;
            
            if (expectedAcks > 0) {
                replicationCoordinator.startOperation(
                    replication.getOperationId(),
                    expectedAcks
                );
            }

            Message msg = new ObjectMessage(null, replication);
            channel.send(msg);

            if (expectedAcks > 0) {
                boolean success = replicationCoordinator.waitForCompletion(
                    replication.getOperationId(),
                    10,
                    TimeUnit.SECONDS
                );

                if (!success) {
                    throw new RemoteException(
                        "Falha na replicação: nem todas as réplicas confirmaram a edição"
                    );
                }
            }

                System.out.println("Arquivo editado e replicado com sucesso");
                return true;
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            throw new RemoteException("Erro ao editar arquivo", e);
        }
    }

    @Override
    public List<String> listFiles(String userId) throws RemoteException {
        try {
            return fileRepository.findAllFileNames();
        } catch (SQLException e) {
            throw new RemoteException("Erro ao listar arquivos", e);
        }
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    @Override
    public int getClusterSize() throws RemoteException {
        return channel.getView().size();
    }

    @Override
    public List<br.ifmg.sd.models.FileMetadata> findFilesByName(String fileName)
        throws RemoteException {
        System.out.println("RMI: Buscando arquivos com nome: " + fileName);

        try {
            return fileRepository.findByFileName(fileName);
        } catch (Exception e) {
            throw new RemoteException("Erro ao buscar arquivos", e);
        }
    }

    @Override
    public byte[] downloadFile(String userId, String fileName)
        throws RemoteException {
        System.out.println(
            "RMI: Download do arquivo " + fileName + " do usuário " + userId
        );

        Lock lock = lockService.getLock(userId + ":" + fileName);
        lock.lock();

        try {
            byte[] content = fileRepository.findByUserIdAndFileName(userId, fileName);

            if (content == null) {
                throw new RemoteException(
                    "Arquivo não encontrado: " + fileName + " (usuário: " + userId + ")"
                );
            }

            System.out.println(
                "Download concluído: " +
                fileName +
                " (" + content.length + " bytes)"
            );

            return content;
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteException("Erro ao fazer download do arquivo", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean registerUser(String userId, String name, String password, String email)
        throws RemoteException {
        System.out.println("RMI: Registrando usuário: " + email);

        try {
            long createdAt = System.currentTimeMillis();
            boolean success = userRepository.registerWithTimestamp(userId, name, password, email, createdAt);
            
            if (!success) {
                System.out.println("Email já está em uso: " + email);
                return false;
            }
            
            System.out.println("Usuário registrado: " + email);

            UserReplication replication = new UserReplication(
                userId,
                name,
                password,
                email,
                createdAt
            );

            int clusterSize = channel.getView().size();
            int expectedAcks = clusterSize - 1;
            
            if (expectedAcks > 0) {
                replicationCoordinator.startOperation(
                    replication.getOperationId(),
                    expectedAcks
                );
            }

            Message msg = new ObjectMessage(null, replication);
            channel.send(msg);

            if (expectedAcks > 0) {
                boolean replicationSuccess = replicationCoordinator.waitForCompletion(
                    replication.getOperationId(),
                    10,
                    TimeUnit.SECONDS
                );

                if (!replicationSuccess) {
                    System.err.println(
                        "Aviso: Nem todas as réplicas confirmaram o registro do usuário"
                    );
                }
            }

            System.out.println("Usuário registrado e replicado com sucesso");
            return true;
        } catch (Exception e) {
            throw new RemoteException("Erro ao registrar usuário", e);
        }
    }

    @Override
    public br.ifmg.sd.models.User login(String email, String password)
        throws RemoteException {
        System.out.println("RMI: Login: " + email);

        try {
            br.ifmg.sd.models.User user = userRepository.login(email, password);
            if (user != null) {
                System.out.println("Login bem-sucedido: " + email);
                user.setPassword(null);
            } else {
                System.out.println("Login falhou: " + email);
            }
            return user;
        } catch (Exception e) {
            throw new RemoteException("Erro ao fazer login", e);
        }
    }

    // ==================== JGroups Receiver ====================

    @Override
    public void receive(Message msg) {
        Object obj = null;
        try {
            obj = msg.getObject();
        } catch (Exception e) {
            System.err.println("Erro ao deserializar mensagem: " + e.getMessage());
            return;
        }

        if (obj instanceof FileReplication) {
            handleFileReplication(msg, (FileReplication) obj);
        } else if (obj instanceof UserReplication) {
            handleUserReplication(msg, (UserReplication) obj);
        } else if (obj instanceof ReplicationAck) {
            handleReplicationAck((ReplicationAck) obj);
        }
    }

    private void handleFileReplication(Message msg, FileReplication replication) {
        if (msg.getSrc().equals(channel.getAddress())) {
            System.out.println("Ignorando mensagem própria: " + replication.getOperationId());
            return;
        }

        boolean success = false;
        String errorMessage = null;

        try {
            System.out.println("Recebendo replicação: " + replication);

            switch (replication.getOperation()) {
                case SAVE:
                    fileRepository.saveWithDiskPath(
                        replication.getUserId(),
                        replication.getUserName(),
                        replication.getFileName(),
                        replication.getContent(),
                        replication.getDiskPath(),
                        replication.getCreatedAt(),
                        replication.getUpdatedAt()
                    );
                    break;
                case DELETE:
                    fileRepository.delete(
                        replication.getUserId(),
                        replication.getFileName()
                    );
                    break;
                case EDIT:
                    fileRepository.edit(
                        replication.getUserId(),
                        replication.getFileName(),
                        replication.getContent(),
                        replication.getDiskPath()
                    );
                    break;
            }
            success = true;
        } catch (Exception e) {
            errorMessage = e.getClass().getSimpleName() + ": " + 
                          (e.getMessage() != null ? e.getMessage() : "Unknown error");
            System.err.println(
                "Erro ao processar replicação: " + errorMessage
            );
            e.printStackTrace();
        }

        try {
            ReplicationAck ack = new ReplicationAck(
                replication.getOperationId(),
                serverName,
                success,
                errorMessage != null ? errorMessage : ""
            );

            Message ackMsg = new ObjectMessage(msg.getSrc(), ack);
            channel.send(ackMsg);
            System.out.println("ACK enviado: " + ack);
        } catch (Exception e) {
            System.err.println("Erro ao enviar ACK: " + e.getMessage());
        }
    }

    private void handleReplicationAck(ReplicationAck ack) {
        System.out.println("Recebendo ACK: " + ack);
        replicationCoordinator.registerAck(
            ack.getOperationId(),
            ack.getSenderId(),
            ack.isSuccess()
        );
    }

    private void handleUserReplication(Message msg, UserReplication replication) {
        if (msg.getSrc().equals(channel.getAddress())) {
            System.out.println("Ignorando mensagem própria: " + replication.getOperationId());
            return;
        }

        boolean success = false;
        String errorMessage = null;

        try {
            System.out.println("Recebendo replicação de usuário: " + replication);

            userRepository.registerWithTimestamp(
                replication.getUserId(),
                replication.getName(),
                replication.getPassword(),
                replication.getEmail(),
                replication.getCreatedAt()
            );
            
            success = true;
        } catch (Exception e) {
            errorMessage = e.getClass().getSimpleName() + ": " + 
                          (e.getMessage() != null ? e.getMessage() : "Unknown error");
            System.err.println(
                "Erro ao processar replicação de usuário: " + errorMessage
            );
            e.printStackTrace();
        }

        try {
            ReplicationAck ack = new ReplicationAck(
                replication.getOperationId(),
                serverName,
                success,
                errorMessage != null ? errorMessage : ""
            );

            Message ackMsg = new ObjectMessage(msg.getSrc(), ack);
            channel.send(ackMsg);
            System.out.println("ACK enviado: " + ack);
        } catch (Exception e) {
            System.err.println("Erro ao enviar ACK: " + e.getMessage());
        }
    }

    @Override
    public void getState(OutputStream output) throws Exception {
        System.out.println("Solicitacao de estado recebida");
        DataOutputStream dataOutput = new DataOutputStream(output);

        // Enviar usuários primeiro
        List<br.ifmg.sd.models.User> users = userRepository.findAll();
        dataOutput.writeInt(users.size());
        System.out.println("Enviando " + users.size() + " usuários...");

        for (br.ifmg.sd.models.User user : users) {
            dataOutput.writeUTF(user.getId());
            dataOutput.writeUTF(user.getName());
            dataOutput.writeUTF(user.getPassword());
            dataOutput.writeUTF(user.getEmail());
            System.out.println("  Enviando usuário: " + user.getEmail());
        }

        // Enviar arquivos
        int fileCount = fileRepository.size();
        dataOutput.writeInt(fileCount);
        System.out.println("Enviando " + fileCount + " registros do banco...");

        for (br.ifmg.sd.models.FileMetadata metadata : fileRepository.getAllMetadata()) {
            dataOutput.writeUTF(metadata.getUserId());
            dataOutput.writeUTF(metadata.getUserName());
            dataOutput.writeUTF(metadata.getFileName());
            dataOutput.writeUTF(metadata.getDiskPath());
            dataOutput.writeLong(metadata.getCreatedAt());
            dataOutput.writeLong(metadata.getUpdatedAt());

            try {
                byte[] fileContent = fileRepository.findByUserIdAndFileName(
                    metadata.getUserId(),
                    metadata.getFileName()
                );
                if (fileContent != null) {
                    dataOutput.writeInt(fileContent.length);
                    dataOutput.write(fileContent);
                    System.out.println(
                        "  Enviando arquivo: " +
                            metadata.getFileName() +
                            " (" +
                            fileContent.length +
                            " bytes)"
                    );
                } else {
                    dataOutput.writeInt(0);
                    System.out.println(
                        "  Aviso: arquivo físico não encontrado: " + metadata.getFileName()
                    );
                }
            } catch (Exception e) {
                System.err.println(
                    "Erro ao enviar arquivo " + metadata.getFileName() + ": " + e.getMessage()
                );
                dataOutput.writeInt(0);
            }
        }

        dataOutput.flush();
        System.out.println(
            "Estado completo enviado: " + users.size() + " usuários, " + fileCount + " arquivos"
        );
    }

    @Override
    public void setState(InputStream input) throws Exception {
        System.out.println("Recebendo estado...");
        DataInputStream dataInput = new DataInputStream(input);

        // Receber usuários primeiro
        int userCount = dataInput.readInt();
        System.out.println("Recebendo " + userCount + " usuários...");

        int userSuccessCount = 0;
        int userErrorCount = 0;

        for (int i = 0; i < userCount; i++) {
            try {
                String userId = dataInput.readUTF();
                String name = dataInput.readUTF();
                String password = dataInput.readUTF();
                String email = dataInput.readUTF();

                userRepository.save(new br.ifmg.sd.models.User(userId, email, name, password));
                userSuccessCount++;
                System.out.println(
                    "  [" +
                        (i + 1) +
                        "/" +
                        userCount +
                        "] Usuário replicado: " +
                        email
                );
            } catch (Exception e) {
                userErrorCount++;
                System.err.println(
                    "  Erro ao receber usuário [" +
                        (i + 1) +
                        "/" +
                        userCount +
                        "]: " +
                        e.getMessage()
                );
            }
        }

        // Receber arquivos
        int fileCount = dataInput.readInt();
        System.out.println("Recebendo " + fileCount + " arquivos...");

        int successCount = 0;
        int errorCount = 0;

        for (int i = 0; i < fileCount; i++) {
            try {
                String userId = dataInput.readUTF();
                String userName = dataInput.readUTF();
                String fileName = dataInput.readUTF();
                String diskPath = dataInput.readUTF();
                long createdAt = dataInput.readLong();
                long updatedAt = dataInput.readLong();
                int contentLength = dataInput.readInt();

                byte[] content = null;
                if (contentLength > 0) {
                    content = new byte[contentLength];
                    dataInput.readFully(content);
                }

                // Salvar no banco e disco com o mesmo diskPath
                if (content != null) {
                    fileRepository.saveWithDiskPath(
                        userId,
                        userName,
                        fileName,
                        content,
                        diskPath,
                        createdAt,
                        updatedAt
                    );
                    successCount++;
                    System.out.println(
                        "  [" +
                            (i + 1) +
                            "/" +
                            fileCount +
                            "] Replicado: " +
                            fileName +
                            " (" +
                            contentLength +
                            " bytes)"
                    );
                } else {
                    System.out.println(
                        "  [" +
                            (i + 1) +
                            "/" +
                            fileCount +
                            "] Ignorado (sem conteúdo): " +
                            fileName
                    );
                }
            } catch (Exception e) {
                errorCount++;
                System.err.println(
                    "  Erro ao receber arquivo [" +
                        (i + 1) +
                        "/" +
                        fileCount +
                        "]: " +
                        e.getMessage()
                );
            }
        }

        System.out.println(
            "Estado recebido! Usuários - Sucesso: " +
                userSuccessCount +
                ", Erros: " +
                userErrorCount +
                " | Arquivos - Sucesso: " +
                successCount +
                ", Erros: " +
                errorCount
        );
    }

    // ==================== Shutdown ====================

    public void stop() {
        try {
            if (channel != null && channel.isConnected()) {
                channel.close();
            }
            if (dbConnection != null) {
                dbConnection.close();
            }
            System.out.println(serverName + " desconectado");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
