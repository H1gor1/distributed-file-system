package br.ifmg.sd.models;

import java.io.Serializable;

public class SessionUpdate implements Serializable {

    private String token;
    private Session session;
    private UpdateType type;

    public enum UpdateType {
        CREATE,
        UPDATE,
        DELETE,
    }

    public SessionUpdate() {}

    public SessionUpdate(String token, Session session, UpdateType type) {
        this.token = token;
        this.session = session;
        this.type = type;
    }

    public String getToken() {
        return token;
    }

    public Session getSession() {
        return session;
    }

    public UpdateType getType() {
        return type;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public void setType(UpdateType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return (
            "SessionUpdate [token=" +
            token.substring(0, 10) +
            ", type=" +
            type +
            "]"
        );
    }
}
