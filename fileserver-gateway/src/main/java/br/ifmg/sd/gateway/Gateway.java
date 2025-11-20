package br.ifmg.sd.gateway;

import br.ifmg.sd.rpc.AuthResponse;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.jgroups.*;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;

public class Gateway {

    private JChannel channel;
    private RpcDispatcher dispatcher;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = true;

    public Gateway() {}

    public void start(int port) throws Exception {
        System.out.println("Iniciando Gateway na porta " + port);

        InputStream configStream = getClass()
            .getClassLoader()
            .getResourceAsStream("udp.xml");

        if (configStream == null) {
            throw new RuntimeException("Arquivo udp.xml não encontrado!");
        }

        channel = new JChannel(configStream);
        dispatcher = new RpcDispatcher(channel, null);
        
        channel.connect("control-cluster");

        System.out.println("Gateway conectado ao cluster control-cluster como CLIENTE");
        System.out.println("Membros do cluster: " + channel.getView());

        serverSocket = new ServerSocket(port);
        threadPool = Executors.newCachedThreadPool();

        System.out.println("Gateway aguardando conexões na porta " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(() -> handleClient(clientSocket));
            } catch (Exception e) {
                if (running) {
                    System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
            );
            PrintWriter out = new PrintWriter(
                clientSocket.getOutputStream(),
                true
            );
        ) {
            String line = in.readLine();
            if (line == null) return;

            String[] parts = line.split(" ", 2);
            String command = parts[0];
            String data = parts.length > 1 ? parts[1] : "";

            String response;
            switch (command.toUpperCase()) {
                case "LOGIN":
                    response = handleLogin(data);
                    break;
                case "REGISTER":
                    response = handleRegister(data);
                    break;
                case "LOGOUT":
                    response = handleLogout(data);
                    break;
                case "VALIDATE":
                    response = handleValidate(data);
                    break;
                default:
                    response = "ERROR: Unknown command";
            }

            out.println(response);
        } catch (Exception e) {
            System.err.println("Erro ao processar cliente: " + e.getMessage());
        }
    }

    private String handleLogin(String data) {
        try {
            String[] parts = data.split(":", 2);
            if (parts.length != 2) {
                return "ERROR: Invalid format. Use: LOGIN username:password";
            }

            String username = parts[0];
            String password = parts[1];

            MethodCall call = new MethodCall(
                "login",
                new Object[] { username, password },
                new Class[] { String.class, String.class }
            );

            RequestOptions opts = new RequestOptions(
                ResponseMode.GET_FIRST,
                5000
            );
            
            // Chama apenas nos OUTROS membros do cluster (servidores de controle)
            RspList<AuthResponse> responses = dispatcher.callRemoteMethods(
                getControlServers(),
                call,
                opts
            );

            for (Rsp<AuthResponse> rsp : responses) {
                if (rsp.wasReceived() && !rsp.wasSuspected()) {
                    AuthResponse authResp = rsp.getValue();
                    if (authResp != null && authResp.isSuccess()) {
                        System.out.println("Login bem-sucedido: " + username);
                        return "SUCCESS: " + authResp.getToken();
                    }
                }
            }

            return "ERROR: Invalid credentials";
        } catch (Exception e) {
            System.err.println("Erro no login: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private String handleRegister(String data) {
        try {
            String[] parts = data.split(":", 3);
            if (parts.length != 3) {
                return "ERROR: Invalid format. Use: REGISTER username:email:password";
            }

            String username = parts[0];
            String email = parts[1];
            String password = parts[2];

            MethodCall call = new MethodCall(
                "register",
                new Object[] { username, email, password },
                new Class[] { String.class, String.class, String.class }
            );

            RequestOptions opts = new RequestOptions(
                ResponseMode.GET_FIRST,
                5000
            );
            
            RspList<AuthResponse> responses = dispatcher.callRemoteMethods(
                getControlServers(),
                call,
                opts
            );

            for (Rsp<AuthResponse> rsp : responses) {
                if (rsp.wasReceived() && !rsp.wasSuspected()) {
                    AuthResponse authResp = rsp.getValue();
                    if (authResp != null) {
                        if (authResp.isSuccess()) {
                            System.out.println("Registro bem-sucedido: " + username);
                            return "SUCCESS: " + authResp.getToken();
                        } else {
                            return "ERROR: " + authResp.getMessage();
                        }
                    }
                }
            }

            return "ERROR: Registration failed";
        } catch (Exception e) {
            System.err.println("Erro no registro: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private String handleLogout(String token) {
        try {
            MethodCall call = new MethodCall(
                "logout",
                new Object[] { token },
                new Class[] { String.class }
            );

            RequestOptions opts = new RequestOptions(
                ResponseMode.GET_FIRST,
                5000
            );
            
            RspList<AuthResponse> responses = dispatcher.callRemoteMethods(
                getControlServers(),
                call,
                opts
            );

            for (Rsp<AuthResponse> rsp : responses) {
                if (rsp.wasReceived() && !rsp.wasSuspected()) {
                    AuthResponse authResp = rsp.getValue();
                    if (authResp != null && authResp.isSuccess()) {
                        System.out.println("Logout bem-sucedido");
                        return "SUCCESS: Logout successful";
                    }
                }
            }

            return "ERROR: Logout failed";
        } catch (Exception e) {
            System.err.println("Erro no logout: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private String handleValidate(String token) {
        try {
            MethodCall call = new MethodCall(
                "validateToken",
                new Object[] { token },
                new Class[] { String.class }
            );

            RequestOptions opts = new RequestOptions(
                ResponseMode.GET_FIRST,
                5000
            );
            
            RspList<Boolean> responses = dispatcher.callRemoteMethods(
                getControlServers(),
                call,
                opts
            );

            for (Rsp<Boolean> rsp : responses) {
                if (rsp.wasReceived() && !rsp.wasSuspected()) {
                    Boolean valid = rsp.getValue();
                    if (valid != null && valid) {
                        return "SUCCESS: Token is valid";
                    }
                }
            }

            return "ERROR: Invalid token";
        } catch (Exception e) {
            System.err.println("Erro na validação: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    private java.util.List<Address> getControlServers() {
        // Retorna todos os membros EXCETO este gateway
        return channel.getView().getMembers().stream()
            .filter(addr -> !addr.equals(channel.getAddress()))
            .collect(java.util.stream.Collectors.toList());
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (threadPool != null) {
                threadPool.shutdown();
            }
            if (dispatcher != null) {
                dispatcher.stop();
            }
            if (channel != null && channel.isConnected()) {
                channel.close();
            }
        } catch (Exception e) {
            System.err.println("Erro ao parar Gateway: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Gateway gateway = new Gateway();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDesligando Gateway...");
            gateway.stop();
        }));

        try {
            int port = 9090;
            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
            
            gateway.start(port);
        } catch (Exception e) {
            System.err.println("Erro ao iniciar Gateway: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
