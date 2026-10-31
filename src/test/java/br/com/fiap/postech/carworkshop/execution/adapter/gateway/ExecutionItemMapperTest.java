package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStep;
import br.com.fiap.postech.carworkshop.execution.domain.message.EmptyPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.ExecutionEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.PendingEvent;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionItemMapperTest {

    // Round-trip tocando TODO campo: e o teste que pega a gravacao que esquece o campo novo, que foi a causa de
    // tres bugs do bloco do OS sem nenhum sintoma no log.
    @Test
    void copiesEveryFieldOfTheStateBothWays() {
        ExecutionItem original = new ExecutionItem(
                ExecutionFixtures.WORK_ORDER_ID,
                ExecutionFixtures.BRANCH,
                ExecutionFixtures.SAGA_ID,
                ExecutionStatus.REPAIR_IN_PROGRESS,
                "parafuso quebrou",
                ExecutionFixtures.NOW,
                ExecutionFixtures.PLATE,
                ExecutionFixtures.COMPLAINT,
                ExecutionFixtures.MECHANIC,
                "Amortecedor dianteiro gasto",
                ExecutionFixtures.diagnosedItems(),
                ExecutionFixtures.repairItems(),
                7L,
                Instant.parse("2026-10-30T13:30:00Z"));

        ExecutionItem restored = ExecutionItemMapper.toDomain(ExecutionItemMapper.toStateItem(original));

        assertEquals(original.workOrderId(), restored.workOrderId());
        assertEquals(original.branchId(), restored.branchId());
        assertEquals(original.sagaId(), restored.sagaId());
        assertEquals(original.status(), restored.status());
        assertEquals(original.statusReason(), restored.statusReason());
        assertEquals(original.enqueuedAt(), restored.enqueuedAt());
        assertEquals(original.vehiclePlate(), restored.vehiclePlate());
        assertEquals(original.complaint(), restored.complaint());
        assertEquals(original.mechanic(), restored.mechanic());
        assertEquals(original.diagnosisDescription(), restored.diagnosisDescription());
        assertEquals(original.diagnosedItems(), restored.diagnosedItems());
        assertEquals(original.repairItems(), restored.repairItems());
        assertEquals(original.version(), restored.version());
        assertEquals(original.updatedAt(), restored.updatedAt());
        assertEquals(original, restored);
    }

    @Test
    void copiesAnItemThatHasOnlyTheMandatoryFields() {
        ExecutionItem original = ExecutionFixtures.queued();

        ExecutionItem restored = ExecutionItemMapper.toDomain(ExecutionItemMapper.toStateItem(original));

        assertEquals(original, restored);
        assertNull(restored.mechanic());
        assertTrue(restored.diagnosedItems().isEmpty());
    }

    private static ExecutionItem itemWithItems() {
        return ExecutionFixtures.queued()
                .startDiagnosis(ExecutionFixtures.MECHANIC, ExecutionFixtures.NOW).orElseThrow()
                .completeDiagnosis("Amortecedor gasto", ExecutionFixtures.diagnosedItems(),
                        ExecutionFixtures.NOW).orElseThrow();
    }

    @Test
    void writesTheKeysAndTheQueueIndexOfTheState() {
        Map<String, AttributeValue> item = ExecutionItemMapper.toStateItem(ExecutionFixtures.queued());

        assertEquals("WO#42", item.get(ExecutionItemMapper.PK).s());
        assertEquals("STATE", item.get(ExecutionItemMapper.SK).s());
        assertEquals("SP-01#DIAGNOSIS_QUEUED", item.get(ExecutionItemMapper.GSI1PK).s());
        assertEquals("2026-10-30T12:00:00Z", item.get(ExecutionItemMapper.GSI1SK).s());
    }

    // A chave do indice e reescrita com o valor terminal, nunca removida: remover tiraria o item do indice e a
    // prova de que ele saiu da fila passaria pelo motivo errado.
    @Test
    void keepsTheQueueIndexKeyOnATerminalState() {
        ExecutionItem canceled = ExecutionFixtures.queued()
                .cancel("CANCELED_BY_STAFF", ExecutionFixtures.NOW).orElseThrow();

        Map<String, AttributeValue> item = ExecutionItemMapper.toStateItem(canceled);

        assertEquals("SP-01#CANCELED", item.get(ExecutionItemMapper.GSI1PK).s());
        assertTrue(item.containsKey(ExecutionItemMapper.GSI1SK));
    }

    @Test
    void writesTheStepWithADeterministicSortKey() {
        ExecutionStep step = new ExecutionStep(ExecutionFixtures.NOW, SagaMessageType.CANCEL_DIAGNOSIS,
                ExecutionStatus.CANCELED, "CANCELED_BY_STAFF", ExecutionStep.SAGA);

        Map<String, AttributeValue> item = ExecutionItemMapper.toStepItem(ExecutionFixtures.WORK_ORDER_ID, step);

        assertEquals("WO#42", item.get(ExecutionItemMapper.PK).s());
        assertEquals("STEP#2026-10-30T12:00:00Z#CancelDiagnosis", item.get(ExecutionItemMapper.SK).s());
        assertEquals("CANCELED", item.get(ExecutionItemMapper.STATUS).s());
        assertEquals("CANCELED_BY_STAFF", item.get("detail").s());
        assertEquals("saga", item.get("actor").s());
    }

    @Test
    void writesAStepWithoutAStatusWhenNothingChanged() {
        ExecutionStep step = new ExecutionStep(ExecutionFixtures.NOW, SagaMessageType.CANCEL_DIAGNOSIS, null,
                "PAYMENT_EXPIRED", ExecutionStep.SAGA);

        Map<String, AttributeValue> item = ExecutionItemMapper.toStepItem(ExecutionFixtures.WORK_ORDER_ID, step);

        assertFalse(item.containsKey(ExecutionItemMapper.STATUS));
    }

    // O TTL do DynamoDB le segundos de epoca num atributo numerico: gravar o instante como texto deixaria a
    // linha da inbox para sempre, sem erro em lugar nenhum.
    @Test
    void writesTheInboxExpiryAsEpochSeconds() {
        Map<String, AttributeValue> item = ExecutionItemMapper.toInboxItem(ExecutionFixtures.WORK_ORDER_ID,
                ExecutionFixtures.MESSAGE_ID, SagaMessageType.CANCEL_DIAGNOSIS, ExecutionFixtures.NOW,
                ExecutionFixtures.NOW.plusSeconds(60));

        assertEquals("INBOX#" + ExecutionFixtures.MESSAGE_ID, item.get(ExecutionItemMapper.SK).s());
        assertEquals(String.valueOf(ExecutionFixtures.NOW.plusSeconds(60).getEpochSecond()),
                item.get("expiresAt").n());
    }

    // Atributo numerico ausente volta no padrao em vez de estourar NullPointerException ao desembrulhar: um
    // item gravado por outra versao do esquema nao pode derrubar o relay nem a leitura da fila.
    @Test
    void readsAnAbsentNumberAsItsDefaultInsteadOfThrowing() {
        Map<String, AttributeValue> outbox = new HashMap<>(ExecutionItemMapper.toOutboxItem(
                ExecutionEvent.replyTo(ExecutionFixtures.command(SagaMessageType.CANCEL_DIAGNOSIS),
                        SagaMessageType.DIAGNOSIS_CANCELED, new EmptyPayload(), ExecutionFixtures.NOW), "{}"));
        outbox.remove(ExecutionItemMapper.VERSION);

        assertEquals(1, ExecutionItemMapper.toPendingEvent(outbox).version());

        Map<String, AttributeValue> state = new HashMap<>(
                ExecutionItemMapper.toStateItem(itemWithItems()));
        state.put("diagnosedItems", AttributeValue.fromL(List.of(AttributeValue.fromM(Map.of(
                "type", AttributeValue.fromS("PART"), "catalogId", AttributeValue.fromN("12"))))));

        assertEquals(1, ExecutionItemMapper.toDomain(state).diagnosedItems().get(0).quantity());
    }

    @Test
    void copiesEveryFieldOfTheOutboxBothWays() {
        ExecutionEvent event = ExecutionEvent.replyTo(
                ExecutionFixtures.command(SagaMessageType.CANCEL_DIAGNOSIS),
                SagaMessageType.DIAGNOSIS_CANCELED, new EmptyPayload(), ExecutionFixtures.NOW);

        Map<String, AttributeValue> item = ExecutionItemMapper.toOutboxItem(event, "{}");
        PendingEvent restored = ExecutionItemMapper.toPendingEvent(item);

        assertEquals("PENDING", item.get(ExecutionItemMapper.GSI2PK).s());
        assertEquals("2026-10-30T12:00:00Z", item.get(ExecutionItemMapper.GSI2SK).s());
        assertEquals(event.envelope().messageId(), restored.messageId());
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED, restored.type());
        assertEquals(1, restored.version());
        assertEquals(ExecutionFixtures.NOW, restored.occurredAt());
        assertEquals(ExecutionFixtures.SAGA_ID, restored.sagaId());
        assertEquals(ExecutionFixtures.WORK_ORDER_ID, restored.workOrderId());
        assertEquals(ExecutionFixtures.BRANCH, restored.branchId());
        assertEquals(ExecutionFixtures.CORRELATION_ID, restored.correlationId());
        assertEquals(ExecutionFixtures.MESSAGE_ID, restored.causationId());
        assertEquals("{}", restored.payload());
    }
}
