package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.utilities.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import java.util.Map;
import org.jgroups.blocks.MethodCall;

public class UploadFileHandler extends BaseHandler {

    public UploadFileHandler(ClusterClient clusterClient) {
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
        String contentBase64 = body.get("content");

        if (fileName == null || contentBase64 == null) {
            HttpUtils.sendErrorResponse(exchange, 400, "fileName e content são obrigatórios");
            return;
        }

        byte[] content = java.util.Base64.getDecoder().decode(contentBase64);

        MethodCall call = new MethodCall(
            "uploadFile",
            new Object[] { token, fileName, content },
            new Class<?>[] { String.class, String.class, byte[].class }
        );

        Boolean success = clusterClient.callRemoteMethod(call, Boolean.class);

        if (Boolean.TRUE.equals(success)) {
            String json = String.format(
                "{\"message\":\"Arquivo salvo com sucesso\",\"fileName\":\"%s\"}",
                fileName
            );
            HttpUtils.sendJsonResponse(exchange, 200, json);
        } else {
            HttpUtils.sendErrorResponse(exchange, 500, "Erro ao salvar arquivo");
        }
    }
}
