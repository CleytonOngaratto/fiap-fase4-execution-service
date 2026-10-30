package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;
import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ExecutionCommandsConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExecutionCommandsConsumer.class);

    private static final Set<String> KNOWN = Set.of(
            SagaMessageType.START_DIAGNOSIS,
            SagaMessageType.CANCEL_DIAGNOSIS,
            SagaMessageType.START_REPAIR);

    @Inject
    SqsClient sqs;

    @Inject
    SagaEnvelopeCodec codec;

    @Inject
    ExecutionCommandHandler handler;

    @ConfigProperty(name = "execution.commands.queue-url")
    String commandsQueueUrl;

    @ConfigProperty(name = "execution.commands.batch-size")
    int batchSize;

    @Scheduled(every = "2s", identity = "execution-commands-consumer",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void poll() {
        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(commandsQueueUrl)
                .maxNumberOfMessages(batchSize)
                .messageAttributeNames("All")
                .build()).messages();

        for (Message message : messages) {
            consume(message);
        }
    }

    void consume(Message message) {
        JsonNode envelope;
        try {
            envelope = codec.decode(message.body());
        } catch (RuntimeException e) {
            // Sem apagar: a mensagem volta, esgota o maxReceiveCount e para na DLQ, onde da para investigar.
            log.error("Command could not be read; leaving it for the redrive policy.", e);
            return;
        }

        String type = envelope.path(SagaEnvelopeCodec.FIELD_TYPE).asText(null);
        if (!KNOWN.contains(type)) {
            log.warn("Unknown command type {}: ignored.", type);
            delete(message);
            return;
        }

        String correlationId = envelope.path(SagaEnvelopeCodec.FIELD_CORRELATION_ID).asText(null);
        try (LogContext ignored = LogContext.of(correlationId, sagaIdOf(envelope))) {
            ExecutionWriteResult result = handler.apply(envelope, type);
            if (result != ExecutionWriteResult.APPLIED) {
                log.info("Command {} finished as {}.", type, result);
            }
            // Resultado de negocio apaga a mensagem, inclusive o conflito: a fila e FIFO por ordem de servico, e
            // deixar a mensagem travaria todas as seguintes daquela OS ate a DLQ.
            delete(message);
        } catch (RuntimeException e) {
            log.error("Command failed; leaving it for the redrive policy.", e);
        }
    }

    private void delete(Message message) {
        sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(commandsQueueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }

    // O sagaId so alimenta o log: um valor malformado nao pode derrubar o processamento da mensagem.
    private static UUID sagaIdOf(JsonNode envelope) {
        String value = envelope.path(SagaEnvelopeCodec.FIELD_SAGA_ID).asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
