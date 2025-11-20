package br.ifmg.sd.gateway;

import br.ifmg.sd.rpc.AuthResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.jgroups.*;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;

public class HttpGateway {

    private JChannel channel;
    private RpcDispatcher dispatcher;
    private HttpServer server;

    public void start(int port) throws Exception {
        System.out.println("Iniciando HTTP Gateway na porta " + port);

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
        System.out.println("Servidores de controle disponíveis: " + getControlServers().size());

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/logout", new LogoutHandler());
        server.createContext("/api/validate", new ValidateHandler());
        server.createContext("/health", new HealthHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("HTTP Gateway aguardando requisições na porta " + port);
    }

    private java.util.List<Address> getControlServers() {
        return channel.getView().getMembers().stream()
            .filter(addr -> !addr.equals(channel.getAddress()))
            .collect(Collectors.toList());
    }

    class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            try {
                Map<String, String> body = parseJsonBody(exchange);
                String username = body.get("username");
                String email = body.get("email");
                String password = body.get("password");

                if (username == null || email == null || password == null) {
                    sendResponse(exchange, 400, "{\"error\": \"Missing fields\"}");
                    return;
                }

                MethodCall call = new MethodCall(
                    "register",
                    new Object[] { username, email, password },
                    new Class[] { String.class, String.class, String.class }
                );

                RequestOptions opts = new RequestOptions(ResponseMode.GET_FIRST, 5000);
                RspList<AuthResponse> responses = dispatcher.callRemoteMethods(
                    getControlServers(), call, opts
                );

                for (Rsp<AuthResponse> rsp : responses) {
                    if (rsp.wasReceived() && !rsp.wasSuspected()) {
                        AuthResponse authResp = rsp.getValue();
                        if (authResp != null) {
                            if (authResp.isSuccess()) {
                                String json = String.format(
                                    "{\"success\": true, \"token\": \"%s\"}",
                                    authResp.getToken()
                                );
                                sendResponse(exchange, 200, json);
                                return;
                            } else {
                                String json = String.format(
                                    "{\"success\": false, \"error\": \"%s\"}",
                                    authResp.getMessage()
                                );
                                sendResponse(exchange, 400, json);
                                return;
                            }
                        }
                    }
                }

                sendResponse(exchange, 500, "{\"error\": \"No response from servers\"}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }

    class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            try {
                Map<String, String> body = parseJsonBody(exchange);
                String username = body.get("username");
                String password = body.get("password");

                if (username == null || password == null) {
                    sendResponse(exchange, 400, "{\"error\": \"Missing username or password\"}");
                    return;
                }

                MethodCall call = new MethodCall(
                    "login",
                    new Object[] { username, password },
                    new Class[] { String.class, String.class }
                );

                RequestOptions opts = new RequestOptions(ResponseMode.GET_FIRST, 5000);
                RspList<AuthResponse> responses = dispatcher.callRemoteMethods(
                    getControlServers(), call, opts
                );

                for (Rsp<AuthResponse> rsp : responses) {
                    if (rsp.wasReceived() && !rsp.wasSuspected()) {
                        AuthResponse authResp = rsp.getValue();
                        if (authResp != null && authResp.isSuccess()) {
                            String json = String.format(
                                "{\"success\": true, \"token\": \"%s\"}",
                                authResp.getToken()
                            );
                            sendResponse(exchange, 200, json);
                            return;
                        }
                    }
                }

                sendResponse(exchange, 401, "{\"error\": \"Invalid credentials\"}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }

    class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            try {
                String token = exchange.getRequestHeaders().getFirst("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }

                if (token == null || token.isEmpty()) {
                    sendResponse(exchange, 400, "{\"error\": \"Missing token\"}");
                    return;
                }

                MethodCall call = new MethodCall(
                    "logout",
                    new Object[] { token },
                    new Class[] { String.class }
                );

                RequestOptions opts = new RequestOptions(ResponseMode.GET_FIRST, 5000);
                RspList<AuthResponse> responses = dispatcher.callRemoteMethods(
                    getControlServers(), call, opts
                );

                for (Rsp<AuthResponse> rsp : responses) {
                    if (rsp.wasReceived() && !rsp.wasSuspected()) {
                        AuthResponse authResp = rsp.getValue();
                        if (authResp != null && authResp.isSuccess()) {
                            sendResponse(exchange, 200, "{\"success\": true}");
                            return;
                        }
                    }
                }

                sendResponse(exchange, 500, "{\"error\": \"Logout failed\"}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }

    class ValidateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }

            try {
                String token = exchange.getRequestHeaders().getFirst("Authorization");
                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }

                if (token == null || token.isEmpty()) {
                    sendResponse(exchange, 400, "{\"error\": \"Missing token\"}");
                    return;
                }

                MethodCall call = new MethodCall(
                    "validateToken",
                    new Object[] { token },
                    new Class[] { String.class }
                );

                RequestOptions opts = new RequestOptions(ResponseMode.GET_FIRST, 5000);
                RspList<Boolean> responses = dispatcher.callRemoteMethods(
                    getControlServers(), call, opts
                );

                for (Rsp<Boolean> rsp : responses) {
                    if (rsp.wasReceived() && !rsp.wasSuspected()) {
                        Boolean valid = rsp.getValue();
                        if (valid != null && valid) {
                            sendResponse(exchange, 200, "{\"valid\": true}");
                            return;
                        }
                    }
                }

                sendResponse(exchange, 401, "{\"valid\": false}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }

    class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = String.format(
                "{\"status\": \"UP\", \"cluster_members\": %d}",
                getControlServers().size()
            );
            sendResponse(exchange, 200, response);
        }
    }

    private Map<String, String> parseJsonBody(HttpExchange exchange) throws IOException {
        String body = new String(
            exchange.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );
        
        Map<String, String> result = new HashMap<>();
        body = body.trim().replace("{", "").replace("}", "");
        
        for (String pair : body.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                result.put(key, value);
            }
        }
        
        return result;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response)
        throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (dispatcher != null) {
            dispatcher.stop();
        }
        if (channel != null && channel.isConnected()) {
            channel.close();
        }
    }

    public static void main(String[] args) {
        HttpGateway gateway = new HttpGateway();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDesligando HTTP Gateway...");
            gateway.stop();
        }));

        try {
            int port = 8080;
            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
            
            gateway.start(port);
        } catch (Exception e) {
            System.err.println("Erro ao iniciar HTTP Gateway: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
