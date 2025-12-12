package br.ifmg.sd.models;

import java.io.Serializable;

public class UserReplication implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String operationId;
    private final String userId;
    private final String name;
    private final String password;
    private final String email;
    private final long createdAt;
    private final long timestamp;

    public UserReplication(
        String userId,
        String name,
        String password,
        String email,
        long createdAt
    ) {
        this.operationId = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.name = name;
        this.password = password;
        this.email = email;
        this.createdAt = createdAt;
        this.timestamp = System.currentTimeMillis();
    }

    public String getOperationId() {
        return operationId;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return (
            "UserReplication{" +
            "operationId='" + operationId + '\'' +
            ", userId='" + userId + '\'' +
            ", name='" + name + '\'' +
            ", email='" + email + '\'' +
            ", timestamp=" + timestamp +
            '}'
        );
    }
}
