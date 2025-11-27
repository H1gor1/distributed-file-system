package br.ifmg.sd.data;

import br.ifmg.sd.data.repository.FileRepository;
import br.ifmg.sd.data.repository.UserRepository;
import br.ifmg.sd.models.FileReplication;
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
import org.jgroups.*;
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

    public DataServer(String serverName, String registryHost, int registryPort)
        throws RemoteException {
        super();
        this.serverName = serverName;
        this.registryHost = registryHost;
        this.registryPort = registryPort;
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
        channel.getState(null, 10000);

        System.out.println(serverName + " conectado ao data-cluster");
        System.out.println("Endereço: " + channel.getAddress());
        System.out.println("Coordenador? " + isCoordinator());

        if (isCoordinator()) {
            registerInRMI();
        }
    }

    private void initDatabase() throws SQLException {
        String dbPath = serverName + "/" + "fileserver_" + serverName + ".db";
        dbConnection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        String createTable = """
                CREATE TABLE IF NOT EXISTS files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    content BLOB NOT NULL,
                    created_at INTEGER NOT NULL,
                    UNIQUE(user_id, file_name)
                );

                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    password TEXT NOT NULL,
                    email TEXT NOT NULL,
                    UNIQUE(email)
                );
            """;

        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute(createTable);
        }

        fileRepository = new FileRepository(dbConnection);
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
                    "✓ RMI Registry criado na porta " + registryPort
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
                "✓ DataService registrado no RMI Registry como 'data-service'"
            );
        } catch (Exception e) {
            System.err.println(
                "✗ Erro ao registrar no RMI Registry: " + e.getMessage()
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
            fileRepository.save(userId, fileName, content);

            FileReplication replication = new FileReplication(
                userId,
                fileName,
                content,
                FileReplication.OperationType.SAVE
            );

            Message msg = new ObjectMessage(null, replication);
            channel.send(msg);

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
        } catch (SQLException e) {
            throw new RemoteException("Erro ao ler arquivo", e);
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
                fileName,
                null,
                FileReplication.OperationType.DELETE
            );

            Message msg = new ObjectMessage(null, replication);
            channel.send(msg);

            return true;
        } catch (Exception e) {
            throw new RemoteException("Erro ao deletar arquivo", e);
        }
    }

    @Override
    public List<String> listFiles(String userId) throws RemoteException {
        try {
            return fileRepository.findFileNamesByUserId(userId);
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

    // ==================== JGroups Receiver ====================

    @Override
    public void receive(Message msg) {
        try {
            FileReplication replication = msg.getObject();
            System.out.println("Recebendo replicação: " + replication);

            switch (replication.getOperation()) {
                case SAVE:
                    fileRepository.save(
                        replication.getUserId(),
                        replication.getFileName(),
                        replication.getContent()
                    );
                    break;
                case DELETE:
                    fileRepository.delete(
                        replication.getUserId(),
                        replication.getFileName()
                    );
                    break;
            }
        } catch (Exception e) {
            System.err.println(
                "Erro ao processar replicação: " + e.getMessage()
            );
        }
    }

    @Override
    public void getState(OutputStream output) throws Exception {
        // TODO: Implementar transferência de estado
        System.out.println("Enviando estado do banco de dados...");
    }

    @Override
    public void setState(InputStream input) throws Exception {
        // TODO: Implementar recebimento de estado
        System.out.println("Recebendo estado do banco de dados...");
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
