package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LoggingExecutionEventGatewayTest {

    @Test
    void recordsTheEnvelopeWithoutAnyProvider() {
        LoggingExecutionEventGateway gateway = new LoggingExecutionEventGateway();

        assertDoesNotThrow(() -> gateway.publish(new OutboundMessage(ExecutionFixtures.MESSAGE_ID,
                SagaMessageType.DIAGNOSIS_CANCELED, 1, ExecutionFixtures.WORK_ORDER_ID,
                ExecutionFixtures.SAGA_ID, ExecutionFixtures.CORRELATION_ID, "{}")));
    }
}
