package com.ruoyi.web.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AmapConfigMigrationTest {
    private Connection connection;

    @Before
    public void createSchema() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:amap_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        execute("CREATE TABLE sys_config (config_id INT AUTO_INCREMENT PRIMARY KEY, "
                + "config_name VARCHAR(100), config_key VARCHAR(100), config_value VARCHAR(500), "
                + "config_type CHAR(1), create_by VARCHAR(64), create_time DATETIME, remark VARCHAR(500))");
    }

    @After
    public void closeConnection() throws Exception {
        connection.close();
    }

    @Test
    public void createsBothParametersAndCanRunAgain() throws Exception {
        migrate();
        migrate();
        assertEquals(2, count());
        assertValue("amap.key", "");
        assertValue("amap.security.key", "");
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT config_type, remark FROM sys_config")) {
            while (rows.next()) {
                assertEquals("Y", rows.getString("config_type"));
                assertTrue(rows.getString("remark").length() > 0);
            }
        }
    }

    @Test
    public void preservesConfiguredValuesAndAddsOnlyMissingParameter() throws Exception {
        execute("INSERT INTO sys_config(config_name, config_key, config_value, config_type, create_by) "
                + "VALUES('Existing map', 'amap.key', 'installation-key', 'N', 'operator')");
        migrate();
        assertEquals(2, count());
        assertValue("amap.key", "installation-key");
        assertValue("amap.security.key", "");
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT config_name, config_type, create_by "
                     + "FROM sys_config WHERE config_key = 'amap.key'")) {
            assertTrue(row.next());
            assertEquals("Existing map", row.getString("config_name"));
            assertEquals("N", row.getString("config_type"));
            assertEquals("operator", row.getString("create_by"));
        }
    }

    @Test
    public void preservesExistingEmptyAndNullValues() throws Exception {
        execute("INSERT INTO sys_config(config_key, config_value) VALUES "
                + "('amap.key', ''), ('amap.security.key', NULL)");
        migrate();
        assertEquals(2, count());
        assertValue("amap.key", "");
        assertValue("amap.security.key", null);
    }

    @Test
    public void allocatesIdsWithoutChangingOtherParameters() throws Exception {
        execute("INSERT INTO sys_config(config_id, config_key, config_value) VALUES "
                + "(12, 'other.first', 'first'), (13, 'other.second', 'second')");
        migrate();
        assertEquals(4, count());
        assertValue("other.first", "first");
        assertValue("other.second", "second");
        assertValue("amap.key", "");
        assertValue("amap.security.key", "");
    }

    private void migrate() throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get(
                "src/main/resources/db/migration/V1_2_17__seed_amap_configuration.sql")), StandardCharsets.UTF_8);
        for (String statement : sql.split(";")) {
            if (!statement.trim().isEmpty()) {
                execute(statement);
            }
        }
    }

    private void execute(String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count() throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT COUNT(*) FROM sys_config")) {
            row.next();
            return row.getInt(1);
        }
    }

    private void assertValue(String key, String expected) throws Exception {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT config_value FROM sys_config WHERE config_key = ?")) {
            statement.setString(1, key);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                assertEquals(expected, row.getString(1));
            }
        }
    }
}
