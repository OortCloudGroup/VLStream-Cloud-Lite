package com.ruoyi.system;

import com.ruoyi.system.domain.SysWork;
import com.ruoyi.system.mapper.SysWorkMapper;
import com.ruoyi.system.service.impl.SysWorkService;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SysWorkPersistenceTest {
    private SqlSession session() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Connection c = ds.getConnection()) {
            String migration = new String(Files.readAllBytes(Paths.get("../ruoyi-admin/src/main/resources/db/migration/V1_2_7__persist_workbench_layout.sql")), StandardCharsets.UTF_8);
            c.createStatement().execute(migration);
        }
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        config.addMapper(SysWorkMapper.class);
        return new SqlSessionFactoryBuilder().build(config).openSession(true);
    }
    private SysWork work(String layout) {
        SysWork work = new SysWork();
        work.setLayoutList(layout); work.setIndex("4");
        return work;
    }

    @Test public void longLayoutRoundTripsAndLegacyImportNeverOverwrites() throws Exception {
        try (SqlSession session = session()) {
            SysWorkMapper mapper = session.getMapper(SysWorkMapper.class);
            String longLayout = String.join("", java.util.Collections.nCopies(10000, "摄像机布局"));
            mapper.save(work(longLayout));
            mapper.importIfAbsent(work("stale cached layout"));
            assertEquals(longLayout, mapper.getLayout().getLayoutList());
            assertEquals("4", mapper.getLayout().getIndex());
            mapper.save(work("new user layout"));
            assertEquals("new user layout", mapper.getLayout().getLayoutList());
        }
    }

    @Test public void existingDatabaseLayoutDoesNotRequireRedis() throws Exception {
        try (SqlSession session = session()) {
            SysWorkMapper mapper = session.getMapper(SysWorkMapper.class);
            mapper.save(work("persisted"));
            RedisTemplate redis = mock(RedisTemplate.class);
            SysWorkService service = new SysWorkService();
            ReflectionTestUtils.setField(service, "mapper", mapper);
            ReflectionTestUtils.setField(service, "redisTemplate", redis);
            assertEquals("persisted", service.getLayout().getLayoutList());
            verifyNoInteractions(redis);
        }
    }

    @Test public void legacyJsonImportsOnceAndCannotOverwriteLaterSave() throws Exception {
        try (SqlSession session = session()) {
            SysWorkMapper mapper = session.getMapper(SysWorkMapper.class);
            RedisTemplate redis = mock(RedisTemplate.class);
            ValueOperations values = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(values);
            when(values.get(any())).thenReturn("{\"layoutList\":\"legacy\",\"index\":\"4\"}");
            SysWorkService service = new SysWorkService();
            ReflectionTestUtils.setField(service, "mapper", mapper);
            ReflectionTestUtils.setField(service, "redisTemplate", redis);
            assertEquals("legacy", service.getLayout().getLayoutList());
            service.save(work("explicit save"));
            assertEquals("explicit save", service.getLayout().getLayoutList());
            verify(values, times(1)).get(any());
        }
    }
}
