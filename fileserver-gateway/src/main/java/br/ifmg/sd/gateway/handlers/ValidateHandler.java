package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.utilities.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import org.jgroups.blocks.MethodCall;

public class ValidateHandler extends BaseHandler {

    public ValidateHandler(ClusterClient clusterClient) {
        super(clusterClient);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws Exception {
        validateMethod(exchange, "GET");

        String token = getAuthToken(exchange);
        if (token == null || token.isEmpty()) {
            HttpUtils.sendErrorResponse(exchange, 400, "Missing token");
            return;
        }

        MethodCall call = new MethodCall(
            "validateToken",
            new Object[] { token },
            new Class[] { String.class }
        );

        Boolean valid = clusterClient.callRemoteMethod(call, Boolean.class);

        if (valid != null && valid) {
            HttpUtils.sendJsonResponse(exchange, 200, "{\"valid\": true}");
        } else {
            HttpUtils.sendJsonResponse(exchange, 401, "{\"valid\": false}");
        }
    }
}
