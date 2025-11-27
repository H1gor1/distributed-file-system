package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.utilities.HttpUtils;
import br.ifmg.sd.rpc.AuthResponse;
import com.sun.net.httpserver.HttpExchange;
import java.util.Map;
import org.jgroups.blocks.MethodCall;

public class RegisterHandler extends BaseHandler {

    public RegisterHandler(ClusterClient clusterClient) {
        super(clusterClient);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws Exception {
        validateMethod(exchange, "POST");

        Map<String, String> body = HttpUtils.parseJsonBody(exchange);
        String username = body.get("username");
        String email = body.get("email");
        String password = body.get("password");

        if (username == null || email == null || password == null) {
            HttpUtils.sendErrorResponse(exchange, 400, "Missing fields");
            return;
        }

        MethodCall call = new MethodCall(
            "register",
            new Object[] { username, email, password },
            new Class[] { String.class, String.class, String.class }
        );

        AuthResponse response = clusterClient.callRemoteMethod(
            call,
            AuthResponse.class
        );

        if (response == null) {
            HttpUtils.sendErrorResponse(
                exchange,
                500,
                "No response from servers"
            );
            return;
        }

        if (response.isSuccess()) {
            HttpUtils.sendJsonResponse(
                exchange,
                200,
                String.format(
                    "{\"success\": true, \"token\": \"%s\"}",
                    response.getToken()
                )
            );
        } else {
            HttpUtils.sendJsonResponse(
                exchange,
                400,
                String.format(
                    "{\"success\": false, \"error\": \"%s\"}",
                    response.getMessage()
                )
            );
        }
    }
}
