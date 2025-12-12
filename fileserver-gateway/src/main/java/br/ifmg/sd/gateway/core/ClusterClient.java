package br.ifmg.sd.gateway.core;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.jgroups.Address;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;

public class ClusterClient {

    private static final int TIMEOUT_MS = 5000;
    private final Random random = new Random();

    private final RpcDispatcher dispatcher;
    private final Supplier<List<Address>> serverProvider;

    public ClusterClient(
        RpcDispatcher dispatcher,
        Supplier<List<Address>> serverProvider
    ) {
        this.dispatcher = dispatcher;
        this.serverProvider = serverProvider;
    }

    public <T> T callRemoteMethod(MethodCall call, Class<T> returnType)
        throws Exception {
        List<Address> servers = serverProvider.get();
        
        if (servers == null || servers.isEmpty()) {
            throw new Exception("No servers available");
        }

        // Selecionar um servidor aleatório
        Address selectedServer = servers.get(random.nextInt(servers.size()));
        
        RequestOptions opts = new RequestOptions(
            ResponseMode.GET_ALL,
            TIMEOUT_MS
        );
        
        RspList<T> responses = dispatcher.callRemoteMethods(
            List.of(selectedServer),
            call,
            opts
        );

        for (Rsp<T> rsp : responses) {
            if (
                rsp.wasReceived() &&
                !rsp.wasSuspected() &&
                rsp.getValue() != null
            ) {
                return rsp.getValue();
            }
        }
        return null;
    }
}
