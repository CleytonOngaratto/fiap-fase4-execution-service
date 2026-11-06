package br.com.fiap.postech.carworkshop.execution.infrastructure.observability;

import org.jboss.logmanager.Logger;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public final class LogLines extends Handler implements AutoCloseable {

    private static final List<String> CONTEXT_KEYS =
            List.of(LogContext.CORRELATION_ID, LogContext.SAGA_ID, LogContext.BRANCH_ID);

    private final Logger logger;
    private final List<Level> levels = new ArrayList<>();
    private final List<Map<String, String>> contexts = new ArrayList<>();

    private LogLines(Logger logger) {
        this.logger = logger;
    }

    public static LogLines of(Class<?> source) {
        LogLines lines = new LogLines(Logger.getLogger(source.getName()));
        lines.logger.addHandler(lines);
        return lines;
    }

    public List<Map<String, String>> errors() {
        return atLeast(Level.SEVERE);
    }

    public List<Map<String, String>> warnings() {
        return atLeast(Level.WARNING);
    }

    private List<Map<String, String>> atLeast(Level threshold) {
        List<Map<String, String>> selected = new ArrayList<>();
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).intValue() >= threshold.intValue()) {
                selected.add(contexts.get(i));
            }
        }
        return selected;
    }

    @Override
    public void publish(LogRecord logRecord) {
        Map<String, String> context = new HashMap<>();
        for (String key : CONTEXT_KEYS) {
            String value = MDC.get(key);
            if (value != null) {
                context.put(key, value);
            }
        }
        levels.add(logRecord.getLevel());
        contexts.add(context);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        logger.removeHandler(this);
    }
}
