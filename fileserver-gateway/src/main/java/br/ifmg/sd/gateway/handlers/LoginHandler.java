package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.utilities.HttpUtils;
import br.ifmg.sd.rpc.AuthResponse;
import com.sun.net.httpserver.HttpExchange;
import java.util.Map;
import org.jgroups.blocks.MethodCall;

public class LoginHandler extends BaseHandler {

    public LoginHandler(ClusterClient clusterClient) {
        super(clusterClient);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws Exception {
        validateMethod(exchange, "POST");

        Map<String, String> body = HttpUtils.parseJsonBody(exchange);
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            HttpUtils.sendErrorResponse(
                exchange,
                400,
                "Missing username or password"
            );
            return;
        }

        MethodCall call = new MethodCall(
            "login",
            new Object[] { username, password },
            new Class[] { String.class, String.class }
        );

        AuthResponse response = clusterClient.callRemoteMethod(
            call,
            AuthResponse.class
        );

        if (response != null && response.isSuccess()) {
            HttpUtils.sendJsonResponse(
                exchange,
                200,
                String.format(
                    "{\"success\": true, \"token\": \"%s\"}",
                    response.getToken()
                )
            );
        } else {
            HttpUtils.sendErrorResponse(exchange, 401, "Invalid credentials");
        }
    }
}
