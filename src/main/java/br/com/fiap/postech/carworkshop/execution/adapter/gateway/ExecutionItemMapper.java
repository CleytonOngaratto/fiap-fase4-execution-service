package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStep;
import br.com.fiap.postech.carworkshop.execution.domain.entity.RepairItem;
import br.com.fiap.postech.carworkshop.execution.domain.message.ExecutionEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaEnvelope;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.PendingEvent;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ExecutionItemMapper {

    public static final String PK = "pk";
    public static final String SK = "sk";
    public static final String GSI1PK = "gsi1pk";
    public static final String GSI1SK = "gsi1sk";
    public static final String GSI2PK = "gsi2pk";
    public static final String GSI2SK = "gsi2sk";
    public static final String BRANCH_STATUS_INDEX = "gsi-branch-status";
    public static final String OUTBOX_PENDING_INDEX = "gsi-outbox-pending";
    public static final String STATE_SK = "STATE";
    public static final String PENDING_PARTITION = "PENDING";
    public static final String STATUS = "status";
    public static final String VERSION = "version";
    public static final String SENT_AT = "sentAt";

    private static final String WORK_ORDER_ID = "workOrderId";
    private static final String BRANCH_ID = "branchId";
    private static final String SAGA_ID = "sagaId";
    private static final String STATUS_REASON = "statusReason";
    private static final String ENQUEUED_AT = "enqueuedAt";
    private static final String VEHICLE_PLATE = "vehiclePlate";
    private static final String COMPLAINT = "complaint";
    private static final String MECHANIC = "mechanic";
    private static final String DIAGNOSIS_DESCRIPTION = "diagnosisDescription";
    private static final String DIAGNOSED_ITEMS = "diagnosedItems";
    private static final String REPAIR_ITEMS = "repairItems";
    private static final String UPDATED_AT = "updatedAt";
    private static final String OCCURRED_AT = "occurredAt";
    private static final String ACTION = "action";
    private static final String DETAIL = "detail";
    private static final String ACTOR = "actor";
    private static final String MESSAGE_ID = "messageId";
    private static final String TYPE = "type";
    private static final String PROCESSED_AT = "processedAt";
    private static final String EXPIRES_AT = "expiresAt";
    private static final String CORRELATION_ID = "correlationId";
    private static final String CAUSATION_ID = "causationId";
    private static final String PAYLOAD = "payload";
    private static final String CATALOG_ID = "catalogId";
    private static final String DESCRIPTION = "description";
    private static final String QUANTITY = "quantity";

    private ExecutionItemMapper() {
    }

    public static String partitionOf(Long workOrderId) {
        return "WO#" + workOrderId;
    }

    public static String queuePartitionOf(String branchId, ExecutionStatus status) {
        return branchId + "#" + status;
    }

    public static String stepSortKey(Instant occurredAt, String action) {
        return "STEP#" + occurredAt + "#" + action;
    }

    public static String inboxSortKey(UUID messageId) {
        return "INBOX#" + messageId;
    }

    public static String outboxSortKey(UUID messageId) {
        return "OUTBOX#" + messageId;
    }

    public static Map<String, AttributeValue> toStateItem(ExecutionItem item) {
        Map<String, AttributeValue> attributes = new HashMap<>();
        attributes.put(PK, text(partitionOf(item.workOrderId())));
        attributes.put(SK, text(STATE_SK));
        attributes.put(GSI1PK, text(queuePartitionOf(item.branchId(), item.status())));
        attributes.put(GSI1SK, text(item.enqueuedAt().toString()));
        attributes.put(WORK_ORDER_ID, number(item.workOrderId()));
        attributes.put(BRANCH_ID, text(item.branchId()));
        attributes.put(STATUS, text(item.status().name()));
        attributes.put(ENQUEUED_AT, text(item.enqueuedAt().toString()));
        attributes.put(VERSION, number(item.version()));
        attributes.put(UPDATED_AT, text(item.updatedAt().toString()));
        putIfPresent(attributes, SAGA_ID, asString(item.sagaId()));
        putIfPresent(attributes, STATUS_REASON, item.statusReason());
        putIfPresent(attributes, VEHICLE_PLATE, item.vehiclePlate());
        putIfPresent(attributes, COMPLAINT, item.complaint());
        putIfPresent(attributes, MECHANIC, item.mechanic());
        putIfPresent(attributes, DIAGNOSIS_DESCRIPTION, item.diagnosisDescription());
        if (!item.diagnosedItems().isEmpty()) {
            attributes.put(DIAGNOSED_ITEMS, diagnosedList(item.diagnosedItems()));
        }
        if (!item.repairItems().isEmpty()) {
            attributes.put(REPAIR_ITEMS, repairList(item.repairItems()));
        }
        return attributes;
    }

    public static ExecutionItem toDomain(Map<String, AttributeValue> attributes) {
        return new ExecutionItem(
                asLong(attributes, WORK_ORDER_ID),
                asText(attributes, BRANCH_ID),
                asUuid(attributes, SAGA_ID),
                ExecutionStatus.valueOf(asText(attributes, STATUS)),
                asText(attributes, STATUS_REASON),
                asInstant(attributes, ENQUEUED_AT),
                asText(attributes, VEHICLE_PLATE),
                asText(attributes, COMPLAINT),
                asText(attributes, MECHANIC),
                asText(attributes, DIAGNOSIS_DESCRIPTION),
                diagnosedItems(attributes.get(DIAGNOSED_ITEMS)),
                repairItems(attributes.get(REPAIR_ITEMS)),
                asLong(attributes, VERSION),
                asInstant(attributes, UPDATED_AT));
    }

    public static Map<String, AttributeValue> toStepItem(Long workOrderId, ExecutionStep step) {
        Map<String, AttributeValue> attributes = new HashMap<>();
        attributes.put(PK, text(partitionOf(workOrderId)));
        attributes.put(SK, text(stepSortKey(step.occurredAt(), step.action())));
        attributes.put(OCCURRED_AT, text(step.occurredAt().toString()));
        attributes.put(ACTION, text(step.action()));
        putIfPresent(attributes, STATUS, step.status() == null ? null : step.status().name());
        putIfPresent(attributes, DETAIL, step.detail());
        putIfPresent(attributes, ACTOR, step.actor());
        return attributes;
    }

    public static Map<String, AttributeValue> toInboxItem(Long workOrderId, UUID messageId, String type,
                                                          Instant processedAt, Instant expiresAt) {
        Map<String, AttributeValue> attributes = new HashMap<>();
        attributes.put(PK, text(partitionOf(workOrderId)));
        attributes.put(SK, text(inboxSortKey(messageId)));
        attributes.put(MESSAGE_ID, text(messageId.toString()));
        attributes.put(PROCESSED_AT, text(processedAt.toString()));
        attributes.put(EXPIRES_AT, number(expiresAt.getEpochSecond()));
        putIfPresent(attributes, TYPE, type);
        return attributes;
    }

    public static Map<String, AttributeValue> toOutboxItem(ExecutionEvent event, String payload) {
        SagaEnvelope envelope = event.envelope();
        Map<String, AttributeValue> attributes = new HashMap<>();
        attributes.put(PK, text(partitionOf(envelope.workOrderId())));
        attributes.put(SK, text(outboxSortKey(envelope.messageId())));
        attributes.put(GSI2PK, text(PENDING_PARTITION));
        attributes.put(GSI2SK, text(envelope.occurredAt().toString()));
        attributes.put(MESSAGE_ID, text(envelope.messageId().toString()));
        attributes.put(TYPE, text(envelope.type()));
        attributes.put(VERSION, number(envelope.version()));
        attributes.put(OCCURRED_AT, text(envelope.occurredAt().toString()));
        attributes.put(SAGA_ID, text(envelope.sagaId().toString()));
        attributes.put(WORK_ORDER_ID, number(envelope.workOrderId()));
        attributes.put(PAYLOAD, text(payload));
        putIfPresent(attributes, BRANCH_ID, envelope.branchId());
        putIfPresent(attributes, CORRELATION_ID, envelope.correlationId());
        putIfPresent(attributes, CAUSATION_ID, asString(envelope.causationId()));
        return attributes;
    }

    public static PendingEvent toPendingEvent(Map<String, AttributeValue> attributes) {
        return new PendingEvent(
                asUuid(attributes, MESSAGE_ID),
                asText(attributes, TYPE),
                asLong(attributes, VERSION).intValue(),
                asInstant(attributes, OCCURRED_AT),
                asUuid(attributes, SAGA_ID),
                asLong(attributes, WORK_ORDER_ID),
                asText(attributes, BRANCH_ID),
                asText(attributes, CORRELATION_ID),
                asUuid(attributes, CAUSATION_ID),
                asText(attributes, PAYLOAD));
    }

    private static AttributeValue diagnosedList(List<DiagnosedItem> items) {
        List<AttributeValue> entries = new ArrayList<>();
        for (DiagnosedItem item : items) {
            Map<String, AttributeValue> entry = new HashMap<>();
            entry.put(TYPE, text(item.type()));
            entry.put(CATALOG_ID, number(item.catalogId()));
            entry.put(QUANTITY, number(item.quantity()));
            entries.add(AttributeValue.fromM(entry));
        }
        return AttributeValue.fromL(entries);
    }

    private static AttributeValue repairList(List<RepairItem> items) {
        List<AttributeValue> entries = new ArrayList<>();
        for (RepairItem item : items) {
            Map<String, AttributeValue> entry = new HashMap<>();
            entry.put(TYPE, text(item.type()));
            entry.put(CATALOG_ID, number(item.catalogId()));
            entry.put(QUANTITY, number(item.quantity()));
            putIfPresent(entry, DESCRIPTION, item.description());
            entries.add(AttributeValue.fromM(entry));
        }
        return AttributeValue.fromL(entries);
    }

    private static List<DiagnosedItem> diagnosedItems(AttributeValue value) {
        if (value == null || value.l() == null) {
            return List.of();
        }
        List<DiagnosedItem> items = new ArrayList<>();
        for (AttributeValue entry : value.l()) {
            Map<String, AttributeValue> fields = entry.m();
            items.add(new DiagnosedItem(asText(fields, TYPE), asLong(fields, CATALOG_ID),
                    asLong(fields, QUANTITY).intValue()));
        }
        return items;
    }

    private static List<RepairItem> repairItems(AttributeValue value) {
        if (value == null || value.l() == null) {
            return List.of();
        }
        List<RepairItem> items = new ArrayList<>();
        for (AttributeValue entry : value.l()) {
            Map<String, AttributeValue> fields = entry.m();
            items.add(new RepairItem(asText(fields, TYPE), asLong(fields, CATALOG_ID),
                    asText(fields, DESCRIPTION), asLong(fields, QUANTITY).intValue()));
        }
        return items;
    }

    private static void putIfPresent(Map<String, AttributeValue> attributes, String name, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(name, text(value));
        }
    }

    private static AttributeValue text(String value) {
        return AttributeValue.fromS(value);
    }

    private static AttributeValue number(long value) {
        return AttributeValue.fromN(String.valueOf(value));
    }

    private static String asString(UUID value) {
        return value == null ? null : value.toString();
    }

    private static String asText(Map<String, AttributeValue> attributes, String name) {
        AttributeValue value = attributes.get(name);
        return value == null ? null : value.s();
    }

    private static Long asLong(Map<String, AttributeValue> attributes, String name) {
        AttributeValue value = attributes.get(name);
        return value == null || value.n() == null ? null : Long.valueOf(value.n());
    }

    private static Instant asInstant(Map<String, AttributeValue> attributes, String name) {
        String value = asText(attributes, name);
        return value == null ? null : Instant.parse(value);
    }

    private static UUID asUuid(Map<String, AttributeValue> attributes, String name) {
        String value = asText(attributes, name);
        return value == null ? null : UUID.fromString(value);
    }
}
