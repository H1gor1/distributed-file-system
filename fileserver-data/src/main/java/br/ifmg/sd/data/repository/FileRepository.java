package br.ifmg.sd.data.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FileRepository {

    private final Connection connection;

    public FileRepository(Connection connection) {
        this.connection = connection;
    }

    public void save(String userId, String fileName, byte[] content) throws SQLException {
        String sql = """
                INSERT INTO files (user_id, file_name, content, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(user_id, file_name)
                DO UPDATE SET content = excluded.content, created_at = excluded.created_at
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, fileName);
            pstmt.setBytes(3, content);
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.executeUpdate();
        }
    }

    public byte[] findByUserIdAndFileName(String userId, String fileName) throws SQLException {
        String sql = "SELECT content FROM files WHERE user_id = ? AND file_name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, fileName);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBytes("content");
            }
        }
        return null;
    }

    public void delete(String userId, String fileName) throws SQLException {
        String sql = "DELETE FROM files WHERE user_id = ? AND file_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, fileName);
            pstmt.executeUpdate();
        }
    }

    public List<String> findFileNamesByUserId(String userId) throws SQLException {
        List<String> files = new ArrayList<>();
        String sql = "SELECT file_name FROM files WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                files.add(rs.getString("file_name"));
            }
        }

        return files;
    }
}
