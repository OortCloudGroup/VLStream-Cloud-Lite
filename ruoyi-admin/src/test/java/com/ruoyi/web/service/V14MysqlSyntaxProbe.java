package com.ruoyi.web.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Opt-in MySQL parser probe. Never executes the migration, INSERT, DDL, GRANT or EXPLAIN ANALYZE. */
public final class V14MysqlSyntaxProbe {
    static final String SCHEMA = "ry-wvp-local";
    static final String SERVER_UUID = "c89187b7-f09e-11ee-8e2b-1a041c82250c";
    static final String MIGRATION_SHA256 = "06474458446648A20FDFDE52741F46708DA949EFECB127F5F984D510B2434A72";
    private static final String MIGRATION_NAME = "V1_2_14__vlstream_device_sharing.sql";
    private static final Pattern MENU_INSERT = Pattern.compile(
        "(?m)^INSERT INTO `sys_menu` \\([^\\r\\n]*\\)\\r?\\nSELECT [^\\r\\n]*\\r?\\nWHERE NOT EXISTS \\([^\\r\\n]*\\);[ \\t]*(?=\\r?$)");
    private static final Map<String, String> SNAPSHOT_TABLES = new LinkedHashMap<>();
    static {
        SNAPSHOT_TABLES.put("sys_menu", "menu_id");
        SNAPSHOT_TABLES.put("sys_role", "role_id");
        SNAPSHOT_TABLES.put("sys_role_menu", "role_id,menu_id");
        SNAPSHOT_TABLES.put("sys_user_role", "user_id,role_id");
        SNAPSHOT_TABLES.put("wvp_vlstream_access_grant", "grant_id");
        SNAPSHOT_TABLES.put("wvp_vlstream_access_request", "request_id");
    }

    private V14MysqlSyntaxProbe() { }

    public static void main(String[] args) {
        try {
            if (!enabled(System::getProperty, System::getenv)) {
                System.out.println("SKIPPED: V14 MySQL syntax probe is not explicitly enabled; no connection attempted.");
                return;
            }
            System.out.println(run(options(System::getProperty, System::getenv)).summary());
        } catch (ProbeFailure failure) {
            System.err.println("FAILED: " + failure.getMessage());
            System.exit(1);
        } catch (Exception unexpected) {
            // Driver/configuration errors can contain credentials: never print their message or cause.
            System.err.println("FAILED: V14 MySQL syntax probe failed without completing verification.");
            System.exit(1);
        }
    }

    static boolean enabled(Function<String, String> properties, Function<String, String> environment) {
        String value = setting(properties, environment, "enabled");
        if (value == null || "false".equalsIgnoreCase(value)) return false;
        if (!"true".equalsIgnoreCase(value)) throw refused();
        return true;
    }

    static Options options(Function<String, String> properties, Function<String, String> environment) {
        String url = setting(properties, environment, "url");
        validateUrl(url);
        String user = setting(properties, environment, "user");
        String password = setting(properties, environment, "password");
        if (user == null || user.trim().isEmpty() || password == null || password.isEmpty()) throw refused();
        String configuredPath = setting(properties, environment, "migration");
        Path migration;
        if (configuredPath != null && !configuredPath.isEmpty()) migration = Paths.get(configuredPath);
        else {
            migration = Paths.get("ruoyi-admin/src/main/resources/db/migration", MIGRATION_NAME);
            if (!Files.isRegularFile(migration)) migration = Paths.get("src/main/resources/db/migration", MIGRATION_NAME);
        }
        return new Options(url, user, password, migration);
    }

    private static String setting(Function<String, String> properties, Function<String, String> environment, String name) {
        String value = properties.apply("wvp.v14.mysql." + name);
        return value != null ? value : environment.apply("WVP_V14_MYSQL_" + name.toUpperCase(Locale.ROOT));
    }

    static void validateUrl(String url) {
        try {
            if (url == null || !url.startsWith("jdbc:mysql://") || !url.equals(url.trim())) throw refused();
            URI target = URI.create(url.substring("jdbc:".length()));
            if (!"mysql".equals(target.getScheme()) || !"192.168.60.77".equals(target.getHost())
                || target.getPort() != 32443 || !"192.168.60.77:32443".equals(target.getRawAuthority())
                || !("/" + SCHEMA).equals(target.getRawPath()) || target.getRawUserInfo() != null
                || target.getRawFragment() != null) throw refused();
            String query = target.getRawQuery();
            if (query == null) return;
            Set<String> seen = new HashSet<>();
            for (String item : query.split("&", -1)) {
                String[] pair = item.split("=", -1);
                if (pair.length != 2) throw refused();
                String key = URLDecoder.decode(pair[0], "UTF-8");
                String value = URLDecoder.decode(pair[1], "UTF-8");
                if (!seen.add(key.toLowerCase(Locale.ROOT))) throw refused();
                if (Arrays.asList("useSSL", "requireSSL", "verifyServerCertificate", "allowPublicKeyRetrieval", "useUnicode").contains(key)) {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) throw refused();
                } else if ("characterEncoding".equals(key)) {
                    if (!Arrays.asList("UTF-8", "utf8", "utf-8").contains(value)) throw refused();
                } else if ("serverTimezone".equals(key)) {
                    if (!Arrays.asList("UTC", "Asia/Shanghai", "GMT+8", "GMT+08:00").contains(value)) throw refused();
                } else if ("sslMode".equals(key)) {
                    if (!Arrays.asList("DISABLED", "PREFERRED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY").contains(value)) throw refused();
                } else throw refused();
            }
        } catch (ProbeFailure failure) {
            throw failure;
        } catch (Exception invalid) {
            throw refused();
        }
    }

    static List<String> readMenuInserts(Path migration) {
        try {
            byte[] bytes = Files.readAllBytes(migration);
            String source = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
            byte[] canonicalBytes = source.getBytes(StandardCharsets.UTF_8);
            if (!MIGRATION_NAME.equals(migration.getFileName().toString()) || !MIGRATION_SHA256.equals(hex(sha256().digest(canonicalBytes)))) {
                throw new ProbeFailure("Migration source name or SHA-256 differs from the reviewed corrected V14.");
            }
            List<String> statements = new ArrayList<>();
            Matcher matcher = MENU_INSERT.matcher(source);
            while (matcher.find()) statements.add(matcher.group().trim());
            if (statements.size() != 2 || !statements.get(0).contains("'vlstream:device:share:manage'")
                || !statements.get(1).contains("'vlstream:device:share:approve'")) {
                throw new ProbeFailure("Exactly two reviewed sys_menu INSERT statements are required.");
            }
            return statements;
        } catch (IOException inaccessible) {
            throw new ProbeFailure("Reviewed V14 source could not be read.");
        }
    }

    static String originalExtraParenthesis(String corrected) {
        String normalized = corrected.replace("\r\n", "\n");
        int where = normalized.indexOf("\nWHERE NOT EXISTS ");
        if (where < 0 || where != normalized.lastIndexOf("\nWHERE NOT EXISTS ")) throw refused();
        return normalized.substring(0, where) + ")" + normalized.substring(where);
    }

    static Report run(Options options) {
        List<String> inserts = readMenuInserts(options.migration);
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", options.user);
        connectionProperties.setProperty("password", options.password);
        connectionProperties.setProperty("allowMultiQueries", "false");
        connectionProperties.setProperty("autoReconnect", "false");
        connectionProperties.setProperty("allowLoadLocalInfile", "false");
        connectionProperties.setProperty("connectTimeout", "5000");
        connectionProperties.setProperty("socketTimeout", "10000");
        try (Connection connection = DriverManager.getConnection(options.url, connectionProperties)) {
            return probe(connection, inserts);
        } catch (SQLException failure) {
            throw sqlFailure(failure);
        }
    }

    static Report probe(Connection connection, List<String> inserts) {
        try {
            // No READ ONLY transaction: MySQL refuses EXPLAIN INSERT in it with error 1792.
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                try (ResultSet identity = statement.executeQuery("SELECT DATABASE(), @@server_uuid")) {
                    if (!identity.next() || !SCHEMA.equals(identity.getString(1)) || !SERVER_UUID.equalsIgnoreCase(identity.getString(2))) {
                        throw new ProbeFailure("Connection database or server UUID does not match the isolated fixture.");
                    }
                }
            }
            Map<String, String> before = snapshot(connection);
            ProbeFailure assertion = null;
            try {
                for (String sql : inserts) {
                    explain(connection, sql, false);
                    explain(connection, originalExtraParenthesis(sql), true);
                }
            } catch (ProbeFailure failed) {
                assertion = failed;
            } finally {
                if (!before.equals(snapshot(connection))) throw new ProbeFailure("Permission, role or sharing rows changed during the probe.");
            }
            if (assertion != null) throw assertion;
            return new Report(before);
        } catch (SQLException failure) {
            throw sqlFailure(failure);
        }
    }

    private static void explain(Connection connection, String insert, boolean mustBe1064) throws SQLException {
        if (!insert.startsWith("INSERT INTO `sys_menu` ") || !insert.endsWith(";") || insert.substring(0, insert.length() - 1).contains(";")) throw refused();
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (ResultSet plan = statement.executeQuery("EXPLAIN " + insert.substring(0, insert.length() - 1))) {
                while (plan.next()) { /* Ordinary optimizer output only; never ANALYZE or execute the INSERT. */ }
            }
            if (mustBe1064) throw new ProbeFailure("Original extra-parenthesis SQL unexpectedly passed MySQL parsing.");
        } catch (SQLException failure) {
            if (mustBe1064 && failure.getErrorCode() == 1064) return;
            throw sqlFailure(failure);
        }
    }

    private static Map<String, String> snapshot(Connection connection) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> table : SNAPSHOT_TABLES.entrySet()) {
            MessageDigest digest = sha256();
            long count = 0;
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                try (ResultSet rows = statement.executeQuery("SELECT * FROM `" + table.getKey() + "` ORDER BY " + table.getValue())) {
                    ResultSetMetaData metadata = rows.getMetaData();
                    int columns = metadata.getColumnCount();
                    for (int i = 1; i <= columns; i++) digest.update(metadata.getColumnLabel(i).getBytes(StandardCharsets.UTF_8));
                    while (rows.next()) {
                        count++;
                        for (int i = 1; i <= columns; i++) {
                            byte[] value = rows.getBytes(i);
                            digest.update((byte) (value == null ? 0 : 1));
                            if (value != null) {
                                digest.update(ByteBuffer.allocate(4).putInt(value.length).array());
                                digest.update(value);
                            }
                        }
                    }
                }
                result.put(table.getKey(), count + ":" + hex(digest.digest()));
            } catch (SQLException missing) {
                if (missing.getErrorCode() == 1146 && table.getKey().startsWith("wvp_vlstream_access_")) result.put(table.getKey(), "ABSENT");
                else throw missing;
            }
        }
        return result;
    }

    private static ProbeFailure sqlFailure(SQLException failure) {
        // SQLState and numeric vendor code are sufficient evidence without leaking driver/server text.
        String state = failure.getSQLState();
        if (state == null || !state.matches("[A-Za-z0-9]{5}")) state = "unknown";
        return new ProbeFailure("MySQL verification failed (SQLState=" + state + ", vendorCode=" + failure.getErrorCode() + ").");
    }

    private static ProbeFailure refused() { return new ProbeFailure("V14 MySQL syntax probe configuration or SQL was refused."); }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02X", value & 0xff));
        return result.toString();
    }

    static final class Options {
        private final String url;
        private final String user;
        private final String password;
        private final Path migration;
        private Options(String url, String user, String password, Path migration) {
            this.url = url; this.user = user; this.password = password; this.migration = migration;
        }
    }

    static final class Report {
        private final Map<String, String> snapshots;
        private Report(Map<String, String> snapshots) { this.snapshots = snapshots; }
        String summary() {
            return "PASS: corrected_EXPLAIN=2; original_1064=2; server_uuid_verified=true; source_sha256=" + MIGRATION_SHA256
                + "; permission_role_grant_counts_and_digests_unchanged=true; snapshot=" + snapshots;
        }
    }

    static final class ProbeFailure extends RuntimeException {
        private ProbeFailure(String message) { super(message); }
    }
}
