package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagaEnvelopeCodecTest {

    private static final UUID MESSAGE_ID = UUID.fromString("0b8f1c2e-0000-4000-8000-000000000001");
    private static final UUID SAGA_ID = UUID.fromString("6f1d0000-0000-4000-8000-000000000002");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SagaEnvelopeCodec codec;

    @BeforeEach
    void setUp() {
        codec = new SagaEnvelopeCodec();
        codec.objectMapper = objectMapper;
    }

    @Test
    void toStartDiagnosis_readsTheEnvelopeAndThePayload() {
        String body = """
                {
                  "messageId": "%s",
                  "type": "StartDiagnosis",
                  "version": 1,
                  "occurredAt": "2026-10-16T12:00:00Z",
                  "sagaId": "%s",
                  "workOrderId": 42,
                  "correlationId": "EVIDENCIA-B3-001",
                  "payload": { "vehiclePlate": "ABC1D23" }
                }
                """.formatted(MESSAGE_ID, SAGA_ID);

        StartDiagnosisCommand command = codec.toStartDiagnosis(codec.decode(body));

        assertEquals(MESSAGE_ID, command.messageId());
        assertEquals(SAGA_ID, command.sagaId());
        assertEquals(42L, command.workOrderId());
        assertEquals("EVIDENCIA-B3-001", command.correlationId());
        assertEquals("ABC1D23", command.vehiclePlate());
    }

    @Test
    void toStartDiagnosis_toleratesAnAbsentPayloadField() {
        String body = """
                {
                  "messageId": "%s",
                  "type": "StartDiagnosis",
                  "sagaId": "%s",
                  "workOrderId": 42,
                  "payload": { }
                }
                """.formatted(MESSAGE_ID, SAGA_ID);

        StartDiagnosisCommand command = codec.toStartDiagnosis(codec.decode(body));

        assertNull(command.vehiclePlate());
        assertNull(command.correlationId());
    }

    @Test
    void encode_writesTheCatalogEnvelopeWithoutMechanic() throws Exception {
        StartDiagnosisCommand command =
                new StartDiagnosisCommand(MESSAGE_ID, 42L, SAGA_ID, "EVIDENCIA-B3-001", "ABC1D23");
        DiagnosisStartedEvent event =
                DiagnosisStartedEvent.from(command, Instant.parse("2026-10-16T12:00:00Z"));

        JsonNode envelope = objectMapper.readTree(codec.encode(event));

        assertEquals("DiagnosisStarted", envelope.get("type").asText());
        assertEquals(1, envelope.get("version").asInt());
        assertEquals("2026-10-16T12:00:00Z", envelope.get("occurredAt").asText());
        assertEquals(SAGA_ID.toString(), envelope.get("sagaId").asText());
        assertEquals(42L, envelope.get("workOrderId").asLong());
        assertEquals("EVIDENCIA-B3-001", envelope.get("correlationId").asText());
        assertEquals(MESSAGE_ID.toString(), envelope.get("causationId").asText());
        assertEquals("2026-10-16T12:00:00Z", envelope.get("payload").get("startedAt").asText());
        assertFalse(envelope.get("payload").has("mechanic"));
        assertTrue(envelope.has("messageId"));
    }

    @Test
    void encode_omitsCorrelationIdWhenThereIsNone() throws Exception {
        StartDiagnosisCommand command =
                new StartDiagnosisCommand(MESSAGE_ID, 42L, SAGA_ID, null, "ABC1D23");

        JsonNode envelope = objectMapper.readTree(
                codec.encode(DiagnosisStartedEvent.from(command, Instant.parse("2026-10-16T12:00:00Z"))));

        assertFalse(envelope.has("correlationId"));
    }

    @Test
    void decode_rejectsABodyThatIsNotJson() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("not json"));
    }
}
