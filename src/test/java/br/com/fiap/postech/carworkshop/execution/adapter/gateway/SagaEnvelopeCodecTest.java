package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.message.CancelDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisCompletedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.EmptyPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.FailurePayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.RepairCompletedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.RepairStartedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartRepairCommand;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.PendingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagaEnvelopeCodecTest {

    private SagaEnvelopeCodec codec;

    @BeforeEach
    void setUp() {
        codec = TestCodec.codec();
    }

    @Test
    void refusesABodyThatIsNotJson() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("nao e json"));
    }

    @Test
    void readsTheWholeEnvelope() {
        String body = """
                {"messageId":"0b8f1c2e-0000-4000-8000-000000000002","type":"StartDiagnosis","version":1,
                 "occurredAt":"2026-10-30T12:00:00Z","sagaId":"6f1d0f3a-0000-4000-8000-000000000001",
                 "workOrderId":42,"branchId":"SP-01","correlationId":"EVIDENCIA-B6-001",
                 "causationId":"0b8f1c2e-0000-4000-8000-000000000009","payload":{}}
                """;

        SagaEnvelope envelope = codec.envelopeOf(codec.decode(body));

        assertEquals(ExecutionFixtures.MESSAGE_ID, envelope.messageId());
        assertEquals(SagaMessageType.START_DIAGNOSIS, envelope.type());
        assertEquals(1, envelope.version());
        assertEquals(ExecutionFixtures.NOW, envelope.occurredAt());
        assertEquals(ExecutionFixtures.SAGA_ID, envelope.sagaId());
        assertEquals(ExecutionFixtures.WORK_ORDER_ID, envelope.workOrderId());
        assertEquals(ExecutionFixtures.BRANCH, envelope.branchId());
        assertEquals(ExecutionFixtures.CORRELATION_ID, envelope.correlationId());
        assertEquals(UUID.fromString("0b8f1c2e-0000-4000-8000-000000000009"), envelope.causationId());
    }

    @Test
    void readsTheStartDiagnosisPayloadIncludingTheComplaint() {
        String body = envelope("StartDiagnosis",
                "{\"vehiclePlate\":\"ABC1D23\",\"complaint\":\"Barulho na suspensao\"}");

        StartDiagnosisCommand command = codec.toStartDiagnosis(codec.decode(body));

        assertEquals(ExecutionFixtures.PLATE, command.vehiclePlate());
        assertEquals(ExecutionFixtures.COMPLAINT, command.complaint());
        assertEquals(ExecutionFixtures.BRANCH, command.envelope().branchId());
    }

    @Test
    void readsTheCancelDiagnosisReason() {
        String body = envelope("CancelDiagnosis", "{\"reason\":\"CANCELED_BY_STAFF\"}");

        CancelDiagnosisCommand command = codec.toCancelDiagnosis(codec.decode(body));

        assertEquals("CANCELED_BY_STAFF", command.reason());
    }

    // O unitPrice do snapshot e ignorado de proposito: preco nao e assunto deste servico.
    @Test
    void readsTheRepairItemsAndDropsThePrice() {
        String body = envelope("StartRepair", "{\"items\":["
                + "{\"type\":\"SERVICE\",\"catalogId\":7,\"description\":\"Troca de oleo\",\"quantity\":1,"
                + "\"unitPrice\":180.00},"
                + "{\"type\":\"PART\",\"catalogId\":12,\"description\":\"Filtro de oleo\",\"quantity\":3,"
                + "\"unitPrice\":45.50}]}");

        StartRepairCommand command = codec.toStartRepair(codec.decode(body));

        assertEquals(ExecutionFixtures.repairItems(), command.items());
    }

    @Test
    void readsAnEmptyItemListWhenThePayloadHasNone() {
        StartRepairCommand command = codec.toStartRepair(codec.decode(envelope("StartRepair", "{}")));

        assertTrue(command.items().isEmpty());
    }

    @Test
    void readsAMalformedIdentifierAsAbsent() {
        String body = "{\"messageId\":\"nao-e-uuid\",\"sagaId\":\"nao-e-uuid\","
                + "\"occurredAt\":\"nao-e-data\",\"workOrderId\":42,\"payload\":{}}";

        SagaEnvelope envelope = codec.envelopeOf(codec.decode(body));

        assertNull(envelope.messageId());
        assertNull(envelope.sagaId());
        assertNull(envelope.occurredAt());
    }

    // O instante vai como ISO com Z porque o codec escreve toString(), nao delega ao Jackson: este teste e o
    // que trava a forma.
    @Test
    void writesTheDiagnosisStartedPayload() {
        String json = codec.writePayload(new DiagnosisStartedPayload(ExecutionFixtures.MECHANIC,
                ExecutionFixtures.NOW));

        assertEquals("{\"mechanic\":\"mecanico.joana\",\"startedAt\":\"2026-10-30T12:00:00Z\"}", json);
    }

    @Test
    void writesTheDiagnosisCompletedPayloadWithTheItemsAndWithoutAnyPrice() {
        String json = codec.writePayload(new DiagnosisCompletedPayload("Amortecedor dianteiro gasto",
                ExecutionFixtures.diagnosedItems()));

        assertEquals("{\"description\":\"Amortecedor dianteiro gasto\",\"items\":["
                + "{\"type\":\"SERVICE\",\"catalogId\":7,\"quantity\":1},"
                + "{\"type\":\"PART\",\"catalogId\":12,\"quantity\":3}]}", json);
    }

    @Test
    void writesTheRepairInstants() {
        assertEquals("{\"startedAt\":\"2026-10-30T12:00:00Z\"}",
                codec.writePayload(new RepairStartedPayload(ExecutionFixtures.NOW)));
        assertEquals("{\"completedAt\":\"2026-10-30T12:00:00Z\"}",
                codec.writePayload(new RepairCompletedPayload(ExecutionFixtures.NOW)));
    }

    @Test
    void writesTheFailureReason() {
        assertEquals("{\"reason\":\"REPAIR_FAILED\"}",
                codec.writePayload(new FailurePayload("REPAIR_FAILED")));
    }

    @Test
    void writesAnEmptyPayloadAsAnEmptyObject() {
        assertEquals("{}", codec.writePayload(new EmptyPayload()));
    }

    @Test
    void encodesThePendingEventWithThePayloadAsANestedObject() {
        PendingEvent event = new PendingEvent(ExecutionFixtures.MESSAGE_ID,
                SagaMessageType.DIAGNOSIS_CANCELED, 1, ExecutionFixtures.NOW, ExecutionFixtures.SAGA_ID,
                ExecutionFixtures.WORK_ORDER_ID, ExecutionFixtures.BRANCH, ExecutionFixtures.CORRELATION_ID,
                UUID.fromString("0b8f1c2e-0000-4000-8000-000000000009"), "{}");

        JsonNode encoded = codec.decode(codec.encode(event));

        assertEquals(ExecutionFixtures.MESSAGE_ID.toString(), encoded.path("messageId").asText());
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED, encoded.path("type").asText());
        assertEquals(1, encoded.path("version").asInt());
        assertEquals("2026-10-30T12:00:00Z", encoded.path("occurredAt").asText());
        assertEquals(ExecutionFixtures.SAGA_ID.toString(), encoded.path("sagaId").asText());
        assertEquals(42L, encoded.path("workOrderId").asLong());
        assertEquals(ExecutionFixtures.BRANCH, encoded.path("branchId").asText());
        assertEquals(ExecutionFixtures.CORRELATION_ID, encoded.path("correlationId").asText());
        assertEquals("0b8f1c2e-0000-4000-8000-000000000009", encoded.path("causationId").asText());
        assertTrue(encoded.path("payload").isObject());
    }

    @Test
    void omitsTheOptionalEnvelopeFieldsWhenTheyAreAbsent() {
        PendingEvent event = new PendingEvent(ExecutionFixtures.MESSAGE_ID,
                SagaMessageType.DIAGNOSIS_STARTED, 1, ExecutionFixtures.NOW, ExecutionFixtures.SAGA_ID,
                ExecutionFixtures.WORK_ORDER_ID, null, null, null,
                "{\"startedAt\":\"2026-10-30T12:00:00Z\"}");

        JsonNode encoded = codec.decode(codec.encode(event));

        assertFalse(encoded.has("branchId"));
        assertFalse(encoded.has("correlationId"));
        assertFalse(encoded.has("causationId"));
        assertEquals("2026-10-30T12:00:00Z", encoded.path("payload").path("startedAt").asText());
    }

    private static String envelope(String type, String payload) {
        return "{\"messageId\":\"0b8f1c2e-0000-4000-8000-000000000002\",\"type\":\"" + type + "\","
                + "\"version\":1,\"occurredAt\":\"2026-10-30T12:00:00Z\","
                + "\"sagaId\":\"6f1d0f3a-0000-4000-8000-000000000001\",\"workOrderId\":42,"
                + "\"branchId\":\"SP-01\",\"correlationId\":\"EVIDENCIA-B6-001\",\"payload\":" + payload + "}";
    }
}
