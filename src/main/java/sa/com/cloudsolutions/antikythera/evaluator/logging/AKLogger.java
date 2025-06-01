package sa.com.cloudsolutions.antikythera.evaluator.logging;

import org.slf4j.helpers.MarkerIgnoringBase;

public class AKLogger extends MarkerIgnoringBase {
    public static final String STR_TRACE = "TRACE";
    public static final String STR_DEBUG = "DEBUG";
    public static final String STR_INFO = "INFO";
    public static final String STR_WARN = "WARN";
    public static final String STR_ERROR = "ERROR";
    Class<?> clazz;

    public AKLogger(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public String getName() {
        return clazz.getName();
    }

    @Override
    public boolean isTraceEnabled() { return true; }
    @Override
    public boolean isDebugEnabled() { return true; }
    @Override
    public boolean isInfoEnabled() { return true; }
    @Override
    public boolean isWarnEnabled() { return true; }
    @Override
    public boolean isErrorEnabled() { return true; }

    @Override
    public void trace(String msg) {
        LoggingEvaluator.captureLog(getName(), STR_TRACE, msg, null);
    }

    @Override
    public void trace(String format, Object arg) {
        LoggingEvaluator.captureLog(getName(), STR_TRACE, format, new Object[]{arg});
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        LoggingEvaluator.captureLog(getName(), STR_TRACE, format, new Object[]{arg1, arg2});
    }

    @Override
    public void trace(String format, Object... arguments) {
        LoggingEvaluator.captureLog(getName(), STR_TRACE, format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        throw new UnsupportedOperationException("Need to add this to LoggingEvaluator");
    }

    @Override
    public void debug(String msg) {
        LoggingEvaluator.captureLog(getName(), STR_DEBUG, msg, null);
    }

    @Override
    public void debug(String format, Object arg) {
        LoggingEvaluator.captureLog(getName(), STR_DEBUG, format, new Object[]{arg});
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        LoggingEvaluator.captureLog(getName(), STR_DEBUG, format, new Object[]{arg1, arg2});
    }

    @Override
    public void debug(String format, Object... arguments) {
        LoggingEvaluator.captureLog(getName(), STR_DEBUG, format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        throw new UnsupportedOperationException("Need to add this to LoggingEvaluator");
    }

    @Override
    public void info(String msg) {
        LoggingEvaluator.captureLog(getName(), STR_INFO, msg, null);
    }

    @Override
    public void info(String format, Object arg) {
        LoggingEvaluator.captureLog(getName(), STR_INFO, format, new Object[]{arg});
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        LoggingEvaluator.captureLog(getName(), STR_INFO, format, new Object[]{arg1, arg2});
    }

    @Override
    public void info(String format, Object... arguments) {
        LoggingEvaluator.captureLog(getName(), STR_INFO, format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        throw new UnsupportedOperationException("Need to add this to LoggingEvaluator");
    }

    @Override
    public void warn(String msg) {
        LoggingEvaluator.captureLog(getName(), STR_WARN, msg, null);
    }

    @Override
    public void warn(String format, Object arg) {
        LoggingEvaluator.captureLog(getName(), STR_WARN, format, new Object[]{arg});
    }

    @Override
    public void warn(String format, Object... arguments) {
        LoggingEvaluator.captureLog(getName(), STR_WARN, format, arguments);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        LoggingEvaluator.captureLog(getName(), STR_WARN, format, new Object[]{arg1, arg2});
    }

    @Override
    public void warn(String msg, Throwable t) {
        throw new UnsupportedOperationException("Need to add this to LoggingEvaluator");
    }

    @Override
    public void error(String msg) {
        LoggingEvaluator.captureLog(getName(), STR_ERROR, msg, null);
    }

    @Override
    public void error(String format, Object arg) {
        LoggingEvaluator.captureLog(getName(), STR_ERROR, format, new Object[]{arg});
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        LoggingEvaluator.captureLog(getName(), STR_ERROR, format, new Object[]{arg1, arg2});
    }

    @Override
    public void error(String format, Object... arguments) {
        LoggingEvaluator.captureLog(getName(), STR_ERROR, format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        throw new UnsupportedOperationException("Need to add this to LoggingEvaluator");
    }
}
