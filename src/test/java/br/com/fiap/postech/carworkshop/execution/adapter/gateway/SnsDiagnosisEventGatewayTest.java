package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SnsDiagnosisEventGatewayTest {

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:execution-events.fifo";

    private final SnsClient sns = mock(SnsClient.class);
    private SnsDiagnosisEventGateway gateway;

    @BeforeEach
    void setUp() {
        SagaEnvelopeCodec codec = new SagaEnvelopeCodec();
        codec.objectMapper = new ObjectMapper();
        gateway = new SnsDiagnosisEventGateway();
        gateway.sns = sns;
        gateway.codec = codec;
        gateway.topicArn = TOPIC_ARN;
    }

    private static DiagnosisStartedEvent event(String correlationId) {
        StartDiagnosisCommand command =
                new StartDiagnosisCommand(UUID.randomUUID(), 42L, UUID.randomUUID(), correlationId, "ABC1D23");
        return DiagnosisStartedEvent.from(command, Instant.parse("2026-10-16T12:00:00Z"));
    }

    private PublishRequest captureRequest() {
        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void publish_groupsByWorkOrderAndDeduplicatesByMessageId() {
        DiagnosisStartedEvent diagnosisStarted = event("EVIDENCIA-B3-001");

        gateway.publish(diagnosisStarted);

        PublishRequest request = captureRequest();
        assertEquals(TOPIC_ARN, request.topicArn());
        assertEquals("42", request.messageGroupId());
        assertEquals(diagnosisStarted.messageId().toString(), request.messageDeduplicationId());
    }

    @Test
    void publish_carriesTheFourCatalogAttributes() {
        DiagnosisStartedEvent diagnosisStarted = event("EVIDENCIA-B3-001");

        gateway.publish(diagnosisStarted);

        Map<String, MessageAttributeValue> attributes = captureRequest().messageAttributes();
        assertEquals(4, attributes.size());
        assertEquals("DiagnosisStarted", attributes.get("type").stringValue());
        assertEquals("1", attributes.get("version").stringValue());
        assertEquals("Number", attributes.get("version").dataType());
        assertEquals(diagnosisStarted.sagaId().toString(), attributes.get("sagaId").stringValue());
        assertEquals("EVIDENCIA-B3-001", attributes.get("correlationId").stringValue());
    }

    @Test
    void publish_omitsTheCorrelationAttributeWhenThereIsNone() {
        gateway.publish(event(null));

        assertFalse(captureRequest().messageAttributes().containsKey("correlationId"));
    }
}
