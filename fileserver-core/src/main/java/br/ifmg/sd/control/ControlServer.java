package br.ifmg.sd.control;

import br.ifmg.sd.models.Session;
import br.ifmg.sd.models.SessionUpdate;
import br.ifmg.sd.models.SessionUpdate.UpdateType;
import br.ifmg.sd.models.User;
import br.ifmg.sd.rpc.AuthResponse;
import br.ifmg.sd.rpc.ControlService;
import br.ifmg.sd.rpc.DataService;
import br.ifmg.sd.security.JWTUtil;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jgroups.*;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.Util;

public class ControlServer implements Receiver, ControlService {

    private final String serverName;
    private JChannel channel;
    private RpcDispatcher dispatcher;

    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();
    private final Map<String, User> userDatabase = new ConcurrentHashMap<>();

    private final String registryHost;
    private final int registryPort;
    private DataService dataService;

    public ControlServer(
        String serverName,
        String registryHost,
        int registryPort
    ) {
        this.serverName = serverName;
        this.registryHost = registryHost;
        this.registryPort = registryPort;
    }

    public void start() throws Exception {
        System.out.println("Iniciando " + serverName);

        InputStream configStream = getClass()
            .getClassLoader()
            .getResourceAsStream("udp.xml");

        if (configStream == null) {
            throw new RuntimeException("Arquivo udp.xml nao encontrado!");
        }

        channel = new JChannel(configStream);
        dispatcher = new RpcDispatcher(channel, this);

        channel.setReceiver(this);
        channel.connect("control-cluster");
        channel.getState(null, 10000);

        System.out.println(serverName + " conectado ao cluster!");
        System.out.println("Endereço: " + channel.getAddress());
        System.out.println("RPC Dispatcher pronto para receber chamadas");
    }

    public RpcDispatcher getDispatcher() {
        return dispatcher;
    }

    public void receive(Message msg) {
        try {
            SessionUpdate update = msg.getObject();
            System.out.println("Recebendo dados");

            switch (update.getType()) {
                case CREATE:
                case UPDATE:
                    sessionCache.put(update.getToken(), update.getSession());
                    break;
                case DELETE:
                    sessionCache.remove(update.getToken());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.err.println("Erro ao receber mensagem: " + e.getMessage());
        }
    }

    @Override
    public void getState(OutputStream output) throws Exception {
        DataOutputStream dataOutput = new DataOutputStream(output);
        Util.objectToStream(sessionCache, dataOutput);
    }

    @Override
    public void setState(InputStream input) throws Exception {
        DataInputStream dataInput = new DataInputStream(input);
        Map<String, Session> state = Util.objectFromStream(dataInput);
        sessionCache.putAll(state);
    }

    /**
     * Cria uma nova sessão e replica para o cluster.
     */
    public String createSession(
        String userId,
        String username,
        String userEmail
    ) throws Exception {
        String token = JWTUtil.generateToken(userId, username, userEmail);
        long now = System.currentTimeMillis();
        long expiresAt = JWTUtil.getExpirationTime(token);

        Session session = new Session(
            userId,
            username,
            userEmail,
            token,
            now,
            expiresAt
        );

        sessionCache.put(token, session);

        SessionUpdate update = new SessionUpdate(
            token,
            session,
            UpdateType.CREATE
        );
        Message msg = new ObjectMessage(null, update);
        channel.send(msg);

        System.out.println("Sessão criada e replicada: " + userId);

        return token;
    }

    /**
     * Valida se um token existe e é válido.
     */
    public boolean validateSession(String token) {
        Session session = sessionCache.get(token);

        if (session == null) {
            System.out.println("Token não encontrado no cache");
            return false;
        }

        boolean valid = session.isValid() && JWTUtil.validateToken(token);

        if (valid) {
            System.out.println("Token válido: " + session.getUserId());
        } else {
            System.out.println("Token expirado");
        }

        return valid;
    }

    /**
     * Remove uma sessão (logout) e replica para o cluster.
     */
    public void removeSession(String token) throws Exception {
        Session session = sessionCache.remove(token);

        if (session != null) {
            // Replica remoção para outros servidores
            SessionUpdate update = new SessionUpdate(
                token,
                null,
                UpdateType.DELETE
            );
            Message msg = new ObjectMessage(null, update);
            channel.send(msg);

            System.out.println("Logout: " + session.getUserId());
        }
    }

    @Override
    public AuthResponse login(String username, String password)
        throws Exception {
        System.out.println("Login RPC: " + username);

        try {
            DataService ds = getDataService();
            User user = ds.login(username, password);

            if (user == null) {
                return AuthResponse.error("Invalid credentials");
            }

            userDatabase.put(username, user);

            String token = createSession(
                user.getId(),
                user.getName(),
                user.getEmail()
            );
            return AuthResponse.success(token);
        } catch (Exception e) {
            System.err.println("Erro no login: " + e.getMessage());
            e.printStackTrace();
            return AuthResponse.error("Erro ao fazer login: " + e.getMessage());
        }
    }

    @Override
    public AuthResponse register(String username, String email, String password)
        throws Exception {
        System.out.println("Register RPC: " + username);

        try {
            String userId = java.util.UUID.randomUUID().toString();

            DataService ds = getDataService();
            boolean registered = ds.registerUser(
                userId,
                username,
                password,
                email
            );

            if (!registered) {
                return AuthResponse.error("Email already exists");
            }

            User user = new User(userId, email, username, password);
            userDatabase.put(username, user);

            String token = createSession(userId, username, email);
            return AuthResponse.success(token);
        } catch (Exception e) {
            System.err.println("Erro no registro: " + e.getMessage());
            e.printStackTrace();
            return AuthResponse.error(
                "Erro ao registrar usuario: " + e.getMessage()
            );
        }
    }

    @Override
    public boolean validateToken(String token) throws Exception {
        return validateSession(token);
    }

    @Override
    public AuthResponse logout(String token) throws Exception {
        System.out.println("Logout RPC");
        removeSession(token);
        return new AuthResponse(true, null, "Logout successful");
    }

    @Override
    public String getUserIdFromToken(String token) throws Exception {
        Session session = sessionCache.get(token);
        return session != null ? session.getUserId() : null;
    }

    /**
     * Obtém referência remota ao DataService via RMI Registry lookup.
     */
    public DataService getDataService() throws Exception {
        if (dataService == null) {
            Registry registry = LocateRegistry.getRegistry(
                registryHost,
                registryPort
            );
            dataService = (DataService) registry.lookup("data-service");
            System.out.println("✓ DataService obtido do RMI Registry");
        }
        return dataService;
    }

    /**
     * Exemplo de uso: salvar arquivo no cluster de dados.
     */
    public boolean saveFileToDataCluster(
        String token,
        String fileName,
        byte[] content
    ) throws Exception {
        String userId = getUserIdFromToken(token);
        if (userId == null) {
            throw new IllegalArgumentException("Token inválido");
        }

        try {
            DataService ds = getDataService();
            return ds.saveFile(userId, fileName, content);
        } catch (java.rmi.RemoteException e) {
            throw new Exception("Erro ao salvar arquivo: " + e.getMessage());
        }
    }

    /**
     * Exemplo de uso: listar arquivos do usuário.
     */
    public List<String> listUserFiles(String token) throws Exception {
        String userId = getUserIdFromToken(token);
        if (userId == null) {
            throw new IllegalArgumentException("Token inválido");
        }

        DataService ds = getDataService();
        return ds.listFiles(userId);
    }

    // ==================== Implementação das Operações de Arquivos ====================

    @Override
    public boolean uploadFile(String token, String fileName, byte[] content)
        throws Exception {
        if (!validateSession(token)) {
            throw new SecurityException("Token inválido ou expirado");
        }

        String userId = getUserIdFromToken(token);
        System.out.println(
            "Upload: " +
                fileName +
                " (" +
                content.length +
                " bytes) - usuário: " +
                userId
        );

        try {
            DataService ds = getDataService();
            return ds.saveFile(userId, fileName, content);
        } catch (java.rmi.RemoteException e) {
            throw new Exception("Erro ao fazer upload: " + e.getMessage());
        }
    }

    @Override
    public byte[] downloadFile(String token, String fileName) throws Exception {
        if (!validateSession(token)) {
            throw new SecurityException("Token inválido ou expirado");
        }

        String userId = getUserIdFromToken(token);
        System.out.println("Download: " + fileName + " - usuário: " + userId);

        try {
            DataService ds = getDataService();
            return ds.downloadFile(userId, fileName);
        } catch (java.rmi.RemoteException e) {
            throw new Exception("Erro ao fazer download: " + e.getMessage());
        }
    }

    @Override
    public byte[] downloadFileWithUser(String token, String fileName, String targetUserId) throws Exception {
        if (!validateSession(token)) {
            throw new SecurityException("Token inválido ou expirado");
        }

        // Se targetUserId não foi fornecido, usa o próprio usuário
        String userId = (targetUserId != null && !targetUserId.isEmpty()) 
            ? targetUserId 
            : getUserIdFromToken(token);
            
        System.out.println("Download: " + fileName + " - usuário: " + userId);

        try {
            DataService ds = getDataService();
            return ds.downloadFile(userId, fileName);
        } catch (java.rmi.RemoteException e) {
            throw new Exception("Erro ao fazer download: " + e.getMessage());
        }
    }

    @Override
    public boolean updateFile(String token, String fileName, byte[] newContent)
        throws Exception {
        if (!validateSession(token)) {
            throw new SecurityException("Token inválido ou expirado");
        }

        String userId = getUserIdFromToken(token);
        System.out.println(
            "Update: " +
                fileName +
                " (" +
                newContent.length +
                " bytes) - usuário: " +
                userId
        );

        try {
            DataService ds = getDataService();
            return ds.editFile(userId, fileName, newContent);
        } catch (java.rmi.RemoteException e) {
            throw new Exception("Erro ao atualizar arquivo: " + e.getMessage());
        }
    }

    @Override
    public boolean updateFileWithUser(String token, String fileName, byte[] newContent, String targetUserId)
        throws Exception {
        if (!validateSession(token)) {
            throw new SecurityException("Token inválido ou expirado");
        }

        // Se targetUserId não foi fornecido, usa o próprio usuário
        String userId = (targetUserId != null && !targetUserId.isEmpty()) 
            ? targetUserId 
            : getUserIdFromToken(token);
            
        System.out.println(
            "Update: " + fileName + " (" + newContent.length + " bytes) - usuário: " + userId
        );

        try {
            DataService ds = getDataService();
            return ds.editFile(userId, fileName, newContent);
        } catch (java.rmi.RemoteException e) {
            throw new Exception("Erro ao atualizar arquivo: " + e.getMessage());
        }
    }

    @Override
    public boolean deleteFile(String token, String fileName) throws Exception {
        if (!validateSession(token)) {
            throw new SecurityException("Token inválido ou expirado");
        }

        String userId = getUserIdFromToken(token);
        System.out.println("Delete: " + fileName + " - usuário: " + userId);

        try {
            DataService ds = getDataService();
            return ds.deleteFile(userId, fileName);
        } catch (java.rmi.RemoteException e) {
            throw new Exception("Erro ao deletar arquivo: " + e.getMessage());
        }
    }

    @Override
    public List<br.ifmg.sd.models.FileMetadata> searchFiles(
        String token,
        String fileName
    ) throws Exception {
        if (!validateSession(token)) {
            throw new SecurityException("Token inválido ou expirado");
        }

        System.out.println("Busca: " + fileName);

        try {
            DataService ds = getDataService();
            return ds.findFilesByName(fileName);
        } catch (java.rmi.RemoteException e) {
            throw new Exception("Erro ao buscar arquivos: " + e.getMessage());
        }
    }

    @Override
    public List<String> listMyFiles(String token) throws Exception {
        if (!validateSession(token)) {
            throw new SecurityException("Token inválido ou expirado");
        }

        String userId = getUserIdFromToken(token);
        System.out.println("Listar arquivos - usuário: " + userId);

        try {
            DataService ds = getDataService();
            return ds.listFiles(userId);
        } catch (java.rmi.RemoteException e) {
            throw new Exception("Erro ao listar arquivos: " + e.getMessage());
        }
    }

    /**
     * Para o servidor gracefully.
     */
    public void stop() {
        if (dispatcher != null) {
            dispatcher.stop();
        }
        if (channel != null && channel.isConnected()) {
            channel.close();
            System.out.println(serverName + " desconectado");
        }
    }

    public void printStats() {
        System.out.println("\n Estatísticas do " + serverName + ":");
        System.out.println("   Sessões ativas: " + sessionCache.size());
        System.out.println("   Usuários: " + userDatabase.size());
        System.out.println(
            "   Membros no cluster: " +
                (channel != null ? channel.getView().size() : 0)
        );
        System.out.println();
    }
}
