package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.ExecutionItemMapper;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.MechanicUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionItemRepositoryPort;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = ExecutionMessagingResource.class, restrictToAnnotatedClass = true)
class ExecutionMessagingIntegrationTest {

    @Inject
    ExecutionCommandsConsumer consumer;

    @Inject
    OutboxRelayService relay;

    @Inject
    ExecutionItemRepositoryPort items;

    @Inject
    MechanicUseCase mechanics;

    @Inject
    SqsClient sqs;

    @Inject
    DynamoDbClient dynamo;

    @ConfigProperty(name = "execution.commands.queue-url")
    String queueUrl;

    @Test
    void startDiagnosisCommand_queuesTheWorkOrderAndRecordsTheInbox() {
        long workOrderId = 6101L;
        UUID messageId = UUID.randomUUID();

        send(command(SagaMessageType.START_DIAGNOSIS, messageId, workOrderId, "{}"), workOrderId);
        consumer.poll();

        ExecutionItem item = items.findByWorkOrderId(workOrderId).orElseThrow();
        assertEquals(ExecutionStatus.DIAGNOSIS_QUEUED, item.status());
        assertEquals(1, countOf(workOrderId, "INBOX#"));
        assertEquals(1, countOf(workOrderId, "STEP#"));
        assertEquals(0, countOf(workOrderId, "OUTBOX#"));
        assertEquals(0, messagesLeft());
    }

    // Reentrega com o MESMO messageId: a inbox decide, e o efeito acontece uma vez so - nem segundo item, nem
    // segunda resposta para o orquestrador.
    @Test
    void theSameCommandTwice_advancesOnceAndAnswersOnce() {
        long workOrderId = 6102L;
        UUID messageId = UUID.randomUUID();
        String body = command(SagaMessageType.CANCEL_DIAGNOSIS, messageId, workOrderId,
                "{\"reason\":\"PAYMENT_EXPIRED\"}");

        send(body, workOrderId);
        consumer.poll();
        send(body, workOrderId);
        consumer.poll();

        assertEquals(1, countOf(workOrderId, "INBOX#"));
        assertEquals(1, countOf(workOrderId, "OUTBOX#"));
        assertEquals(1, countOf(workOrderId, "STEP#"));
        assertEquals(0, messagesLeft());
    }

    // A divida que o bloco fecha: sem item nenhum o cancelamento responde do mesmo jeito.
    @Test
    void cancelDiagnosis_withoutAnyDiagnosis_stillWritesTheReply() {
        long workOrderId = 6103L;

        send(command(SagaMessageType.CANCEL_DIAGNOSIS, UUID.randomUUID(), workOrderId,
                "{\"reason\":\"CANCELED_BY_STAFF\"}"), workOrderId);
        consumer.poll();

        assertTrue(items.findByWorkOrderId(workOrderId).isEmpty());
        assertEquals(1, countOf(workOrderId, "OUTBOX#"));
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED, outboxTypeOf(workOrderId));
        assertEquals(0, messagesLeft());
    }

    @Test
    void cancelDiagnosis_afterTheWorkOrderWasQueued_cancelsTheItemAndAnswers() {
        long workOrderId = 6104L;
        send(command(SagaMessageType.START_DIAGNOSIS, UUID.randomUUID(), workOrderId, "{}"), workOrderId);
        consumer.poll();

        send(command(SagaMessageType.CANCEL_DIAGNOSIS, UUID.randomUUID(), workOrderId,
                "{\"reason\":\"REJECTED_BY_CUSTOMER\"}"), workOrderId);
        consumer.poll();

        assertEquals(ExecutionStatus.CANCELED, items.findByWorkOrderId(workOrderId).orElseThrow().status());
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED, outboxTypeOf(workOrderId));
        assertEquals(0, messagesLeft());
    }

    @Test
    void relay_publishesThePendingEventAndEmptiesThePendingIndex() {
        long workOrderId = 6105L;
        send(command(SagaMessageType.CANCEL_DIAGNOSIS, UUID.randomUUID(), workOrderId,
                "{\"reason\":\"APPROVAL_EXPIRED\"}"), workOrderId);
        consumer.poll();
        assertEquals(1, pendingOutboxCount(workOrderId));

        relay.drain();

        assertNotNull(sentAtOf(workOrderId));
        assertEquals(0, pendingOutboxCount(workOrderId));
    }

    // A prova da fila por filial: tres ordens entram fora de ordem e a consulta devolve pela espera, nao pelo
    // id. E a particao do indice separa as filiais.
    @Test
    void theBranchQueueComesBackOrderedByTheWaitingTime() {
        enqueueAt(6203L, "RJ-02");
        enqueueAt(6201L, "RJ-02");
        enqueueAt(6202L, "RJ-02");
        enqueueAt(6301L, "MG-03");

        List<ExecutionItem> queue = items.queueOf("RJ-02", ExecutionStatus.DIAGNOSIS_QUEUED, 25);

        assertEquals(List.of(6203L, 6201L, 6202L),
                queue.stream().map(ExecutionItem::workOrderId).toList());
        assertTrue(queue.get(0).enqueuedAt().isBefore(queue.get(1).enqueuedAt()));
        assertEquals(List.of(6301L), items.queueOf("MG-03", ExecutionStatus.DIAGNOSIS_QUEUED, 25)
                .stream().map(ExecutionItem::workOrderId).toList());
    }

    // Estado terminal muda de particao no indice em vez de sair dele: a ordem deixa a fila de diagnostico e
    // continua consultavel pelo status novo.
    @Test
    void aCanceledItemLeavesTheDiagnosisQueueAndStaysReadableByItsNewStatus() {
        long workOrderId = 6204L;
        enqueueAt(workOrderId, "PR-04");

        send(command(SagaMessageType.CANCEL_DIAGNOSIS, UUID.randomUUID(), workOrderId,
                "{\"reason\":\"CANCELED_BY_STAFF\"}"), workOrderId);
        consumer.poll();

        assertTrue(items.queueOf("PR-04", ExecutionStatus.DIAGNOSIS_QUEUED, 25).isEmpty());
        assertEquals(1, items.queueOf("PR-04", ExecutionStatus.CANCELED, 25).size());
    }

    // Transacao cancelada nao grava nada: a condicao do estado falha e o passo e o evento tambem ficam de fora.
    @Test
    void aRefusedTransitionWritesNeitherTheStepNorTheEvent() {
        long workOrderId = 6205L;
        enqueueAt(workOrderId, "SC-05");
        long stepsBefore = countOf(workOrderId, "STEP#");

        send(command(SagaMessageType.START_REPAIR, UUID.randomUUID(), workOrderId,
                "{\"items\":[{\"type\":\"PART\",\"catalogId\":12,\"quantity\":1}]}"), workOrderId);
        consumer.poll();

        assertEquals(ExecutionStatus.DIAGNOSIS_QUEUED,
                items.findByWorkOrderId(workOrderId).orElseThrow().status());
        assertEquals(stepsBefore + 1, countOf(workOrderId, "STEP#"));
        assertEquals(0, countOf(workOrderId, "OUTBOX#"));
        assertEquals(0, messagesLeft());
    }

    @Test
    void theMechanicWalksTheWorkOrderFromTheQueueToThePivot() {
        long workOrderId = 6206L;
        enqueueAt(workOrderId, "BA-06");

        mechanics.startDiagnosis(workOrderId, "mecanico.joana", "EVIDENCIA-B6-INT");
        assertEquals(ExecutionStatus.DIAGNOSIS_IN_PROGRESS,
                items.findByWorkOrderId(workOrderId).orElseThrow().status());
        assertEquals(SagaMessageType.DIAGNOSIS_STARTED, outboxTypeOf(workOrderId));

        relay.drain();
        mechanics.failDiagnosis(workOrderId, "sem reparo viavel", "mecanico.joana", null);

        ExecutionItem item = items.findByWorkOrderId(workOrderId).orElseThrow();
        assertEquals(ExecutionStatus.DIAGNOSIS_FAILED, item.status());
        assertEquals("sem reparo viavel", item.statusReason());
        assertEquals("mecanico.joana", item.mechanic());
        assertFalse(itemsOf(workOrderId, "STEP#").isEmpty());
    }

    private void enqueueAt(long workOrderId, String branch) {
        send(commandOf(SagaMessageType.START_DIAGNOSIS, UUID.randomUUID(), workOrderId, branch, "{}"),
                workOrderId);
        consumer.poll();
    }

    private void send(String body, long workOrderId) {
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .messageGroupId(String.valueOf(workOrderId))
                .messageDeduplicationId(UUID.randomUUID().toString())
                .build());
    }

    private static String command(String type, UUID messageId, long workOrderId, String payload) {
        return commandOf(type, messageId, workOrderId, "SP-01", payload);
    }

    private static String commandOf(String type, UUID messageId, long workOrderId, String branch,
                                    String payload) {
        return "{\"messageId\":\"" + messageId + "\",\"type\":\"" + type + "\",\"version\":1,"
                + "\"occurredAt\":\"2026-10-30T12:00:00Z\",\"sagaId\":\"" + UUID.randomUUID() + "\","
                + "\"workOrderId\":" + workOrderId + ",\"branchId\":\"" + branch + "\","
                + "\"correlationId\":\"EVIDENCIA-B6-INT\",\"payload\":" + payload + "}";
    }

    private List<Map<String, AttributeValue>> itemsOf(long workOrderId, String prefix) {
        return dynamo.query(QueryRequest.builder()
                .tableName(ExecutionMessagingResource.TABLE)
                .keyConditionExpression("#partition = :partition AND begins_with(#sort, :prefix)")
                .expressionAttributeNames(Map.of("#partition", ExecutionItemMapper.PK,
                        "#sort", ExecutionItemMapper.SK))
                .expressionAttributeValues(Map.of(
                        ":partition", AttributeValue.fromS(ExecutionItemMapper.partitionOf(workOrderId)),
                        ":prefix", AttributeValue.fromS(prefix)))
                .build()).items();
    }

    private long countOf(long workOrderId, String prefix) {
        return itemsOf(workOrderId, prefix).size();
    }

    private String outboxTypeOf(long workOrderId) {
        return itemsOf(workOrderId, "OUTBOX#").get(0).get("type").s();
    }

    private String sentAtOf(long workOrderId) {
        AttributeValue value = itemsOf(workOrderId, "OUTBOX#").get(0).get(ExecutionItemMapper.SENT_AT);
        return value == null ? null : value.s();
    }

    // A particao do indice de pendencias e global, entao a contagem se filtra pela ordem de servico: sem isso o
    // teste leria o que as outras ordens da classe deixaram pendente.
    private long pendingOutboxCount(long workOrderId) {
        return dynamo.query(QueryRequest.builder()
                .tableName(ExecutionMessagingResource.TABLE)
                .indexName(ExecutionItemMapper.OUTBOX_PENDING_INDEX)
                .keyConditionExpression("#partition = :partition")
                .expressionAttributeNames(Map.of("#partition", ExecutionItemMapper.GSI2PK))
                .expressionAttributeValues(Map.of(":partition",
                        AttributeValue.fromS(ExecutionItemMapper.PENDING_PARTITION)))
                .build()).items().stream()
                .filter(item -> String.valueOf(workOrderId).equals(item.get("workOrderId").n()))
                .count();
    }

    private int messagesLeft() {
        Map<QueueAttributeName, String> attributes = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                        .build()).attributes();
        return Integer.parseInt(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                + Integer.parseInt(
                        attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
    }
}
