package com.autodeploy.domain.manager;

import com.autodeploy.domain.model.Server;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

public class ServerManager extends JsonFileManager<Server> {

    private static final String SERVERS_FILE = "servers.json";
    private static ServerManager instance;

    private ServerManager() {
        super(SERVERS_FILE, new TypeReference<List<Server>>() {}, "server");
    }

    public static synchronized ServerManager getInstance() {
        if (instance == null) {
            instance = new ServerManager();
        }
        return instance;
    }

    @Override
    protected boolean isValid(Server server) {
        return server.isValid();
    }

    @Override
    protected String getDisplayName(Server server) {
        return server.getName();
    }

    @Override
    protected void preserveId(Server oldServer, Server newServer) {
        newServer.setId(oldServer.getId());
    }

    public List<Server> getServers() { return getAll(); }
    public void addServer(Server server) { add(server); }
    public void updateServer(Server oldServer, Server newServer) { update(oldServer, newServer); }
    public void deleteServer(Server server) { delete(server); }
}