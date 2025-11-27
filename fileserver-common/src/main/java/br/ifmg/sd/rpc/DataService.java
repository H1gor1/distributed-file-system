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
     * Lista arquivos de um usuário.
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
