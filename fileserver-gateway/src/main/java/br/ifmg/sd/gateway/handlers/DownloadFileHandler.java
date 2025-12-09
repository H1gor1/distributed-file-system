package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.utilities.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import java.util.Base64;
import java.util.Map;
import org.jgroups.blocks.MethodCall;

public class DownloadFileHandler extends BaseHandler {

    public DownloadFileHandler(ClusterClient clusterClient) {
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

        Map<String, String> params = HttpUtils.parseQueryParams(
            exchange.getRequestURI().getQuery()
        );
        String fileName = params.get("fileName");
        String userId = params.get("userId");

        if (fileName == null || fileName.isEmpty()) {
            HttpUtils.sendErrorResponse(exchange, 400, "fileName é obrigatório");
            return;
        }

        MethodCall call = new MethodCall(
            "downloadFileWithUser",
            new Object[] { token, fileName, userId },
            new Class<?>[] { String.class, String.class, String.class }
        );

        byte[] content = clusterClient.callRemoteMethod(call, byte[].class);

        if (content != null) {
            String base64Content = Base64.getEncoder().encodeToString(content);
            String json = String.format(
                "{\"success\":true,\"content\":\"%s\",\"fileName\":\"%s\",\"size\":%d}",
                base64Content,
                fileName,
                content.length
            );
            HttpUtils.sendJsonResponse(exchange, 200, json);
        } else {
            HttpUtils.sendErrorResponse(exchange, 404, "Arquivo não encontrado");
        }
    }
}
