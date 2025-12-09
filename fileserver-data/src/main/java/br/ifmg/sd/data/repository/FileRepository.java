package br.ifmg.sd.data.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FileRepository {

    private final Connection connection;
    private final String serverStoragePath;

    public FileRepository(Connection connection, String serverName)
        throws IOException {
        this.connection = connection;
        this.serverStoragePath = serverName + "/uploads";

        Path storagePath = Paths.get(serverStoragePath);
        if (!Files.exists(storagePath)) {
            Files.createDirectories(storagePath);
            System.out.println("Pasta de uploads criada: " + serverStoragePath);
        }
    }

    public String save(String userId, String fileName, byte[] content)
        throws SQLException, IOException {
        String uniqueFileName = UUID.randomUUID().toString();
        String diskPath = serverStoragePath + "/" + uniqueFileName;

        Path filePath = Paths.get(diskPath);
        Files.write(
            filePath,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        String userName = getUserName(userId);
        long timestamp = System.currentTimeMillis();

        String sql = """
                INSERT INTO files (user_id, user_name, file_name, disk_path, created_at, updated_at, file_size)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id, file_name)
                DO UPDATE SET disk_path = excluded.disk_path, updated_at = excluded.updated_at, file_size = excluded.file_size
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, userName);
            pstmt.setString(3, fileName);
            pstmt.setString(4, diskPath);
            pstmt.setLong(5, timestamp);
            pstmt.setLong(6, timestamp);
            pstmt.setLong(7, content.length);
            pstmt.executeUpdate();
        }

        System.out.println("Arquivo salvo: " + fileName + " -> " + diskPath);
        return diskPath;
    }

    private String getUserName(String userId) throws SQLException {
        String sql = "SELECT name FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        }
        return "Unknown";
    }

    public void saveWithDiskPath(
        String userId,
        String userName,
        String fileName,
        byte[] content,
        String originalDiskPath,
        long createdAt,
        long updatedAt
    ) throws SQLException, IOException {
        String fileBaseName = Paths.get(originalDiskPath).getFileName().toString();
        String localDiskPath = serverStoragePath + "/" + fileBaseName;
        
        Path filePath = Paths.get(localDiskPath);
        Files.createDirectories(filePath.getParent());
        Files.write(
            filePath,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        String sql = """
                INSERT INTO files (user_id, user_name, file_name, disk_path, created_at, updated_at, file_size)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id, file_name)
                DO UPDATE SET disk_path = excluded.disk_path, updated_at = excluded.updated_at, file_size = excluded.file_size
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, userName);
            pstmt.setString(3, fileName);
            pstmt.setString(4, localDiskPath);
            pstmt.setLong(5, createdAt);
            pstmt.setLong(6, updatedAt);
            pstmt.setLong(7, content.length);
            pstmt.executeUpdate();
        }

        System.out.println(
            "Arquivo replicado: " + fileName + " -> " + localDiskPath
        );
    }

    public byte[] findByUserIdAndFileName(String userId, String fileName)
        throws SQLException, IOException {
        String sql =
            "SELECT disk_path FROM files WHERE user_id = ? AND file_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, fileName);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String diskPath = rs.getString("disk_path");
                Path filePath = Paths.get(diskPath);

                if (Files.exists(filePath)) {
                    return Files.readAllBytes(filePath);
                }
            }
        }
        return null;
    }

    public void delete(String userId, String fileName)
        throws SQLException, IOException {
        String sql =
            "SELECT disk_path FROM files WHERE user_id = ? AND file_name = ?";

        String diskPath = null;
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, fileName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                diskPath = rs.getString("disk_path");
            }
        }

        if (diskPath != null) {
            Path filePath = Paths.get(diskPath);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        }

        String deleteSql =
            "DELETE FROM files WHERE user_id = ? AND file_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(deleteSql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, fileName);
            pstmt.executeUpdate();
        }

        System.out.println("Arquivo deletado: " + fileName);
    }

    public List<String> findAllFileNames() throws SQLException {
        List<String> files = new ArrayList<>();
        String sql = "SELECT file_name FROM files";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                files.add(rs.getString("file_name"));
            }
        }

        return files;
    }

    public void editFile(String userId, String fileName, byte[] newContent)
        throws SQLException, IOException {
        String sql =
            "SELECT disk_path FROM files WHERE user_id = ? AND file_name = ?";

        String diskPath = null;
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, fileName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                diskPath = rs.getString("disk_path");
            }
        }

        if (diskPath == null) {
            throw new IOException("Arquivo não encontrado: " + fileName);
        }

        Path filePath = Paths.get(diskPath);
        Files.write(
            filePath,
            newContent,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        String updateSql = """
                UPDATE files SET created_at = ?
                WHERE user_id = ? AND file_name = ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(updateSql)) {
            pstmt.setLong(1, System.currentTimeMillis());
            pstmt.setString(2, userId);
            pstmt.setString(3, fileName);
            pstmt.executeUpdate();
        }

        System.out.println("Arquivo editado localmente: " + fileName);
    }

    public void edit(
        String userId,
        String fileName,
        byte[] content,
        String originalDiskPath
    ) throws SQLException, IOException {
        String fileBaseName = Paths.get(originalDiskPath).getFileName().toString();
        String localDiskPath = serverStoragePath + "/" + fileBaseName;
        
        Path filePath = Paths.get(localDiskPath);
        Files.createDirectories(filePath.getParent());
        Files.write(
            filePath,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        String sql = """
                UPDATE files SET disk_path = ?, created_at = ?
                WHERE user_id = ? AND file_name = ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, localDiskPath);
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.setString(3, userId);
            pstmt.setString(4, fileName);
            pstmt.executeUpdate();
        }

        System.out.println("Arquivo editado: " + fileName);
    }

    public List<br.ifmg.sd.models.FileMetadata> findByFileName(String fileName)
        throws SQLException {
        List<br.ifmg.sd.models.FileMetadata> results = new ArrayList<>();
        String sql = """
                SELECT user_id, user_name, file_name, disk_path, created_at, updated_at, file_size
                FROM files
                WHERE file_name = ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, fileName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                results.add(
                    new br.ifmg.sd.models.FileMetadata(
                        rs.getString("user_id"),
                        rs.getString("user_name"),
                        rs.getString("file_name"),
                        rs.getString("disk_path"),
                        rs.getLong("created_at"),
                        rs.getLong("updated_at"),
                        rs.getLong("file_size")
                    )
                );
            }
        }

        return results;
    }

    public br.ifmg.sd.models.FileMetadata getMetadata(String userId, String fileName)
        throws SQLException {
        String sql = """
                SELECT user_id, user_name, file_name, disk_path, created_at, updated_at, file_size
                FROM files
                WHERE user_id = ? AND file_name = ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, fileName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new br.ifmg.sd.models.FileMetadata(
                    rs.getString("user_id"),
                    rs.getString("user_name"),
                    rs.getString("file_name"),
                    rs.getString("disk_path"),
                    rs.getLong("created_at"),
                    rs.getLong("updated_at"),
                    rs.getLong("file_size")
                );
            }
        }

        return null;
    }

    public int size() throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM files";
        try (
            PreparedStatement pstmt = connection.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()
        ) {
            if (rs.next()) {
                return rs.getInt("count");
            }
        }
        return 0;
    }

    public List<br.ifmg.sd.models.FileMetadata> getAllMetadata() throws SQLException {
        List<br.ifmg.sd.models.FileMetadata> files = new ArrayList<>();
        String sql = """
                SELECT user_id, user_name, file_name, disk_path, created_at, updated_at, file_size
                FROM files
            """;

        try (
            PreparedStatement pstmt = connection.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()
        ) {
            while (rs.next()) {
                files.add(
                    new br.ifmg.sd.models.FileMetadata(
                        rs.getString("user_id"),
                        rs.getString("user_name"),
                        rs.getString("file_name"),
                        rs.getString("disk_path"),
                        rs.getLong("created_at"),
                        rs.getLong("updated_at"),
                        rs.getLong("file_size")
                    )
                );
            }
        }

        return files;
    }

    public String getUserId(br.ifmg.sd.models.File file) throws SQLException {
        String sql =
            "SELECT user_id FROM files WHERE file_name = ? AND disk_path = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, file.getName());
            pstmt.setString(2, file.getPath());

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("user_id");
            }
        }
        return null;
    }

    public String getDiskPath(String userId, String fileName)
        throws SQLException {
        String sql =
            "SELECT disk_path FROM files WHERE user_id = ? AND file_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, fileName);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("disk_path");
            }
        }
        return null;
    }
}
