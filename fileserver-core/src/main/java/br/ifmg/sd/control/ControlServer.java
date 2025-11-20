package br.ifmg.sd.control;

import br.ifmg.sd.models.Session;
import br.ifmg.sd.models.SessionUpdate;
import br.ifmg.sd.models.SessionUpdate.UpdateType;
import br.ifmg.sd.models.User;
import br.ifmg.sd.rpc.AuthResponse;
import br.ifmg.sd.rpc.ControlService;
import br.ifmg.sd.security.JWTUtil;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jgroups.*;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;
import org.jgroups.util.Util;

public class ControlServer implements Receiver, ControlService {

    private final String serverName;
    private JChannel channel;
    private RpcDispatcher dispatcher;

    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();
    private final Map<String, User> userDatabase = new ConcurrentHashMap<>();

    public ControlServer(String serverName) {
        this.serverName = serverName;
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
    public AuthResponse login(String username, String password) throws Exception {
        System.out.println("Login RPC: " + username);
        
        User user = userDatabase.get(username);
        if (user == null || !user.getPassword().equals(password)) {
            return AuthResponse.error("Invalid credentials");
        }
        
        String token = createSession(user.getId(), user.getName(), user.getEmail());
        return AuthResponse.success(token);
    }

    @Override
    public AuthResponse register(String username, String email, String password) throws Exception {
        System.out.println("Register RPC: " + username);
        
        if (userDatabase.containsKey(username)) {
            return AuthResponse.error("Username already exists");
        }
        
        String userId = "user_" + System.currentTimeMillis();
        User user = new User(userId, username, email, password);
        userDatabase.put(username, user);
        
        String token = createSession(userId, username, email);
        return AuthResponse.success(token);
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
