package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.RepairItem;
import br.com.fiap.postech.carworkshop.execution.domain.message.CancelDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisCompletedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.EmptyPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.EventPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.FailurePayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.RepairCompletedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.RepairStartedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartRepairCommand;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.PendingEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SagaEnvelopeCodec {

    public static final String FIELD_MESSAGE_ID = "messageId";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_VERSION = "version";
    public static final String FIELD_OCCURRED_AT = "occurredAt";
    public static final String FIELD_SAGA_ID = "sagaId";
    public static final String FIELD_WORK_ORDER_ID = "workOrderId";
    public static final String FIELD_BRANCH_ID = "branchId";
    public static final String FIELD_CORRELATION_ID = "correlationId";
    public static final String FIELD_CAUSATION_ID = "causationId";
    public static final String FIELD_PAYLOAD = "payload";

    private static final String FIELD_VEHICLE_PLATE = "vehiclePlate";
    private static final String FIELD_COMPLAINT = "complaint";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_CATALOG_ID = "catalogId";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_QUANTITY = "quantity";
    private static final String FIELD_MECHANIC = "mechanic";
    private static final String FIELD_STARTED_AT = "startedAt";
    private static final String FIELD_COMPLETED_AT = "completedAt";

    @Inject
    ObjectMapper objectMapper;

    public JsonNode decode(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Saga message body is not valid JSON.", e);
        }
    }

    public SagaEnvelope envelopeOf(JsonNode node) {
        return new SagaEnvelope(
                uuid(node, FIELD_MESSAGE_ID),
                text(node, FIELD_TYPE),
                node.path(FIELD_VERSION).asInt(SagaEnvelope.CURRENT_VERSION),
                instant(node, FIELD_OCCURRED_AT),
                uuid(node, FIELD_SAGA_ID),
                node.path(FIELD_WORK_ORDER_ID).asLong(),
                text(node, FIELD_BRANCH_ID),
                text(node, FIELD_CORRELATION_ID),
                uuid(node, FIELD_CAUSATION_ID));
    }

    public StartDiagnosisCommand toStartDiagnosis(JsonNode node) {
        JsonNode payload = node.path(FIELD_PAYLOAD);
        return new StartDiagnosisCommand(envelopeOf(node), text(payload, FIELD_VEHICLE_PLATE),
                text(payload, FIELD_COMPLAINT));
    }

    public CancelDiagnosisCommand toCancelDiagnosis(JsonNode node) {
        return new CancelDiagnosisCommand(envelopeOf(node), text(node.path(FIELD_PAYLOAD), FIELD_REASON));
    }

    public StartRepairCommand toStartRepair(JsonNode node) {
        return new StartRepairCommand(envelopeOf(node), repairItems(node.path(FIELD_PAYLOAD).path(FIELD_ITEMS)));
    }

    public String writePayload(EventPayload payload) {
        return payloadNode(payload).toString();
    }

    public String encode(PendingEvent event) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put(FIELD_MESSAGE_ID, event.messageId().toString());
        envelope.put(FIELD_TYPE, event.type());
        envelope.put(FIELD_VERSION, event.version());
        envelope.put(FIELD_OCCURRED_AT, event.occurredAt().toString());
        envelope.put(FIELD_SAGA_ID, event.sagaId().toString());
        envelope.put(FIELD_WORK_ORDER_ID, event.workOrderId());
        putIfPresent(envelope, FIELD_BRANCH_ID, event.branchId());
        putIfPresent(envelope, FIELD_CORRELATION_ID, event.correlationId());
        putIfPresent(envelope, FIELD_CAUSATION_ID, event.causationId() == null ? null
                : event.causationId().toString());
        envelope.set(FIELD_PAYLOAD, decode(event.payload()));
        return envelope.toString();
    }

    // Instante escrito por toString(), nao pelo Jackson: a forma ISO com Z fica fixada aqui e deixa de depender
    // da configuracao do ObjectMapper, que no teste puro nao e a mesma que o Quarkus monta.
    private ObjectNode payloadNode(EventPayload payload) {
        ObjectNode node = objectMapper.createObjectNode();
        if (payload instanceof DiagnosisStartedPayload started) {
            putIfPresent(node, FIELD_MECHANIC, started.mechanic());
            node.put(FIELD_STARTED_AT, started.startedAt().toString());
        } else if (payload instanceof DiagnosisCompletedPayload completed) {
            putIfPresent(node, FIELD_DESCRIPTION, completed.description());
            ArrayNode items = node.putArray(FIELD_ITEMS);
            for (DiagnosedItem item : completed.items()) {
                ObjectNode entry = items.addObject();
                entry.put(FIELD_TYPE, item.type());
                entry.put(FIELD_CATALOG_ID, item.catalogId());
                entry.put(FIELD_QUANTITY, item.quantity());
            }
        } else if (payload instanceof RepairStartedPayload started) {
            node.put(FIELD_STARTED_AT, started.startedAt().toString());
        } else if (payload instanceof RepairCompletedPayload completed) {
            node.put(FIELD_COMPLETED_AT, completed.completedAt().toString());
        } else if (payload instanceof FailurePayload failure) {
            putIfPresent(node, FIELD_REASON, failure.reason());
        } else if (!(payload instanceof EmptyPayload)) {
            throw new IllegalStateException("Event payload " + payload.getClass().getName()
                    + " has no JSON mapping.");
        }
        return node;
    }

    // Tolerant reader: o unitPrice que vem no snapshot do orquestrador e ignorado de proposito - preco e
    // assunto do OS e do Billing, nao deste servico.
    private static List<RepairItem> repairItems(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<RepairItem> items = new ArrayList<>();
        for (JsonNode item : node) {
            items.add(new RepairItem(text(item, FIELD_TYPE), item.path(FIELD_CATALOG_ID).asLong(),
                    text(item, FIELD_DESCRIPTION), item.path(FIELD_QUANTITY).asInt(1)));
        }
        return items;
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
