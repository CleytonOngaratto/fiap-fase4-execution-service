package br.com.fiap.postech.carworkshop.execution.infrastructure.observability;

import com.newrelic.api.agent.Agent;
import com.newrelic.api.agent.ConcurrentHashMapHeaders;
import com.newrelic.api.agent.HeaderType;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.TransactionNamePriority;
import com.newrelic.api.agent.TransportType;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@ApplicationScoped
public class NewRelicTraceContext implements TraceContext {

    private static final Logger log = LoggerFactory.getLogger(NewRelicTraceContext.class);

    private final Supplier<Agent> agent;

    public NewRelicTraceContext() {
        this(NewRelic::getAgent);
    }

    NewRelicTraceContext(Supplier<Agent> agent) {
        this.agent = agent;
    }

    @Override
    public Map<String, String> capture() {
        try {
            ConcurrentHashMapHeaders headers = ConcurrentHashMapHeaders.build(HeaderType.MESSAGE);
            agent.get().getTransaction().insertDistributedTraceHeaders(headers);
            Map<String, String> captured = new HashMap<>();
            for (String name : headers.getHeaderNames()) {
                captured.put(name, headers.getHeader(name));
            }
            return TraceHeaders.only(captured);
        } catch (RuntimeException e) {
            log.warn("Trace context could not be captured: {}", e.toString());
            return Map.of();
        }
    }

    @Override
    public void accept(Map<String, String> headers) {
        Map<String, String> known = TraceHeaders.only(headers);
        if (known.isEmpty()) {
            return;
        }
        try {
            agent.get().getTransaction().acceptDistributedTraceHeaders(TransportType.Queue,
                    ConcurrentHashMapHeaders.buildFromFlatMap(HeaderType.MESSAGE, known));
        } catch (RuntimeException e) {
            log.warn("Trace context could not be accepted: {}", e.toString());
        }
    }

    @Override
    public void nameTransaction(String category, String name) {
        try {
            agent.get().getTransaction().setTransactionName(TransactionNamePriority.CUSTOM_HIGH, true, category, name);
        } catch (RuntimeException e) {
            log.warn("Transaction could not be named: {}", e.toString());
        }
    }

    @Override
    public void addAttribute(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            NewRelic.addCustomParameter(key, value);
        } catch (RuntimeException e) {
            log.warn("Transaction attribute {} could not be added: {}", key, e.toString());
        }
    }

    @Override
    public void ignoreTransaction() {
        try {
            agent.get().getTransaction().ignore();
        } catch (RuntimeException e) {
            log.warn("Transaction could not be ignored: {}", e.toString());
        }
    }

    @Override
    public void noticeError(Throwable error) {
        try {
            agent.get().getErrorApi().noticeError(error);
        } catch (RuntimeException e) {
            log.warn("Error could not be reported to the transaction: {}", e.toString());
        }
    }
}
