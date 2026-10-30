package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.TestCodec;
import br.com.fiap.postech.carworkshop.execution.domain.message.CancelDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartRepairCommand;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.ExecutionCommandUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionCommandHandlerTest {

    private ExecutionCommandUseCase commands;
    private SagaEnvelopeCodec codec;
    private ExecutionCommandHandler handler;

    @BeforeEach
    void setUp() {
        commands = mock(ExecutionCommandUseCase.class);
        codec = TestCodec.codec();
        handler = new ExecutionCommandHandler();
        handler.codec = codec;
        handler.commands = commands;
    }

    @Test
    void sendsTheStartDiagnosisCommandToTheUseCase() {
        when(commands.queueDiagnosis(any())).thenReturn(ExecutionWriteResult.APPLIED);

        ExecutionWriteResult result = handler.apply(
                body(SagaMessageType.START_DIAGNOSIS,
                        "{\"vehiclePlate\":\"ABC1D23\",\"complaint\":\"Barulho\"}"),
                SagaMessageType.START_DIAGNOSIS);

        ArgumentCaptor<StartDiagnosisCommand> captor = ArgumentCaptor.forClass(StartDiagnosisCommand.class);
        verify(commands).queueDiagnosis(captor.capture());
        assertEquals(ExecutionFixtures.PLATE, captor.getValue().vehiclePlate());
        assertEquals("Barulho", captor.getValue().complaint());
        assertEquals(ExecutionWriteResult.APPLIED, result);
    }

    @Test
    void sendsTheCancelDiagnosisCommandToTheUseCase() {
        when(commands.cancelDiagnosis(any())).thenReturn(ExecutionWriteResult.APPLIED);

        handler.apply(body(SagaMessageType.CANCEL_DIAGNOSIS, "{\"reason\":\"PAYMENT_EXPIRED\"}"),
                SagaMessageType.CANCEL_DIAGNOSIS);

        ArgumentCaptor<CancelDiagnosisCommand> captor = ArgumentCaptor.forClass(CancelDiagnosisCommand.class);
        verify(commands).cancelDiagnosis(captor.capture());
        assertEquals("PAYMENT_EXPIRED", captor.getValue().reason());
    }

    @Test
    void sendsTheStartRepairCommandToTheUseCase() {
        when(commands.queueRepair(any())).thenReturn(ExecutionWriteResult.APPLIED);

        handler.apply(body(SagaMessageType.START_REPAIR,
                "{\"items\":[{\"type\":\"PART\",\"catalogId\":12,\"description\":\"Filtro\",\"quantity\":2}]}"),
                SagaMessageType.START_REPAIR);

        ArgumentCaptor<StartRepairCommand> captor = ArgumentCaptor.forClass(StartRepairCommand.class);
        verify(commands).queueRepair(captor.capture());
        assertEquals(1, captor.getValue().items().size());
        assertEquals(12L, captor.getValue().items().get(0).catalogId());
    }

    @Test
    void refusesATypeWithoutAHandler() {
        JsonNode node = body("PaintTheCar", "{}");

        assertThrows(IllegalStateException.class, () -> handler.apply(node, "PaintTheCar"));
    }

    private JsonNode body(String type, String payload) {
        return codec.decode("{\"messageId\":\"0b8f1c2e-0000-4000-8000-000000000002\",\"type\":\"" + type
                + "\",\"sagaId\":\"6f1d0f3a-0000-4000-8000-000000000001\",\"workOrderId\":42,"
                + "\"branchId\":\"SP-01\",\"correlationId\":\"EVIDENCIA-B6-001\",\"payload\":" + payload + "}");
    }
}
