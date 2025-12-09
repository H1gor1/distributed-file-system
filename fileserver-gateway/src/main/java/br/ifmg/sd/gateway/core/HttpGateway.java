package br.ifmg.sd.gateway.core;

import br.ifmg.sd.gateway.utilities.HandlerFactory;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;
import org.jgroups.*;
import org.jgroups.blocks.RpcDispatcher;

public class HttpGateway {

    private static final int DEFAULT_PORT = 8080;
    private static final String CLUSTER_NAME = "control-cluster";
    private static final String CONFIG_FILE = "udp.xml";

    private JChannel channel;
    private RpcDispatcher dispatcher;
    private HttpServer server;
    private ClusterClient clusterClient;

    public void start(int port) throws Exception {
        System.out.println("Iniciando HTTP Gateway na porta " + port);
        initializeCluster();
        startHttpServer(port);
        System.out.println(
            "HTTP Gateway aguardando requisições na porta " + port
        );
    }

    private void initializeCluster() throws Exception {
        InputStream configStream = getClass()
            .getClassLoader()
            .getResourceAsStream(CONFIG_FILE);
        if (configStream == null) {
            throw new RuntimeException(
                "Arquivo " + CONFIG_FILE + " não encontrado!"
            );
        }

        channel = new JChannel(configStream);
        dispatcher = new RpcDispatcher(channel, null);
        channel.connect(CLUSTER_NAME);

        clusterClient = new ClusterClient(dispatcher, this::getControlServers);

        System.out.println("Gateway conectado ao cluster " + CLUSTER_NAME);
        System.out.println("Membros do cluster: " + channel.getView());
        System.out.println(
            "Servidores disponíveis: " + getControlServers().size()
        );
    }

    private void startHttpServer(int port) throws Exception {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        HandlerFactory factory = new HandlerFactory(
            clusterClient,
            this::getControlServers
        );
        server.createContext("/api/register", factory.createRegisterHandler());
        server.createContext("/api/login", factory.createLoginHandler());
        server.createContext("/api/logout", factory.createLogoutHandler());
        server.createContext("/api/validate", factory.createValidateHandler());
        server.createContext("/api/files/upload", factory.createUploadFileHandler());
        server.createContext("/api/files/list", factory.createListFilesHandler());
        server.createContext("/api/files/search", factory.createSearchFilesHandler());
        server.createContext("/api/files/download", factory.createDownloadFileHandler());
        server.createContext("/api/files/update", factory.createUpdateFileHandler());
        server.createContext("/api/files/delete", factory.createDeleteFileHandler());
        server.createContext("/health", factory.createHealthHandler());

        server.setExecutor(null);
        server.start();
    }

    private List<Address> getControlServers() {
        return channel
            .getView()
            .getMembers()
            .stream()
            .filter(addr -> !addr.equals(channel.getAddress()))
            .collect(Collectors.toList());
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (dispatcher != null) dispatcher.stop();
        if (channel != null && channel.isConnected()) channel.close();
    }

    public static void main(String[] args) {
        HttpGateway gateway = new HttpGateway();

        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> {
                System.out.println("\nDesligando HTTP Gateway...");
                gateway.stop();
            })
        );

        try {
            int port = args.length > 0
                ? Integer.parseInt(args[0])
                : DEFAULT_PORT;
            gateway.start(port);
        } catch (Exception e) {
            System.err.println(
                "Erro ao iniciar HTTP Gateway: " + e.getMessage()
            );
            e.printStackTrace();
        }
    }
}
