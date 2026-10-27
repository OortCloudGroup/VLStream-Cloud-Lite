package com.ruoyi.wvp.gb28181.conf;

import gov.nist.core.LogLevels;
import gov.nist.core.StackLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Properties;

/** SIP diagnostics must respect levels and never turn per-packet DEBUG into INFO. */
@Component
public class StackLoggerImpl implements StackLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("gov.nist.javax.sip");
    private volatile boolean enabled = true;
    private volatile int traceLevel = LogLevels.TRACE_WARN;

    @Override
    public boolean isLoggingEnabled() { return enabled && traceLevel != LogLevels.TRACE_NONE; }

    @Override
    public boolean isLoggingEnabled(int level) {
        if (!isLoggingEnabled() || level <= LogLevels.TRACE_NONE || level > traceLevel) return false;
        if (level <= LogLevels.TRACE_ERROR) return LOGGER.isErrorEnabled();
        if (level <= LogLevels.TRACE_WARN) return LOGGER.isWarnEnabled();
        if (level <= LogLevels.TRACE_INFO) return LOGGER.isInfoEnabled();
        if (level <= LogLevels.TRACE_DEBUG) return LOGGER.isDebugEnabled();
        return LOGGER.isTraceEnabled();
    }

    @Override
    public void logDebug(String message) {
        if (isLoggingEnabled(LogLevels.TRACE_DEBUG)) LOGGER.debug(message);
    }

    @Override
    public void logDebug(String message, Exception exception) {
        if (isLoggingEnabled(LogLevels.TRACE_DEBUG)) LOGGER.debug(message, exception);
    }

    @Override
    public void logTrace(String message) {
        if (isLoggingEnabled(LogLevels.TRACE_TRACE)) LOGGER.trace(message);
    }

    @Override
    public void logInfo(String message) {
        if (isLoggingEnabled(LogLevels.TRACE_INFO)) LOGGER.info(message);
    }

    @Override
    public void logWarning(String message) {
        if (isLoggingEnabled(LogLevels.TRACE_WARN)) LOGGER.warn(message);
    }

    @Override
    public void logError(String message) {
        if (isLoggingEnabled(LogLevels.TRACE_ERROR)) LOGGER.error(message);
    }

    @Override
    public void logError(String message, Exception exception) {
        if (isLoggingEnabled(LogLevels.TRACE_ERROR)) LOGGER.error(message, exception);
    }

    @Override
    public void logFatalError(String message) {
        if (isLoggingEnabled(LogLevels.TRACE_FATAL)) LOGGER.error(message);
    }

    @Override
    public void logException(Throwable exception) {
        if (isLoggingEnabled(LogLevels.TRACE_ERROR)) LOGGER.error("SIP stack exception", exception);
    }

    @Override
    public void logStackTrace() { logStackTrace(LogLevels.TRACE_DEBUG); }

    @Override
    public void logStackTrace(int level) {
        if (isLoggingEnabled(level)) LOGGER.debug("SIP stack trace", new Exception("SIP diagnostic stack"));
    }

    @Override
    public void disableLogging() { enabled = false; }

    @Override
    public void enableLogging() { enabled = true; }

    @Override
    public void setStackProperties(Properties properties) {
        String value = properties.getProperty("gov.nist.javax.sip.TRACE_LEVEL", "WARN").trim().toUpperCase(Locale.ROOT);
        switch (value) {
            case "OFF": case "NONE": traceLevel = LogLevels.TRACE_NONE; break;
            case "FATAL": traceLevel = LogLevels.TRACE_FATAL; break;
            case "ERROR": traceLevel = LogLevels.TRACE_ERROR; break;
            case "WARNING": case "WARN": traceLevel = LogLevels.TRACE_WARN; break;
            case "INFO": traceLevel = LogLevels.TRACE_INFO; break;
            case "DEBUG": traceLevel = LogLevels.TRACE_DEBUG; break;
            case "TRACE": traceLevel = LogLevels.TRACE_TRACE; break;
            default:
                try { traceLevel = Math.max(LogLevels.TRACE_NONE, Integer.parseInt(value)); }
                catch (NumberFormatException ignored) { traceLevel = LogLevels.TRACE_WARN; }
        }
    }

    @Override
    public int getLineCount() { return 0; }

    @Override
    public void setBuildTimeStamp(String value) { }

    @Override
    public String getLoggerName() { return LOGGER.getName(); }
}
