package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SnsExecutionEventGatewayTest {

    private static final String TOPIC = "arn:aws:sns:us-east-1:000000000000:execution-events.fifo";

    // MessageGroupId por ordem de servico garante a ordem; MessageDeduplicationId pelo messageId faz o SNS FIFO
    // descartar a republicacao dentro da janela dele.
    @Test
    void publishesWithTheFifoKeysAndTheMessageAttributes() {
        SnsClient sns = mock(SnsClient.class);
        SnsExecutionEventGateway gateway = new SnsExecutionEventGateway(sns, TOPIC);

        gateway.publish(message(ExecutionFixtures.CORRELATION_ID));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(captor.capture());
        PublishRequest request = captor.getValue();
        assertEquals(TOPIC, request.topicArn());
        assertEquals("42", request.messageGroupId());
        assertEquals(ExecutionFixtures.MESSAGE_ID.toString(), request.messageDeduplicationId());
        assertEquals("{}", request.message());
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED, request.messageAttributes().get("type").stringValue());
        assertEquals("1", request.messageAttributes().get("version").stringValue());
        assertEquals("Number", request.messageAttributes().get("version").dataType());
        assertEquals(ExecutionFixtures.SAGA_ID.toString(),
                request.messageAttributes().get("sagaId").stringValue());
        assertEquals(ExecutionFixtures.CORRELATION_ID,
                request.messageAttributes().get("correlationId").stringValue());
    }

    @Test
    void omitsTheCorrelationAttributeWhenThereIsNone() {
        SnsClient sns = mock(SnsClient.class);
        SnsExecutionEventGateway gateway = new SnsExecutionEventGateway(sns, TOPIC);

        gateway.publish(message(null));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(captor.capture());
        assertFalse(captor.getValue().messageAttributes().containsKey("correlationId"));
    }

    private static OutboundMessage message(String correlationId) {
        return new OutboundMessage(ExecutionFixtures.MESSAGE_ID, SagaMessageType.DIAGNOSIS_CANCELED, 1,
                ExecutionFixtures.WORK_ORDER_ID, ExecutionFixtures.SAGA_ID, correlationId, "{}");
    }
}
