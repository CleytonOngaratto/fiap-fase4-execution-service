package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedEvent;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.DiagnosisEventPublisherPort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
@QuarkusTestResource(value = ElasticMqResource.class, restrictToAnnotatedClass = true)
class ExecutionCommandsConsumerTest {

    private static final UUID SAGA_ID = UUID.fromString("6f1d0000-0000-4000-8000-000000000002");

    @Inject
    ExecutionCommandsConsumer consumer;

    @Inject
    SqsClient sqs;

    @InjectMock
    DiagnosisEventPublisherPort publisher;

    @ConfigProperty(name = "execution.commands.queue-url")
    String commandsQueueUrl;

    @BeforeEach
    void emptyTheQueue() {
        sqs.purgeQueue(PurgeQueueRequest.builder().queueUrl(commandsQueueUrl).build());
    }

    private void send(String body, String groupId) {
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(commandsQueueUrl)
                .messageBody(body)
                .messageGroupId(groupId)
                .messageDeduplicationId(UUID.randomUUID().toString())
                .build());
    }

    private static String startDiagnosis(UUID messageId, long workOrderId) {
        return """
                {
                  "messageId": "%s",
                  "type": "StartDiagnosis",
                  "version": 1,
                  "occurredAt": "2026-10-16T12:00:00Z",
                  "sagaId": "%s",
                  "workOrderId": %d,
                  "correlationId": "EVIDENCIA-B3-001",
                  "payload": { "vehiclePlate": "ABC1D23" }
                }
                """.formatted(messageId, SAGA_ID, workOrderId);
    }

    private int messagesLeft() {
        Map<QueueAttributeName, String> attributes = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(commandsQueueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                .build()).attributes();
        return Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                + Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
    }

    private DiagnosisStartedEvent capturePublished() {
        ArgumentCaptor<DiagnosisStartedEvent> captor = ArgumentCaptor.forClass(DiagnosisStartedEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void startDiagnosis_publishesTheEventAndDeletesTheCommand() {
        UUID messageId = UUID.randomUUID();
        send(startDiagnosis(messageId, 42L), "42");

        consumer.poll();

        DiagnosisStartedEvent event = capturePublished();
        assertEquals(42L, event.workOrderId());
        assertEquals(SAGA_ID, event.sagaId());
        assertEquals("EVIDENCIA-B3-001", event.correlationId());
        assertEquals(messageId, event.causationId());
        assertEquals(0, messagesLeft());
    }

    @Test
    void redeliveryOfTheSameCommandKeepsTheSameMessageId() {
        UUID messageId = UUID.randomUUID();
        send(startDiagnosis(messageId, 43L), "43");
        consumer.poll();
        send(startDiagnosis(messageId, 43L), "43");
        consumer.poll();

        ArgumentCaptor<DiagnosisStartedEvent> captor = ArgumentCaptor.forClass(DiagnosisStartedEvent.class);
        verify(publisher, times(2)).publish(captor.capture());
        assertEquals(captor.getAllValues().get(0).messageId(), captor.getAllValues().get(1).messageId());
    }

    @Test
    void unknownType_isLoggedAndDeleted() {
        send(startDiagnosis(UUID.randomUUID(), 44L).replace("StartDiagnosis", "SomethingElse"), "44");

        consumer.poll();

        verify(publisher, never()).publish(any());
        assertEquals(0, messagesLeft());
    }

    @Test
    void bodyThatIsNotJson_staysForTheRedrivePolicy() {
        send("not json", "45");

        consumer.poll();

        verify(publisher, never()).publish(any());
        assertEquals(1, messagesLeft());
    }

    @Test
    void publishFailure_leavesTheCommandForTheRedrivePolicy() {
        doThrow(new IllegalStateException("SNS is down")).when(publisher).publish(any());
        send(startDiagnosis(UUID.randomUUID(), 46L), "46");

        consumer.poll();

        verify(publisher).publish(any());
        assertEquals(1, messagesLeft());
    }
}
