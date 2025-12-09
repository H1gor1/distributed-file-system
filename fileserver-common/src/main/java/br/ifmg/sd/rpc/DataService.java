package br.ifmg.sd.rpc;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface RMI para comunicação com o cluster de DataServers.
 * O coordenador do data-cluster implementa esta interface e registra no RMI Registry.
 */
public interface DataService extends Remote {

    /**
     * Salva um arquivo no cluster de dados (com replicação).
     */
    boolean saveFile(String userId, String fileName, byte[] content) throws RemoteException;

    /**
     * Lê um arquivo do cluster de dados.
     */
    byte[] readFile(String userId, String fileName) throws RemoteException;

    /**
     * Remove um arquivo do cluster de dados.
     */
    boolean deleteFile(String userId, String fileName) throws RemoteException;

    /**
     * Edita um arquivo existente no cluster de dados.
     */
    boolean editFile(String userId, String fileName, byte[] newContent) throws RemoteException;

    /**
     * Recupera um arquivo (retorna conteúdo e metadata).
     */
    byte[] recoverFile(String userId, String fileName) throws RemoteException;

    /**
     * Busca arquivos por nome (retorna lista de metadata).
     */
    List<br.ifmg.sd.models.FileMetadata> findFilesByName(String fileName) throws RemoteException;

    /**
     * Download de arquivo (com lock distribuído).
     */
    byte[] downloadFile(String userId, String fileName) throws RemoteException;

    /**
     * Registra um novo usuário.
     */
    boolean registerUser(String userId, String name, String password, String email) throws RemoteException;

    /**
     * Valida login de usuário.
     */
    br.ifmg.sd.models.User login(String email, String password) throws RemoteException;

    /**
     * Lista todos os arquivos (de todos os usuários).
     */
    List<String> listFiles(String userId) throws RemoteException;

    /**
     * Health check - verifica se o coordenador está ativo.
     */
    boolean ping() throws RemoteException;

    /**
     * Retorna número de membros no cluster de dados.
     */
    int getClusterSize() throws RemoteException;
}
