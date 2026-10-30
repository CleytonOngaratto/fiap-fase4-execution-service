package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.ExecutionEventPublisher;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.OutboundMessage;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.execution.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionOutboxPort;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.PendingEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    @Inject
    ExecutionOutboxPort outbox;

    @Inject
    ExecutionEventPublisher publisher;

    @Inject
    SagaEnvelopeCodec codec;

    @Inject
    Clock clock;

    @ConfigProperty(name = "execution.outbox.relay.batch-size")
    int batchSize;

    // void, nunca um contador: metodo agendado do Quarkus so aceita void ou CompletionStage, e o erro de tipo
    // aponta uma classe sem relacao nenhuma.
    @Scheduled(every = "2s", identity = "execution-outbox-relay",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void drain() {
        List<PendingEvent> pending = outbox.claimPending(batchSize);
        for (PendingEvent event : pending) {
            if (!publish(event)) {
                return;
            }
        }
    }

    // Publica e so depois marca: marcar primeiro perderia a mensagem se o publish falhasse. A entrega em
    // duplicidade que isso admite morre na inbox do orquestrador, que deduplica por messageId.
    private boolean publish(PendingEvent event) {
        try (LogContext ignored = LogContext.of(event.correlationId(), event.sagaId())) {
            publisher.publish(messageFor(event));
            if (!outbox.markSent(event.workOrderId(), event.messageId(), Instant.now(clock))) {
                log.warn("Outbox message {} had already been marked as sent.", event.messageId());
            }
            return true;
        } catch (RuntimeException e) {
            // Para o lote: a ordem por ordem de servico importa, e nao se publica o evento seguinte da mesma OS
            // sem o anterior ter saido.
            log.error("Outbox message {} could not be published; it stays pending.", event.messageId(), e);
            return false;
        }
    }

    private OutboundMessage messageFor(PendingEvent event) {
        return new OutboundMessage(event.messageId(), event.type(), event.version(), event.workOrderId(),
                event.sagaId(), event.correlationId(), codec.encode(event));
    }
}
