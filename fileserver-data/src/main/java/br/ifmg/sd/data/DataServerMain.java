package br.ifmg.sd.data;

public class DataServerMain {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println(
                "Uso: java DataServerMain <serverName> [registryHost] [registryPort]"
            );
            System.out.println(
                "Exemplo: java DataServerMain DataServer-1 localhost 1099"
            );
            System.exit(1);
        }

        String serverName = args[0];
        String registryHost = args.length > 1 ? args[1] : "localhost";
        int registryPort = args.length > 2 ? Integer.parseInt(args[2]) : 1099;

        try {
            DataServer server = new DataServer(
                serverName,
                registryHost,
                registryPort
            );
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

            System.out.println(
                "\n" + serverName + " rodando. Pressione Ctrl+C para sair.\n"
            );

            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Erro ao iniciar DataServer: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
