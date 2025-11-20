package br.ifmg.sd.rpc;

public interface ControlService {
    
    AuthResponse login(String username, String password) throws Exception;
    
    AuthResponse register(String username, String email, String password) throws Exception;
    
    boolean validateToken(String token) throws Exception;
    
    AuthResponse logout(String token) throws Exception;
    
    String getUserIdFromToken(String token) throws Exception;
}
