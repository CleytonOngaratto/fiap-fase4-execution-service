package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.ExecutionEventPublisher;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.OutboundMessage;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.execution.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.execution.infrastructure.observability.TraceContext;
import br.com.fiap.postech.carworkshop.execution.infrastructure.observability.TraceHeaders;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionOutboxPort;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.PendingEvent;
import com.newrelic.api.agent.Trace;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private static final String IDENTITY = "execution-outbox-relay";
    private static final String TRANSACTION_CATEGORY = "Scheduled";

    @Inject
    ExecutionOutboxPort outbox;

    @Inject
    ExecutionEventPublisher publisher;

    @Inject
    SagaEnvelopeCodec codec;

    @Inject
    TraceContext trace;

    @Inject
    Clock clock;

    @ConfigProperty(name = "execution.outbox.relay.batch-size")
    int batchSize;

    // void, nunca um contador: metodo agendado do Quarkus so aceita void ou CompletionStage, e o erro de tipo
    // aponta uma classe sem relacao nenhuma.
    @Scheduled(every = "2s", identity = IDENTITY,
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void drain() {
        List<PendingEvent> pending = outbox.claimPending(batchSize);
        for (PendingEvent event : pending) {
            if (!publish(event)) {
                return;
            }
        }
    }

    // Transacao por mensagem, nao por ciclo: o agente so aceita o contexto de traco uma vez por transacao.
    @Trace(dispatcher = true)
    boolean publish(PendingEvent event) {
        Map<String, String> traceHeaders = TraceHeaders.fromJson(event.traceContext());
        trace.accept(traceHeaders);
        trace.nameTransaction(TRANSACTION_CATEGORY, IDENTITY);
        try (LogContext ignored = LogContext.of(event.correlationId(), event.sagaId(), event.branchId())) {
            return send(event, traceHeaders);
        }
    }

    // Publica e so depois marca: marcar primeiro perderia a mensagem se o publish falhasse. A entrega em
    // duplicidade que isso admite morre na inbox do orquestrador, que deduplica por messageId.
    private boolean send(PendingEvent event, Map<String, String> traceHeaders) {
        try {
            publisher.publish(messageFor(event, traceHeaders));
            if (!outbox.markSent(event.workOrderId(), event.messageId(), Instant.now(clock))) {
                log.warn("Outbox message {} had already been marked as sent.", event.messageId());
            }
            return true;
        } catch (RuntimeException e) {
            // Para o lote: a ordem por ordem de servico importa, e nao se publica o evento seguinte da mesma OS
            // sem o anterior ter saido.
            log.error("Outbox message {} could not be published; it stays pending.", event.messageId(), e);
            trace.noticeError(e);
            return false;
        }
    }

    private OutboundMessage messageFor(PendingEvent event, Map<String, String> traceHeaders) {
        return new OutboundMessage(event.messageId(), event.type(), event.version(), event.workOrderId(),
                event.sagaId(), event.correlationId(), codec.encode(event), traceHeaders);
    }
}
