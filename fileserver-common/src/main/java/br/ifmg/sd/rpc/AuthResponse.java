package br.ifmg.sd.rpc;

import java.io.Serializable;

public class AuthResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private boolean success;
    private String token;
    private String message;

    public AuthResponse() {}

    public AuthResponse(boolean success, String token, String message) {
        this.success = success;
        this.token = token;
        this.message = message;
    }

    public static AuthResponse success(String token) {
        return new AuthResponse(true, token, "Success");
    }

    public static AuthResponse error(String message) {
        return new AuthResponse(false, null, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
