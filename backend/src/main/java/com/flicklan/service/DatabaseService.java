package com.flicklan.service;

import com.flicklan.model.Message;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    @Value("${app.data.dir:./data}")
    private String dataDir;

    private String dbUrl;

    @PostConstruct
    public void init() {
        try {
            File dir = new File(dataDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File dbFile = new File(dir, "flicklan.db");
            dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            try (Connection conn = getConnection()) {
                createTables(conn);
                logger.info("SQLite database initialized at {}", dbFile.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.error("Failed to initialize SQLite database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    private void createTables(Connection conn) throws SQLException {
        String createMessagesTable = """
            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                sender_name TEXT NOT NULL,
                recipient_id TEXT,
                channel TEXT,
                content TEXT,
                file_name TEXT,
                file_size INTEGER,
                file_download_url TEXT,
                timestamp TEXT NOT NULL
            );
            """;

        String createIndex = """
            CREATE INDEX IF NOT EXISTS idx_messages_channel ON messages(channel);
            CREATE INDEX IF NOT EXISTS idx_messages_recipients ON messages(sender_id, recipient_id);
            CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp);
            """;

        String createSettingsTable = """
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createMessagesTable);
            stmt.execute(createIndex);
            stmt.execute(createSettingsTable);
        }
    }

    public synchronized void saveMessage(Message msg) {
        String sql = """
            INSERT OR REPLACE INTO messages 
            (id, type, sender_id, sender_name, recipient_id, channel, content, file_name, file_size, file_download_url, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, msg.getId());
            pstmt.setString(2, msg.getType() != null ? msg.getType().name() : Message.Type.CHAT.name());
            pstmt.setString(3, msg.getSenderId());
            pstmt.setString(4, msg.getSenderName());
            pstmt.setString(5, msg.getRecipientId());
            pstmt.setString(6, msg.getChannel() != null ? msg.getChannel() : "general");
            pstmt.setString(7, msg.getContent());
            pstmt.setString(8, msg.getFileName());
            if (msg.getFileSize() != null) {
                pstmt.setLong(9, msg.getFileSize());
            } else {
                pstmt.setNull(9, Types.INTEGER);
            }
            pstmt.setString(10, msg.getFileDownloadUrl());
            pstmt.setString(11, msg.getTimestamp());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving message {}", msg.getId(), e);
        }
    }

    public List<Message> getChannelMessages(String channel, int limit) {
        String sql = """
            SELECT * FROM messages 
            WHERE channel = ? AND (recipient_id IS NULL OR recipient_id = '')
            ORDER BY timestamp DESC LIMIT ?;
            """;

        List<Message> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, channel);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToMessage(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving channel messages for {}", channel, e);
        }
        return list.reversed();
    }

    public List<Message> getDirectMessages(String user1, String user2, int limit) {
        String sql = """
            SELECT * FROM messages 
            WHERE (sender_id = ? AND recipient_id = ?) 
               OR (sender_id = ? AND recipient_id = ?)
            ORDER BY timestamp DESC LIMIT ?;
            """;

        List<Message> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);
            pstmt.setInt(5, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToMessage(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving direct messages between {} and {}", user1, user2, e);
        }
        return list.reversed();
    }

    public synchronized int deleteDirectMessages(String user1, String user2) {
        String sql = """
            DELETE FROM messages 
            WHERE (sender_id = ? AND recipient_id = ?) 
               OR (sender_id = ? AND recipient_id = ?);
            """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);
            int count = pstmt.executeUpdate();
            logger.info("Deleted {} direct messages between {} and {}", count, user1, user2);
            return count;
        } catch (SQLException e) {
            logger.error("Error deleting direct messages between {} and {}", user1, user2, e);
            return 0;
        }
    }

    public List<Message> getAllMessages() {
        String sql = "SELECT * FROM messages ORDER BY timestamp ASC;";
        List<Message> list = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToMessage(rs));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving all messages for backup", e);
        }
        return list;
    }

    public synchronized int mergeMessages(List<Message> messages) {
        String sql = """
            INSERT OR IGNORE INTO messages 
            (id, type, sender_id, sender_name, recipient_id, channel, content, file_name, file_size, file_download_url, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """;
        int inserted = 0;
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Message msg : messages) {
                    pstmt.setString(1, msg.getId());
                    pstmt.setString(2, msg.getType() != null ? msg.getType().name() : Message.Type.CHAT.name());
                    pstmt.setString(3, msg.getSenderId());
                    pstmt.setString(4, msg.getSenderName());
                    pstmt.setString(5, msg.getRecipientId());
                    pstmt.setString(6, msg.getChannel() != null ? msg.getChannel() : "general");
                    pstmt.setString(7, msg.getContent());
                    pstmt.setString(8, msg.getFileName());
                    if (msg.getFileSize() != null) {
                        pstmt.setLong(9, msg.getFileSize());
                    } else {
                        pstmt.setNull(9, Types.INTEGER);
                    }
                    pstmt.setString(10, msg.getFileDownloadUrl());
                    pstmt.setString(11, msg.getTimestamp());
                    pstmt.addBatch();
                }
                int[] results = pstmt.executeBatch();
                for (int res : results) {
                    if (res > 0 || res == Statement.SUCCESS_NO_INFO) {
                        inserted++;
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Error during batch message merge", e);
        }
        return inserted;
    }

    public synchronized int replaceAllMessages(List<Message> messages) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM messages;");
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("Failed to clear messages table before restore", e);
            return 0;
        }
        return mergeMessages(messages);
    }

    public int getMessageCount() {
        String sql = "SELECT COUNT(*) FROM messages;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Error getting message count", e);
        }
        return 0;
    }

    private Message mapResultSetToMessage(ResultSet rs) throws SQLException {
        Message msg = new Message();
        msg.setId(rs.getString("id"));
        try {
            msg.setType(Message.Type.valueOf(rs.getString("type")));
        } catch (Exception e) {
            msg.setType(Message.Type.CHAT);
        }
        msg.setSenderId(rs.getString("sender_id"));
        msg.setSenderName(rs.getString("sender_name"));
        msg.setRecipientId(rs.getString("recipient_id"));
        msg.setChannel(rs.getString("channel"));
        msg.setContent(rs.getString("content"));
        msg.setFileName(rs.getString("file_name"));
        long size = rs.getLong("file_size");
        if (!rs.wasNull()) {
            msg.setFileSize(size);
        }
        msg.setFileDownloadUrl(rs.getString("file_download_url"));
        msg.setTimestamp(rs.getString("timestamp"));
        return msg;
    }
}
