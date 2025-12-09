package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.utilities.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import org.jgroups.blocks.MethodCall;

public class ListFilesHandler extends BaseHandler {

    public ListFilesHandler(ClusterClient clusterClient) {
        super(clusterClient);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws Exception {
        validateMethod(exchange, "GET");

        String token = getAuthToken(exchange);
        if (token == null) {
            HttpUtils.sendErrorResponse(exchange, 401, "Token não fornecido");
            return;
        }

        MethodCall call = new MethodCall(
            "listUserFiles",
            new Object[] { token },
            new Class<?>[] { String.class }
        );

        @SuppressWarnings("unchecked")
        List<String> files = clusterClient.callRemoteMethod(call, List.class);

        if (files != null) {
            StringBuilder json = new StringBuilder("{\"files\":[");
            for (int i = 0; i < files.size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(files.get(i)).append("\"");
            }
            json.append("]}");
            HttpUtils.sendJsonResponse(exchange, 200, json.toString());
        } else {
            HttpUtils.sendJsonResponse(exchange, 200, "{\"files\":[]}");
        }
    }
}
