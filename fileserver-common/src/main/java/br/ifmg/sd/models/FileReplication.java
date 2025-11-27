package br.ifmg.sd.models;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Mensagem para replicação de arquivos via JGroups entre DataServers.
 */
public class FileReplication implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum OperationType {
        SAVE, DELETE
    }

    private final String userId;
    private final String fileName;
    private final byte[] content;
    private final OperationType operation;
    private final long timestamp;

    public FileReplication(String userId, String fileName, byte[] content, OperationType operation) {
        this.userId = userId;
        this.fileName = fileName;
        this.content = content;
        this.operation = operation;
        this.timestamp = System.currentTimeMillis();
    }

    public String getUserId() {
        return userId;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getContent() {
        return content;
    }

    public OperationType getOperation() {
        return operation;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "FileReplication{" +
                "userId='" + userId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", operation=" + operation +
                ", contentSize=" + (content != null ? content.length : 0) +
                ", timestamp=" + timestamp +
                '}';
    }
}
