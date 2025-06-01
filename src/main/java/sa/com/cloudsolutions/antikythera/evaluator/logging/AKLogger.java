package sa.com.cloudsolutions.antikythera.evaluator.logging;

import org.slf4j.helpers.MarkerIgnoringBase;

public class AKLogger extends MarkerIgnoringBase {
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
        LoggingEvaluator.captureLog(getName(), "TRACE", msg, null);
    }

    @Override
    public void trace(String format, Object arg) {
        LoggingEvaluator.captureLog(getName(), "TRACE", format, new Object[]{arg});
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        LoggingEvaluator.captureLog(getName(), "TRACE", format, new Object[]{arg1, arg2});
    }

    @Override
    public void trace(String format, Object... arguments) {
        LoggingEvaluator.captureLog(getName(), "TRACE", format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {

    }

    @Override
    public void debug(String msg) {
        LoggingEvaluator.captureLog(getName(), "DEBUG", msg, null);
    }

    @Override
    public void debug(String format, Object arg) {
        LoggingEvaluator.captureLog(getName(), "DEBUG", format, new Object[]{arg});
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        LoggingEvaluator.captureLog(getName(), "DEBUG", format, new Object[]{arg1, arg2});
    }

    @Override
    public void debug(String format, Object... arguments) {
        LoggingEvaluator.captureLog(getName(), "DEBUG", format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {

    }

    @Override
    public void info(String msg) {
        LoggingEvaluator.captureLog(getName(), "INFO", msg, null);
    }

    @Override
    public void info(String format, Object arg) {
        LoggingEvaluator.captureLog(getName(), "INFO", format, new Object[]{arg});
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        LoggingEvaluator.captureLog(getName(), "INFO", format, new Object[]{arg1, arg2});
    }

    @Override
    public void info(String format, Object... arguments) {
        LoggingEvaluator.captureLog(getName(), "INFO", format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {

    }

    @Override
    public void warn(String msg) {
        LoggingEvaluator.captureLog(getName(), "WARN", msg, null);
    }

    @Override
    public void warn(String format, Object arg) {
        LoggingEvaluator.captureLog(getName(), "WARN", format, new Object[]{arg});
    }

    @Override
    public void warn(String format, Object... arguments) {
        LoggingEvaluator.captureLog(getName(), "WARN", format, arguments);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        LoggingEvaluator.captureLog(getName(), "WARN", format, new Object[]{arg1, arg2});
    }

    @Override
    public void warn(String msg, Throwable t) {

    }

    @Override
    public void error(String msg) {
        LoggingEvaluator.captureLog(getName(), "ERROR", msg, null);
    }

    @Override
    public void error(String format, Object arg) {
        LoggingEvaluator.captureLog(getName(), "ERROR", format, new Object[]{arg});
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        LoggingEvaluator.captureLog(getName(), "ERROR", format, new Object[]{arg1, arg2});
    }

    @Override
    public void error(String format, Object... arguments) {
        LoggingEvaluator.captureLog(getName(), "ERROR", format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {

    }
}
