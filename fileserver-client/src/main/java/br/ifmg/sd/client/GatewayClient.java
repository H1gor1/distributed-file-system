package br.ifmg.sd.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class GatewayClient {

    private String gatewayHost;
    private int gatewayPort;
    private String currentToken;

    public GatewayClient(String gatewayHost, int gatewayPort) {
        this.gatewayHost = gatewayHost;
        this.gatewayPort = gatewayPort;
    }

    private String sendCommand(String command) {
        try (
            Socket socket = new Socket(gatewayHost, gatewayPort);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );
        ) {
            out.println(command);
            return in.readLine();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public void login(String username, String password) {
        String command = "LOGIN " + username + ":" + password;
        String response = sendCommand(command);
        
        if (response.startsWith("SUCCESS:")) {
            currentToken = response.substring("SUCCESS: ".length());
            System.out.println("Login bem-sucedido! Token: " + currentToken);
        } else {
            System.out.println("Falha no login: " + response);
        }
    }

    public void register(String username, String email, String password) {
        String command = "REGISTER " + username + ":" + email + ":" + password;
        String response = sendCommand(command);
        
        if (response.startsWith("SUCCESS:")) {
            currentToken = response.substring("SUCCESS: ".length());
            System.out.println("Registro bem-sucedido! Token: " + currentToken);
        } else {
            System.out.println("Falha no registro: " + response);
        }
    }

    public void logout() {
        if (currentToken == null) {
            System.out.println("Você não está logado!");
            return;
        }
        
        String command = "LOGOUT " + currentToken;
        String response = sendCommand(command);
        
        if (response.startsWith("SUCCESS:")) {
            System.out.println("Logout bem-sucedido!");
            currentToken = null;
        } else {
            System.out.println("Falha no logout: " + response);
        }
    }

    public void validateToken() {
        if (currentToken == null) {
            System.out.println("Você não está logado!");
            return;
        }
        
        String command = "VALIDATE " + currentToken;
        String response = sendCommand(command);
        System.out.println(response);
    }

    public static void main(String[] args) {
        GatewayClient client = new GatewayClient("localhost", 9090);
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=== Cliente Gateway ===");
        System.out.println("Comandos disponíveis:");
        System.out.println("  1. register <username> <email> <password>");
        System.out.println("  2. login <username> <password>");
        System.out.println("  3. logout");
        System.out.println("  4. validate");
        System.out.println("  5. exit");
        System.out.println();

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) continue;
            
            String[] parts = input.split("\\s+");
            String command = parts[0].toLowerCase();
            
            switch (command) {
                case "register":
                    if (parts.length != 4) {
                        System.out.println("Uso: register <username> <email> <password>");
                    } else {
                        client.register(parts[1], parts[2], parts[3]);
                    }
                    break;
                    
                case "login":
                    if (parts.length != 3) {
                        System.out.println("Uso: login <username> <password>");
                    } else {
                        client.login(parts[1], parts[2]);
                    }
                    break;
                    
                case "logout":
                    client.logout();
                    break;
                    
                case "validate":
                    client.validateToken();
                    break;
                    
                case "exit":
                case "quit":
                    System.out.println("Saindo...");
                    scanner.close();
                    return;
                    
                default:
                    System.out.println("Comando desconhecido: " + command);
            }
        }
    }
}
