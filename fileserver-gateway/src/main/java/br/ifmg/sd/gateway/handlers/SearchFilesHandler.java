package br.ifmg.sd.gateway.handlers;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.utilities.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.Map;
import org.jgroups.blocks.MethodCall;

public class SearchFilesHandler extends BaseHandler {

    public SearchFilesHandler(ClusterClient clusterClient) {
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

        if (fileName == null || fileName.isEmpty()) {
            HttpUtils.sendErrorResponse(exchange, 400, "fileName é obrigatório");
            return;
        }

        MethodCall call = new MethodCall(
            "searchFiles",
            new Object[] { token, fileName },
            new Class<?>[] { String.class, String.class }
        );

        @SuppressWarnings("unchecked")
        List<Object> files = clusterClient.callRemoteMethod(call, List.class);

        if (files != null) {
            StringBuilder json = new StringBuilder("{\"success\":true,\"files\":[");
            for (int i = 0; i < files.size(); i++) {
                if (i > 0) json.append(",");
                
                // Converter o objeto FileMetadata para JSON manualmente
                Object fileObj = files.get(i);
                java.lang.reflect.Method getUserId = fileObj.getClass().getMethod("getUserId");
                java.lang.reflect.Method getUserName = fileObj.getClass().getMethod("getUserName");
                java.lang.reflect.Method getFileName = fileObj.getClass().getMethod("getFileName");
                java.lang.reflect.Method getCreatedAt = fileObj.getClass().getMethod("getCreatedAt");
                java.lang.reflect.Method getUpdatedAt = fileObj.getClass().getMethod("getUpdatedAt");
                java.lang.reflect.Method getFileSize = fileObj.getClass().getMethod("getFileSize");

                json.append(String.format(
                    "{\"userId\":\"%s\",\"userName\":\"%s\",\"fileName\":\"%s\",\"createdAt\":%d,\"updatedAt\":%d,\"fileSize\":%d}",
                    getUserId.invoke(fileObj),
                    getUserName.invoke(fileObj),
                    getFileName.invoke(fileObj),
                    getCreatedAt.invoke(fileObj),
                    getUpdatedAt.invoke(fileObj),
                    getFileSize.invoke(fileObj)
                ));
            }
            json.append("]}");
            HttpUtils.sendJsonResponse(exchange, 200, json.toString());
        } else {
            HttpUtils.sendErrorResponse(exchange, 500, "Erro ao buscar arquivos");
        }
    }
}
