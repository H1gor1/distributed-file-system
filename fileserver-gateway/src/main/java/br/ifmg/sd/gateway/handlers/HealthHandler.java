package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.utilities.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.jgroups.Address;

public class HealthHandler implements HttpHandler {

    private final Supplier<List<Address>> serverProvider;

    public HealthHandler(Supplier<List<Address>> serverProvider) {
        this.serverProvider = serverProvider;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String response = String.format(
            "{\"status\": \"UP\", \"cluster_members\": %d}",
            serverProvider.get().size()
        );
        HttpUtils.sendJsonResponse(exchange, 200, response);
    }
}
