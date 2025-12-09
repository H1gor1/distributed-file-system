package br.ifmg.sd.gateway.utilities;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpUtils {

    public static Map<String, String> parseJsonBody(HttpExchange exchange)
        throws IOException {
        String body = new String(
            exchange.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );
        Map<String, String> result = new HashMap<>();

        body = body.trim().replace("{", "").replace("}", "");
        for (String pair : body.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                result.put(key, value);
            }
        }
        return result;
    }

    public static void sendJsonResponse(
        HttpExchange exchange,
        int statusCode,
        String json
    ) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendErrorResponse(
        HttpExchange exchange,
        int statusCode,
        String message
    ) throws IOException {
        String json = String.format("{\"error\": \"%s\"}", message);
        sendJsonResponse(exchange, statusCode, json);
    }

    public static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2) {
                try {
                    String key = java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                    String value = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception e) {
                    // Ignora parâmetros malformados
                }
            }
        }
        return params;
    }
}
