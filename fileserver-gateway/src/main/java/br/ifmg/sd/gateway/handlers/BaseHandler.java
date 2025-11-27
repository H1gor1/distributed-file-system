package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.utilities.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

public abstract class BaseHandler implements HttpHandler {

    protected final ClusterClient clusterClient;

    protected BaseHandler(ClusterClient clusterClient) {
        this.clusterClient = clusterClient;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            handleRequest(exchange);
        } catch (IllegalArgumentException e) {
            // Já tratado
        } catch (Exception e) {
            HttpUtils.sendErrorResponse(exchange, 500, e.getMessage());
        }
    }

    protected abstract void handleRequest(HttpExchange exchange)
        throws Exception;

    protected void validateMethod(HttpExchange exchange, String expectedMethod)
        throws IOException {
        if (!expectedMethod.equals(exchange.getRequestMethod())) {
            HttpUtils.sendErrorResponse(exchange, 405, "Method not allowed");
            throw new IllegalArgumentException("Invalid method");
        }
    }

    protected String getAuthToken(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}
