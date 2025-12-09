package br.ifmg.sd.gateway.utilities;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.handlers.DeleteFileHandler;
import br.ifmg.sd.gateway.handlers.DownloadFileHandler;
import br.ifmg.sd.gateway.handlers.HealthHandler;
import br.ifmg.sd.gateway.handlers.ListFilesHandler;
import br.ifmg.sd.gateway.handlers.LoginHandler;
import br.ifmg.sd.gateway.handlers.LogoutHandler;
import br.ifmg.sd.gateway.handlers.RegisterHandler;
import br.ifmg.sd.gateway.handlers.SearchFilesHandler;
import br.ifmg.sd.gateway.handlers.UpdateFileHandler;
import br.ifmg.sd.gateway.handlers.UploadFileHandler;
import br.ifmg.sd.gateway.handlers.ValidateHandler;
import com.sun.net.httpserver.HttpHandler;
import java.util.List;
import java.util.function.Supplier;
import org.jgroups.Address;

public class HandlerFactory {

    private final ClusterClient clusterClient;
    private final Supplier<List<Address>> serverProvider;

    public HandlerFactory(
        ClusterClient clusterClient,
        Supplier<List<Address>> serverProvider
    ) {
        this.clusterClient = clusterClient;
        this.serverProvider = serverProvider;
    }

    public HttpHandler createRegisterHandler() {
        return new RegisterHandler(clusterClient);
    }

    public HttpHandler createLoginHandler() {
        return new LoginHandler(clusterClient);
    }

    public HttpHandler createLogoutHandler() {
        return new LogoutHandler(clusterClient);
    }

    public HttpHandler createValidateHandler() {
        return new ValidateHandler(clusterClient);
    }

    public HttpHandler createHealthHandler() {
        return new HealthHandler(serverProvider);
    }

    public HttpHandler createUploadFileHandler() {
        return new UploadFileHandler(clusterClient);
    }

    public HttpHandler createListFilesHandler() {
        return new ListFilesHandler(clusterClient);
    }

    public HttpHandler createSearchFilesHandler() {
        return new SearchFilesHandler(clusterClient);
    }

    public HttpHandler createDownloadFileHandler() {
        return new DownloadFileHandler(clusterClient);
    }

    public HttpHandler createUpdateFileHandler() {
        return new UpdateFileHandler(clusterClient);
    }

    public HttpHandler createDeleteFileHandler() {
        return new DeleteFileHandler(clusterClient);
    }
}
