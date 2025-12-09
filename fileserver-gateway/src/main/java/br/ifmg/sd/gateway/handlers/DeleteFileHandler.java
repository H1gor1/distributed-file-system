package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.utilities.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import java.util.Map;
import org.jgroups.blocks.MethodCall;

public class DeleteFileHandler extends BaseHandler {

    public DeleteFileHandler(ClusterClient clusterClient) {
        super(clusterClient);
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws Exception {
        validateMethod(exchange, "POST");

        String token = getAuthToken(exchange);
        if (token == null) {
            HttpUtils.sendErrorResponse(exchange, 401, "Token não fornecido");
            return;
        }

        Map<String, String> body = HttpUtils.parseJsonBody(exchange);
        String fileName = body.get("fileName");

        if (fileName == null || fileName.isEmpty()) {
            HttpUtils.sendErrorResponse(exchange, 400, "fileName é obrigatório");
            return;
        }

        MethodCall call = new MethodCall(
            "deleteFile",
            new Object[] { token, fileName },
            new Class<?>[] { String.class, String.class }
        );

        Boolean success = clusterClient.callRemoteMethod(call, Boolean.class);

        if (Boolean.TRUE.equals(success)) {
            String json = String.format(
                "{\"success\":true,\"message\":\"Arquivo deletado com sucesso\",\"fileName\":\"%s\"}",
                fileName
            );
            HttpUtils.sendJsonResponse(exchange, 200, json);
        } else {
            HttpUtils.sendErrorResponse(
                exchange,
                500,
                "Erro ao deletar arquivo"
            );
        }
    }
}
