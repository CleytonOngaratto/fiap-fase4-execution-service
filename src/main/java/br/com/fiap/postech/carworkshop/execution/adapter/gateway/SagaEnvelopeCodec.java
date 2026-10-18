package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class SagaEnvelopeCodec {

    public static final String FIELD_MESSAGE_ID = "messageId";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_VERSION = "version";
    public static final String FIELD_OCCURRED_AT = "occurredAt";
    public static final String FIELD_SAGA_ID = "sagaId";
    public static final String FIELD_WORK_ORDER_ID = "workOrderId";
    public static final String FIELD_CORRELATION_ID = "correlationId";
    public static final String FIELD_CAUSATION_ID = "causationId";
    public static final String FIELD_PAYLOAD = "payload";

    private static final String FIELD_VEHICLE_PLATE = "vehiclePlate";
    private static final String FIELD_STARTED_AT = "startedAt";

    @Inject
    ObjectMapper objectMapper;

    public JsonNode decode(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Saga message body is not valid JSON.", e);
        }
    }

    public StartDiagnosisCommand toStartDiagnosis(JsonNode envelope) {
        return new StartDiagnosisCommand(
                uuid(envelope, FIELD_MESSAGE_ID),
                envelope.path(FIELD_WORK_ORDER_ID).asLong(),
                uuid(envelope, FIELD_SAGA_ID),
                text(envelope, FIELD_CORRELATION_ID),
                text(envelope.path(FIELD_PAYLOAD), FIELD_VEHICLE_PLATE));
    }

    // Sem mechanic: este servico ainda nao tem mecanicos, e o contrato omite o campo em vez de mandar valor
    // inventado, que ficaria indistinguivel do real em log e painel.
    public String encode(DiagnosisStartedEvent event) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put(FIELD_MESSAGE_ID, event.messageId().toString());
        envelope.put(FIELD_TYPE, event.type());
        envelope.put(FIELD_VERSION, event.version());
        envelope.put(FIELD_OCCURRED_AT, event.occurredAt().toString());
        envelope.put(FIELD_SAGA_ID, event.sagaId().toString());
        envelope.put(FIELD_WORK_ORDER_ID, event.workOrderId());
        if (event.correlationId() != null && !event.correlationId().isBlank()) {
            envelope.put(FIELD_CORRELATION_ID, event.correlationId());
        }
        envelope.put(FIELD_CAUSATION_ID, event.causationId().toString());
        envelope.putObject(FIELD_PAYLOAD).put(FIELD_STARTED_AT, event.startedAt().toString());
        return envelope.toString();
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : UUID.fromString(value);
    }
}
