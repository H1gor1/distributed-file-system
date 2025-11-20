package br.ifmg.sd.models;

import java.io.Serializable;

public class Session implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private String userName;
    private String userEmail;
    private String token;
    private long createdAt;
    private long expiresAt;

    public Session() {}

    public Session(
        String userId,
        String userName,
        String userEmail,
        String token,
        long createdAt,
        long expiresAt
    ) {
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.token = token;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public boolean isValid() {
        return !isExpired();
    }

    public boolean isInvalid() {
        return !isValid();
    }

    @Override
    public String toString() {
        return (
            "Session{" +
            "userId='" +
            userId +
            '\'' +
            ", token='" +
            token +
            '\'' +
            ", createdAt=" +
            createdAt +
            ", expiresAt=" +
            expiresAt +
            '}'
        );
    }
}
