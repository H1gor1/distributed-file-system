package br.ifmg.sd.gateway.utilities;

import br.ifmg.sd.gateway.core.ClusterClient;
import br.ifmg.sd.gateway.handlers.HealthHandler;
import br.ifmg.sd.gateway.handlers.LoginHandler;
import br.ifmg.sd.gateway.handlers.LogoutHandler;
import br.ifmg.sd.gateway.handlers.RegisterHandler;
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
}
