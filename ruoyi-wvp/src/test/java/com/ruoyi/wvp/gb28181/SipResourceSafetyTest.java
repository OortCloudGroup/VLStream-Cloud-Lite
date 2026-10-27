package com.ruoyi.wvp.gb28181;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ruoyi.wvp.gb28181.conf.DefaultProperties;
import com.ruoyi.wvp.gb28181.conf.StackLoggerImpl;
import gov.nist.core.LogLevels;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import javax.sip.ListeningPoint;
import javax.sip.SipFactory;
import javax.sip.SipStack;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Properties;
import java.util.UUID;

import static org.junit.Assert.*;

public class SipResourceSafetyTest {
    private Logger logger;
    private Level beforeLevel;
    private boolean beforeAdditive;
    private ListAppender<ILoggingEvent> appender;

    @Before
    public void capture() {
        logger = (Logger) LoggerFactory.getLogger("gov.nist.javax.sip");
        beforeLevel = logger.getLevel();
        beforeAdditive = logger.isAdditive();
        logger.setLevel(Level.TRACE);
        logger.setAdditive(false);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @After
    public void restore() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(beforeLevel);
        logger.setAdditive(beforeAdditive);
    }

    private StackLoggerImpl adapter(String level) {
        StackLoggerImpl adapter = new StackLoggerImpl();
        Properties properties = new Properties();
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", level);
        adapter.setStackProperties(properties);
        return adapter;
    }

    @Test
    public void defaultSuppressesPacketDiagnosticsButPreservesErrors() {
        StackLoggerImpl adapter = new StackLoggerImpl();
        for (int i = 0; i < 10000; i++) adapter.logDebug("packet diagnostic");
        adapter.logTrace("trace");
        adapter.logInfo("packet contents");
        adapter.logWarning("warning");
        adapter.logError("error");
        assertEquals(2, appender.list.size());
        assertEquals(Level.WARN, appender.list.get(0).getLevel());
        assertEquals(Level.ERROR, appender.list.get(1).getLevel());
    }

    @Test
    public void debugAndTraceKeepTheirActualLevels() {
        StackLoggerImpl adapter = adapter("TRACE");
        adapter.logDebug("debug");
        adapter.logTrace("trace");
        adapter.logFatalError("fatal");
        assertEquals(Level.DEBUG, appender.list.get(0).getLevel());
        assertEquals(Level.TRACE, appender.list.get(1).getLevel());
        assertEquals(Level.ERROR, appender.list.get(2).getLevel());
    }

    @Test
    public void loggerThresholdStillApplies() {
        logger.setLevel(Level.INFO);
        StackLoggerImpl adapter = adapter("TRACE");
        assertFalse(adapter.isLoggingEnabled(LogLevels.TRACE_DEBUG));
        adapter.logDebug("debug");
        adapter.logTrace("trace");
        assertTrue(appender.list.isEmpty());
    }

    @Test
    public void disableAndEnableApplyToAllLoggingMethods() {
        StackLoggerImpl adapter = adapter("TRACE");
        adapter.disableLogging();
        adapter.logError("error", new Exception("test"));
        adapter.logDebug("debug", new Exception("test"));
        adapter.logException(new Exception("test"));
        adapter.logStackTrace();
        assertFalse(adapter.isLoggingEnabled());
        assertTrue(appender.list.isEmpty());
        adapter.enableLogging();
        adapter.logException(new Exception("test"));
        assertEquals(1, appender.list.size());
        assertNotNull(appender.list.get(0).getThrowableProxy());
    }

    @Test
    public void offNumericAndUnknownTraceSettingsAreSafe() {
        assertFalse(adapter("OFF").isLoggingEnabled());
        assertFalse(adapter("0").isLoggingEnabled());
        assertTrue(adapter("32").isLoggingEnabled(LogLevels.TRACE_DEBUG));
        assertFalse(adapter("bad-setting").isLoggingEnabled(LogLevels.TRACE_DEBUG));
        assertTrue(adapter("warning").isLoggingEnabled(LogLevels.TRACE_WARN));
        assertFalse(adapter("-1").isLoggingEnabled());
    }

    @Test
    public void defaultsBoundWorkersAndKeepCustomLoggerWithoutPacketLogs() {
        Properties properties = DefaultProperties.getProperties("test", false);
        assertEquals("16", properties.getProperty("gov.nist.javax.sip.THREAD_POOL_SIZE"));
        assertEquals("WARN", properties.getProperty("gov.nist.javax.sip.TRACE_LEVEL"));
        assertEquals("false", properties.getProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT"));
        assertEquals(StackLoggerImpl.class.getName(), properties.getProperty("gov.nist.javax.sip.STACK_LOGGER"));
        assertEquals("DEBUG", DefaultProperties.getProperties("test-enabled", true).getProperty("gov.nist.javax.sip.TRACE_LEVEL"));
    }

    @Test
    public void realJainSipUsesFixedWorkersDuringLoopbackDatagramBurst() throws Exception {
        String name = "resource-safety-" + UUID.randomUUID();
        SipFactory factory = SipFactory.getInstance();
        factory.setPathName("gov.nist");
        SipStack stack = factory.createSipStack(DefaultProperties.getProperties(name, false));
        int port;
        try (DatagramSocket reservation = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            port = reservation.getLocalPort();
        }
        try {
            stack.createSipProvider(stack.createListeningPoint("127.0.0.1", port, ListeningPoint.UDP));
            stack.start();
            long deadline = System.currentTimeMillis() + 3000;
            while (workerCount(name) < 16 && System.currentTimeMillis() < deadline) Thread.sleep(10);
            assertEquals(16, workerCount(name));
            byte[] heartbeat = "\r\n\r\n".getBytes("US-ASCII");
            try (DatagramSocket sender = new DatagramSocket()) {
                for (int i = 0; i < 512; i++) {
                    sender.send(new DatagramPacket(heartbeat, heartbeat.length, InetAddress.getLoopbackAddress(), port));
                }
            }
            Thread.sleep(100);
            assertEquals(16, workerCount(name));
        } finally {
            stack.stop();
        }
    }

    private long workerCount(String name) {
        return Thread.getAllStackTraces().keySet().stream()
            .filter(thread -> thread.isAlive() && thread.getName().startsWith(name + "-UDPMessageChannelThread-"))
            .count();
    }
}
