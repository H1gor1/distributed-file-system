package br.ifmg.sd.models;

import java.io.Serializable;

public class FileMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String userName;
    private final String fileName;
    private final String diskPath;
    private final long createdAt;
    private final long updatedAt;
    private final long fileSize;

    public FileMetadata(
        String userId,
        String userName,
        String fileName,
        String diskPath,
        long createdAt,
        long updatedAt,
        long fileSize
    ) {
        this.userId = userId;
        this.userName = userName;
        this.fileName = fileName;
        this.diskPath = diskPath;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.fileSize = fileSize;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDiskPath() {
        return diskPath;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public long getFileSize() {
        return fileSize;
    }

    @Override
    public String toString() {
        return (
            "FileMetadata{" +
            "fileName='" +
            fileName +
            '\'' +
            ", userName='" +
            userName +
            '\'' +
            ", createdAt=" +
            createdAt +
            ", updatedAt=" +
            updatedAt +
            ", fileSize=" +
            fileSize +
            '}'
        );
    }
}
