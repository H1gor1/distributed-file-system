package br.ifmg.sd.rpc;

import br.ifmg.sd.models.FileMetadata;
import java.util.List;

public interface ControlService {
    
    AuthResponse login(String username, String password) throws Exception;
    
    AuthResponse register(String username, String email, String password) throws Exception;
    
    boolean validateToken(String token) throws Exception;
    
    AuthResponse logout(String token) throws Exception;
    
    String getUserIdFromToken(String token) throws Exception;
    
    // Operações de arquivos
    boolean uploadFile(String token, String fileName, byte[] content) throws Exception;
    
    byte[] downloadFile(String token, String fileName) throws Exception;
    
    byte[] downloadFileWithUser(String token, String fileName, String targetUserId) throws Exception;
    
    boolean updateFile(String token, String fileName, byte[] newContent) throws Exception;
    
    boolean updateFileWithUser(String token, String fileName, byte[] newContent, String targetUserId) throws Exception;
    
    boolean deleteFile(String token, String fileName) throws Exception;
    
    List<FileMetadata> searchFiles(String token, String fileName) throws Exception;
    
    List<String> listMyFiles(String token) throws Exception;
}
