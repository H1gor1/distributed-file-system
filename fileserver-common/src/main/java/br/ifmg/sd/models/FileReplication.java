package br.ifmg.sd.models;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Mensagem para replicação de arquivos via JGroups entre DataServers.
 */
public class FileReplication implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum OperationType {
        SAVE,
        DELETE,
        EDIT,
    }

    private final String operationId;
    private final String userId;
    private final String userName;
    private final String fileName;
    private final byte[] content;
    private final OperationType operation;
    private final long timestamp;
    private final String diskPath;
    private final long createdAt;
    private final long updatedAt;

    public FileReplication(
        String userId,
        String userName,
        String fileName,
        byte[] content,
        OperationType operation,
        String diskPath,
        long createdAt,
        long updatedAt
    ) {
        this.operationId = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.userName = userName;
        this.fileName = fileName;
        this.content = content;
        this.operation = operation;
        this.timestamp = System.currentTimeMillis();
        this.diskPath = diskPath;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getUserName() {
        return userName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
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

    public String getDiskPath() {
        return diskPath;
    }

    @Override
    public String toString() {
        return (
            "FileReplication{" +
            "operationId='" +
            operationId +
            '\'' +
            ", userId='" +
            userId +
            '\'' +
            ", fileName='" +
            fileName +
            '\'' +
            ", operation=" +
            operation +
            ", contentSize=" +
            (content != null ? content.length : 0) +
            ", diskPath='" +
            diskPath +
            '\'' +
            ", timestamp=" +
            timestamp +
            '}'
        );
    }
}
