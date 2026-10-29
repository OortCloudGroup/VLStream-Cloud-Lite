package com.ruoyi.web.service;

import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Offline guards run normally; the genuine MySQL check requires an explicit operator opt-in. */
public class V14MysqlSyntaxProbeTest {
    @Test public void defaultConfigurationSkipsWithoutAConnection() {
        assertFalse(V14MysqlSyntaxProbe.enabled(key -> null, key -> null));
        assertFalse(V14MysqlSyntaxProbe.enabled(key -> "false", key -> null));
        assertTrue(V14MysqlSyntaxProbe.enabled(key -> null, key -> "true"));
        assertThrows(V14MysqlSyntaxProbe.ProbeFailure.class,
            () -> V14MysqlSyntaxProbe.enabled(key -> "typo", key -> null));
    }

    @Test public void targetCannotEscapeTheExplicitIsolatedFixtureOrInjectDriverOptions() {
        String target = "jdbc:mysql://192.168.60.77:32443/ry-wvp-local";
        V14MysqlSyntaxProbe.validateUrl(target);
        V14MysqlSyntaxProbe.validateUrl(target + "?useSSL=false&serverTimezone=Asia%2FShanghai&characterEncoding=utf8");
        for (String refused : new String[]{null, " " + target, target + "/", target.replace("ry-wvp-local", "ry-wvp"),
            target.replace("192.168.60.77", "127.0.0.1"), target.replace("32443", "3306"),
            target.replace("//192", "//user:password@192"), target + "#fragment", target + "?user=identity",
            target + "?password=secret", target + "?allowMultiQueries=true", target + "?sessionVariables=sql_mode=ANSI",
            target + "?logger=malicious", target + "?useSSL=false&USEssl=true", target + "?useSSL=false;allowMultiQueries=true"}) {
            assertThrows(V14MysqlSyntaxProbe.ProbeFailure.class, () -> V14MysqlSyntaxProbe.validateUrl(refused));
        }
    }

    @Test public void sourceIsPinnedAndBothOriginalFailuresRestoreOneExtraRightParenthesis() {
        List<String> statements = V14MysqlSyntaxProbe.readMenuInserts(migration());
        assertEquals(2, statements.size());
        for (String statement : statements) {
            String original = V14MysqlSyntaxProbe.originalExtraParenthesis(statement);
            assertEquals(statement.replace("\r\n", "\n").length() + 1, original.length());
            assertTrue(original.contains(")\nWHERE NOT EXISTS"));
        }
    }

    @Test public void changedSourceFailsBeforeAnyConnectionCanBeOpened() throws Exception {
        Path directory = Files.createTempDirectory("v14-invalid-source-");
        Path source = directory.resolve("V1_2_14__vlstream_device_sharing.sql");
        try {
            Files.write(source, "INSERT INTO sys_menu VALUES ('unreviewed');".getBytes(StandardCharsets.UTF_8));
            assertThrows(V14MysqlSyntaxProbe.ProbeFailure.class, () -> V14MysqlSyntaxProbe.readMenuInserts(source));
        } finally { Files.deleteIfExists(source); Files.deleteIfExists(directory); }
    }

    @Test public void offlineJdbcHarnessAllowsOnlySelectAndOrdinaryExplainAndChecksCountsAndDigests() throws Exception {
        List<String> queries = new ArrayList<>();
        V14MysqlSyntaxProbe.Report result = V14MysqlSyntaxProbe.probe(fakeConnection(queries, false, false),
            V14MysqlSyntaxProbe.readMenuInserts(migration()));
        assertEquals(17, queries.size());
        assertEquals(4, queries.stream().filter(sql -> sql.startsWith("EXPLAIN INSERT INTO `sys_menu` ")).count());
        assertTrue(queries.stream().allMatch(sql -> sql.startsWith("SELECT ") || sql.startsWith("EXPLAIN INSERT INTO ")));
        assertTrue(queries.stream().noneMatch(sql -> sql.contains("EXPLAIN ANALYZE") || sql.startsWith("INSERT ")));
        assertTrue(result.summary().contains("permission_role_grant_counts_and_digests_unchanged=true"));
        assertFalse(result.summary().contains("fixture-row"));
    }

    @Test public void wrongServerUuidIsRejectedBeforeAnyPermissionRowsAreRead() throws Exception {
        List<String> queries = new ArrayList<>();
        assertThrows(V14MysqlSyntaxProbe.ProbeFailure.class,
            () -> V14MysqlSyntaxProbe.probe(fakeConnection(queries, true, false), V14MysqlSyntaxProbe.readMenuInserts(migration())));
        assertEquals(1, queries.size());
        assertEquals("SELECT DATABASE(), @@server_uuid", queries.get(0));
    }

    @Test public void anyPermissionRowMutationIsDetectedEvenWhenCountsStayEqual() throws Exception {
        assertThrows(V14MysqlSyntaxProbe.ProbeFailure.class,
            () -> V14MysqlSyntaxProbe.probe(fakeConnection(new ArrayList<>(), false, true), V14MysqlSyntaxProbe.readMenuInserts(migration())));
    }

    @Test public void genuineMysqlSyntaxRegressionIsExplicitlyOptIn() {
        Assume.assumeTrue("Real MySQL syntax test is opt-in; no connection was attempted",
            V14MysqlSyntaxProbe.enabled(System::getProperty, System::getenv));
        V14MysqlSyntaxProbe.Report report = V14MysqlSyntaxProbe.run(V14MysqlSyntaxProbe.options(System::getProperty, System::getenv));
        assertTrue(report.summary().startsWith("PASS: corrected_EXPLAIN=2; original_1064=2;"));
        System.out.println(report.summary());
    }

    private static Path migration() {
        Path module = Paths.get("src/main/resources/db/migration/V1_2_14__vlstream_device_sharing.sql");
        return Files.isRegularFile(module) ? module : Paths.get("ruoyi-admin").resolve(module);
    }

    private static Connection fakeConnection(List<String> queries, boolean wrongUuid, boolean mutate) throws SQLException {
        Connection connection = mock(Connection.class);
        Map<String, Integer> scans = new HashMap<>();
        when(connection.createStatement()).thenAnswer(invocation -> {
            Statement statement = mock(Statement.class);
            when(statement.executeQuery(anyString())).thenAnswer(query -> {
                String sql = query.getArgument(0);
                queries.add(sql);
                if (sql.equals("SELECT DATABASE(), @@server_uuid")) return rows(new String[][]{{V14MysqlSyntaxProbe.SCHEMA,
                    wrongUuid ? "wrong-server" : V14MysqlSyntaxProbe.SERVER_UUID}});
                if (sql.startsWith("SELECT * FROM `")) {
                    int scan = scans.getOrDefault(sql, 0) + 1;
                    scans.put(sql, scan);
                    return rows(new String[][]{{"1", mutate && scan > 1 ? "changed-fixture-row" : "fixture-row"}});
                }
                if (sql.startsWith("EXPLAIN INSERT INTO `sys_menu` ")) {
                    if (sql.contains(")\nWHERE NOT EXISTS")) throw new SQLException("fixture syntax error", "42000", 1064);
                    return rows(new String[0][0]);
                }
                throw new AssertionError("An unapproved SQL category was requested by the probe.");
            });
            return statement;
        });
        return connection;
    }

    private static ResultSet rows(String[][] values) throws SQLException {
        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnLabel(anyInt())).thenAnswer(invocation -> "column" + invocation.getArgument(0));
        when(result.getMetaData()).thenReturn(metadata);
        AtomicInteger row = new AtomicInteger(-1);
        when(result.next()).thenAnswer(invocation -> row.incrementAndGet() < values.length);
        when(result.getString(anyInt())).thenAnswer(invocation -> values[row.get()][(Integer) invocation.getArgument(0) - 1]);
        when(result.getBytes(anyInt())).thenAnswer(invocation -> values[row.get()][(Integer) invocation.getArgument(0) - 1].getBytes(StandardCharsets.UTF_8));
        return result;
    }
}
