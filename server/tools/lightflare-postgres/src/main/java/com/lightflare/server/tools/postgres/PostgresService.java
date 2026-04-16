package com.lightflare.server.tools.postgres;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class PostgresService {

    private final PostgresProperties properties;

    public List<Map<String, Object>> query(String connectionName, String sql) throws SQLException {
        PostgresProperties.Connection config = getConnectionConfig(connectionName);
        
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(config.getConnectionUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    public List<String> listTables(String connectionName) throws SQLException {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'";
        List<Map<String, Object>> results = query(connectionName, sql);
        List<String> tables = new ArrayList<>();
        for (Map<String, Object> row : results) {
            tables.add((String) row.values().iterator().next());
        }
        return tables;
    }

    public List<Map<String, Object>> getTableColumns(String connectionName, String tableName) throws SQLException {
        String sql = String.format(
                "SELECT column_name, data_type, is_nullable, column_default " +
                "FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = '%s' " +
                "ORDER BY ordinal_position", tableName.replace("'", "''"));
        return query(connectionName, sql);
    }

    private PostgresProperties.Connection getConnectionConfig(String connectionName) {
        if (connectionName == null || connectionName.isBlank()) {
            if (properties.getConnections().size() == 1) {
                return properties.getConnections().values().iterator().next();
            }
            throw new IllegalArgumentException("connectionName is required because multiple postgres connections are configured.");
        }
        
        PostgresProperties.Connection config = properties.getConnections().get(connectionName);
        if (config == null) {
            throw new IllegalArgumentException("Unknown or unconfigured PostgreSQL connection: " + connectionName);
        }
        return config;
    }
}
