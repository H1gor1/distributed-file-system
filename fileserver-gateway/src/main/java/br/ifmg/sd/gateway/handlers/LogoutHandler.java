package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.utilities.HttpUtils;
import br.ifmg.sd.rpc.AuthResponse;
import com.sun.net.httpserver.HttpExchange;
import org.jgroups.blocks.MethodCall;

public class LogoutHandler extends BaseHandler {

    public LogoutHandler(ClusterClient clusterClient) {
        super(clusterClient);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws Exception {
        validateMethod(exchange, "POST");

        String token = getAuthToken(exchange);
        if (token == null || token.isEmpty()) {
            HttpUtils.sendErrorResponse(exchange, 400, "Missing token");
            return;
        }

        MethodCall call = new MethodCall(
            "logout",
            new Object[] { token },
            new Class[] { String.class }
        );

        AuthResponse response = clusterClient.callRemoteMethod(
            call,
            AuthResponse.class
        );

        if (response != null && response.isSuccess()) {
            HttpUtils.sendJsonResponse(exchange, 200, "{\"success\": true}");
        } else {
            HttpUtils.sendErrorResponse(exchange, 500, "Logout failed");
        }
    }
}
