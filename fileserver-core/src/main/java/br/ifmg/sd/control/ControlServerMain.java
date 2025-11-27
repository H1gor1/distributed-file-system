package br.ifmg.sd.control;

import java.util.Scanner;

public class ControlServerMain {

    public static void main(String[] args) {
        String serverName = "ControlServer-1";
        String registryHost = "localhost";
        int registryPort = 1099;

        if (args.length > 0) {
            serverName = args[0];
        }
        if (args.length > 1) {
            registryHost = args[1];
        }
        if (args.length > 2) {
            registryPort = Integer.parseInt(args[2]);
        }

        try {
            ControlServer server = new ControlServer(
                serverName,
                registryHost,
                registryPort
            );
            server.start();

            Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    System.out.println("\nDesligando servidor...");
                    server.stop();
                })
            );

            Scanner scanner = new Scanner(System.in);
            System.out.println("\nServidor rodando. Comandos:");
            System.out.println("  stats - Exibir estatísticas");
            System.out.println("  quit  - Sair");

            while (true) {
                System.out.print("> ");
                String command = scanner.nextLine().trim().toLowerCase();

                if (command.equals("quit") || command.equals("exit")) {
                    break;
                } else if (command.equals("stats")) {
                    server.printStats();
                } else {
                    System.out.println("Comando desconhecido");
                }
            }

            scanner.close();
            server.stop();
        } catch (Exception e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
