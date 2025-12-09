package br.ifmg.fileserver.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Scanner;

public class ClientMain {

    private static final String GATEWAY_URL = "http://localhost:8080";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();
    private static String currentToken = null;

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════╗");
        System.out.println("║   Distributed File System - Client       ║");
        System.out.println("╚═══════════════════════════════════════════╝");
        System.out.println("Gateway: " + GATEWAY_URL);

        Scanner scanner = new Scanner(System.in);

        while (true) {
            printMenu();
            System.out.print("→ ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            try {
                if (!processCommand(input)) break;
            } catch (Exception e) {
                System.err.println("❌ Erro: " + e.getMessage());
            }
        }

        scanner.close();
    }

    private static void printMenu() {
        System.out.println("\n═════════════════ MENU ═════════════════");
        System.out.println("1. register <username> <email> <password>");
        System.out.println("2. login <username> <password>");
        System.out.println("3. logout");
        System.out.println("4. validate");
        System.out.println("5. upload <filepath>");
        System.out.println("6. list");
        System.out.println("7. search <filename>");
        System.out.println("8. download <filename>");
        System.out.println("9. update <filename> <filepath>");
        System.out.println("10. delete <filename>");
        System.out.println("11. health");
        System.out.println("12. exit");
        System.out.println("════════════════════════════════════════");
        if (currentToken != null) {
            System.out.println(
                "Token: " +
                    currentToken.substring(
                        0,
                        Math.min(20, currentToken.length())
                    ) +
                    "..."
            );
        }
    }

    private static boolean processCommand(String input) throws Exception {
        String[] parts = input.split("\\s+");
        String command = parts[0].toLowerCase();

        switch (command) {
            case "1":
            case "register":
                if (parts.length < 4) {
                    System.out.println(
                        "Uso: register <username> <email> <password>"
                    );
                    return true;
                }
                register(parts[1], parts[2], parts[3]);
                break;
            case "2":
            case "login":
                if (parts.length < 3) {
                    System.out.println("Uso: login <username> <password>");
                    return true;
                }
                login(parts[1], parts[2]);
                break;
            case "3":
            case "logout":
                logout();
                break;
            case "4":
            case "validate":
                validateToken();
                break;
            case "5":
            case "upload":
                if (parts.length < 2) {
                    System.out.println("Uso: upload <filepath>");
                    return true;
                }
                uploadFile(parts[1]);
                break;
            case "6":
            case "list":
                listFiles();
                break;
            case "7":
            case "search":
                if (parts.length < 2) {
                    System.out.println("Uso: search <filename>");
                    return true;
                }
                searchFiles(parts[1]);
                break;
            case "8":
            case "download":
                if (parts.length < 2) {
                    System.out.println("Uso: download <filename>");
                    return true;
                }
                downloadFile(parts[1]);
                break;
            case "9":
            case "update":
                if (parts.length < 3) {
                    System.out.println("Uso: update <filename> <filepath>");
                    return true;
                }
                updateFile(parts[1], parts[2]);
                break;
            case "10":
            case "delete":
                if (parts.length < 2) {
                    System.out.println("Uso: delete <filename>");
                    return true;
                }
                deleteFile(parts[1]);
                break;
            case "11":
            case "health":
                checkHealth();
                break;
            case "12":
            case "exit":
                System.out.println("Até logo!");
                return false;
            default:
                System.out.println("Comando desconhecido: " + command);
        }

        return true;
    }

    private static void register(String username, String email, String password)
        throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("email", email);
        body.addProperty("password", password);

        String response = sendPost("/api/register", body.toString(), null);
        JsonObject json = gson.fromJson(response, JsonObject.class);

        if (json.get("success").getAsBoolean()) {
            currentToken = json.get("token").getAsString();
            System.out.println("Registrado com sucesso!");
            System.out.println("Token: " + currentToken);
        } else {
            System.out.println("Erro: " + json.get("message").getAsString());
        }
    }

    private static void login(String username, String password)
        throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);

        String response = sendPost("/api/login", body.toString(), null);
        JsonObject json = gson.fromJson(response, JsonObject.class);

        if (json.get("success").getAsBoolean()) {
            currentToken = json.get("token").getAsString();
            System.out.println("Login realizado com sucesso!");
            System.out.println("Token: " + currentToken);
        } else {
            System.out.println("Erro: " + json.get("message").getAsString());
        }
    }

    private static void logout() throws Exception {
        if (currentToken == null) {
            System.out.println("Voce nao esta logado!");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("token", currentToken);

        String response = sendPost("/api/logout", body.toString(), null);
        JsonObject json = gson.fromJson(response, JsonObject.class);

        System.out.println(json.get("message").getAsString());
        currentToken = null;
    }

    private static void validateToken() throws Exception {
        if (currentToken == null) {
            System.out.println("Nenhum token para validar!");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("token", currentToken);

        String response = sendPost("/api/validate", body.toString(), null);
        JsonObject json = gson.fromJson(response, JsonObject.class);

        if (json.get("valid").getAsBoolean()) {
            System.out.println("Token válido!");
        } else {
            System.out.println("Token inválido ou expirado!");
            currentToken = null;
        }
    }

    private static void uploadFile(String filepath) throws Exception {
        if (currentToken == null) {
            System.out.println("Faça login primeiro!");
            return;
        }

        Path path = Paths.get(filepath);
        if (!Files.exists(path)) {
            System.out.println("Arquivo não encontrado: " + filepath);
            return;
        }

        byte[] fileContent = Files.readAllBytes(path);
        String base64Content = Base64.getEncoder().encodeToString(fileContent);
        String fileName = path.getFileName().toString();

        JsonObject body = new JsonObject();
        body.addProperty("fileName", fileName);
        body.addProperty("content", base64Content);

        System.out.println(
            "Enviando arquivo: " +
                fileName +
                " (" +
                fileContent.length +
                " bytes)"
        );

        String response = sendPost(
            "/api/files/upload",
            body.toString(),
            currentToken
        );
        JsonObject json = gson.fromJson(response, JsonObject.class);

        System.out.println(json.get("message").getAsString());
    }

    private static void listFiles() throws Exception {
        if (currentToken == null) {
            System.out.println("Faça login primeiro!");
            return;
        }

        String response = sendGet("/api/files/list", currentToken);
        JsonObject json = gson.fromJson(response, JsonObject.class);

        System.out.println("\nSeus arquivos:");
        json
            .getAsJsonArray("files")
            .forEach(file -> System.out.println("  • " + file.getAsString()));
    }

    private static void searchFiles(String fileName) throws Exception {
        if (currentToken == null) {
            System.out.println("Faca login primeiro!");
            return;
        }

        String response = sendGet(
            "/api/files/search?fileName=" + fileName,
            currentToken
        );
        JsonObject json = gson.fromJson(response, JsonObject.class);

        if (!json.get("success").getAsBoolean()) {
            System.out.println("Erro: " + json.get("message").getAsString());
            return;
        }

        var filesArray = json.getAsJsonArray("files");
        if (filesArray.size() == 0) {
            System.out.println("Nenhum arquivo encontrado com o nome: " + fileName);
            return;
        }

        System.out.println("\nArquivos encontrados (" + filesArray.size() + "):");
        System.out.println("=============================================================");
        
        for (int i = 0; i < filesArray.size(); i++) {
            var file = filesArray.get(i).getAsJsonObject();
            System.out.println("\n[" + (i + 1) + "] " + file.get("fileName").getAsString());
            System.out.println("    Criado por: " + file.get("userName").getAsString());
            System.out.println("    Criado em: " + formatDate(file.get("createdAt").getAsLong()));
            System.out.println("    Modificado em: " + formatDate(file.get("updatedAt").getAsLong()));
            System.out.println("    Tamanho: " + formatBytes(file.get("fileSize").getAsLong()));
            System.out.println("    User ID: " + file.get("userId").getAsString());
        }
        System.out.println("=============================================================");
    }

    private static void downloadFile(String fileName) throws Exception {
        if (currentToken == null) {
            System.out.println("Faca login primeiro!");
            return;
        }

        // Buscar arquivos com esse nome
        String searchResponse = sendGet(
            "/api/files/search?fileName=" + fileName,
            currentToken
        );
        JsonObject searchJson = gson.fromJson(searchResponse, JsonObject.class);

        if (!searchJson.get("success").getAsBoolean()) {
            System.out.println("Erro: " + searchJson.get("message").getAsString());
            return;
        }

        var filesArray = searchJson.getAsJsonArray("files");
        if (filesArray.size() == 0) {
            System.out.println("Nenhum arquivo encontrado com o nome: " + fileName);
            return;
        }

        String selectedUserId = null;
        
        // Se houver múltiplos arquivos, perguntar qual
        if (filesArray.size() > 1) {
            System.out.println("\nMultiplos arquivos encontrados:");
            System.out.println("=============================================================");
            
            for (int i = 0; i < filesArray.size(); i++) {
                var file = filesArray.get(i).getAsJsonObject();
                System.out.println("\n[" + (i + 1) + "] " + file.get("fileName").getAsString());
                System.out.println("    Criado por: " + file.get("userName").getAsString());
                System.out.println("    Criado em: " + formatDate(file.get("createdAt").getAsLong()));
                System.out.println("    Modificado em: " + formatDate(file.get("updatedAt").getAsLong()));
                System.out.println("    Tamanho: " + formatBytes(file.get("fileSize").getAsLong()));
            }
            System.out.println("=============================================================");
            
            System.out.print("\nEscolha o numero do arquivo (1-" + filesArray.size() + "): ");
            Scanner scanner = new Scanner(System.in);
            int choice = scanner.nextInt();
            
            if (choice < 1 || choice > filesArray.size()) {
                System.out.println("Opcao invalida!");
                return;
            }
            
            selectedUserId = filesArray.get(choice - 1).getAsJsonObject().get("userId").getAsString();
        } else {
            selectedUserId = filesArray.get(0).getAsJsonObject().get("userId").getAsString();
        }

        System.out.println("Baixando arquivo: " + fileName);

        String response = sendGet(
            "/api/files/download?fileName=" + fileName + "&userId=" + selectedUserId,
            currentToken
        );
        JsonObject json = gson.fromJson(response, JsonObject.class);

        if (!json.get("success").getAsBoolean()) {
            System.out.println("Erro: " + json.get("message").getAsString());
            return;
        }

        String base64Content = json.get("content").getAsString();
        byte[] content = Base64.getDecoder().decode(base64Content);

        Path downloadPath = Paths.get("downloads", fileName);
        Files.createDirectories(downloadPath.getParent());
        Files.write(downloadPath, content);

        System.out.println(
            "Arquivo baixado com sucesso! (" +
                content.length +
                " bytes)"
        );
        System.out.println("Salvo em: " + downloadPath.toAbsolutePath());
    }

    private static void updateFile(String fileName, String filepath)
        throws Exception {
        if (currentToken == null) {
            System.out.println("Faca login primeiro!");
            return;
        }

        Path path = Paths.get(filepath);
        if (!Files.exists(path)) {
            System.out.println("Arquivo nao encontrado: " + filepath);
            return;
        }

        // Buscar arquivos com esse nome
        String searchResponse = sendGet(
            "/api/files/search?fileName=" + fileName,
            currentToken
        );
        JsonObject searchJson = gson.fromJson(searchResponse, JsonObject.class);

        if (!searchJson.get("success").getAsBoolean()) {
            System.out.println("Erro: " + searchJson.get("message").getAsString());
            return;
        }

        var filesArray = searchJson.getAsJsonArray("files");
        if (filesArray.size() == 0) {
            System.out.println("Nenhum arquivo encontrado com o nome: " + fileName);
            System.out.println("Use 'upload' para criar um novo arquivo.");
            return;
        }

        String selectedUserId = null;
        
        // Se houver múltiplos arquivos, perguntar qual
        if (filesArray.size() > 1) {
            System.out.println("\nMultiplos arquivos encontrados:");
            System.out.println("=============================================================");
            
            for (int i = 0; i < filesArray.size(); i++) {
                var file = filesArray.get(i).getAsJsonObject();
                System.out.println("\n[" + (i + 1) + "] " + file.get("fileName").getAsString());
                System.out.println("    Criado por: " + file.get("userName").getAsString());
                System.out.println("    Criado em: " + formatDate(file.get("createdAt").getAsLong()));
                System.out.println("    Modificado em: " + formatDate(file.get("updatedAt").getAsLong()));
                System.out.println("    Tamanho: " + formatBytes(file.get("fileSize").getAsLong()));
            }
            System.out.println("=============================================================");
            
            System.out.print("\nEscolha o numero do arquivo para atualizar (1-" + filesArray.size() + "): ");
            Scanner scanner = new Scanner(System.in);
            int choice = scanner.nextInt();
            
            if (choice < 1 || choice > filesArray.size()) {
                System.out.println("Opcao invalida!");
                return;
            }
            
            selectedUserId = filesArray.get(choice - 1).getAsJsonObject().get("userId").getAsString();
        } else {
            selectedUserId = filesArray.get(0).getAsJsonObject().get("userId").getAsString();
        }

        byte[] fileContent = Files.readAllBytes(path);
        String base64Content = Base64.getEncoder().encodeToString(fileContent);

        JsonObject body = new JsonObject();
        body.addProperty("fileName", fileName);
        body.addProperty("content", base64Content);
        body.addProperty("userId", selectedUserId);

        System.out.println(
            "Atualizando arquivo: " +
                fileName +
                " (" +
                fileContent.length +
                " bytes)"
        );
        System.out.println("Aguardando replicacao em todos os servidores...");

        String response = sendPost(
            "/api/files/update",
            body.toString(),
            currentToken
        );
        JsonObject json = gson.fromJson(response, JsonObject.class);

        if (json.get("success").getAsBoolean()) {
            System.out.println(json.get("message").getAsString());
        } else {
            System.out.println("Erro: " + json.get("message").getAsString());
        }
    }

    private static void deleteFile(String fileName) throws Exception {
        if (currentToken == null) {
            System.out.println("Faca login primeiro!");
            return;
        }

        System.out.print("Tem certeza que deseja deletar '" + fileName + "'? (s/n): ");
        Scanner scanner = new Scanner(System.in);
        String confirm = scanner.nextLine().trim().toLowerCase();

        if (!confirm.equals("s") && !confirm.equals("sim")) {
            System.out.println("Operacao cancelada");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("fileName", fileName);

        String response = sendPost(
            "/api/files/delete",
            body.toString(),
            currentToken
        );
        JsonObject json = gson.fromJson(response, JsonObject.class);

        if (json.get("success").getAsBoolean()) {
            System.out.println(json.get("message").getAsString());
        } else {
            System.out.println("Erro: " + json.get("message").getAsString());
        }
    }

    private static void checkHealth() throws Exception {
        String response = sendGet("/health", null);
        System.out.println("Health: " + response);
    }

    private static String formatDate(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss"
        );
        return sdf.format(new java.util.Date(timestamp));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private static String sendPost(String endpoint, String body, String token)
        throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(GATEWAY_URL + endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() >= 400) {
            throw new IOException(
                "HTTP " + response.statusCode() + ": " + response.body()
            );
        }

        return response.body();
    }

    private static String sendGet(String endpoint, String token)
        throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(GATEWAY_URL + endpoint))
            .GET();

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() >= 400) {
            throw new IOException(
                "HTTP " + response.statusCode() + ": " + response.body()
            );
        }

        return response.body();
    }
}
