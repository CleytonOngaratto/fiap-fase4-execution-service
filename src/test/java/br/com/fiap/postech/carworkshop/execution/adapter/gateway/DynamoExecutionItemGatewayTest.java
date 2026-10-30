package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStep;
import br.com.fiap.postech.carworkshop.execution.domain.message.EmptyPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.ExecutionEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWrite;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamoExecutionItemGatewayTest {

    private static final String TABLE = "execution";
    private static final String FAILED = "ConditionalCheckFailed";
    private static final String NONE = "None";

    private DynamoDbClient dynamo;
    private DynamoExecutionItemGateway gateway;

    @BeforeEach
    void setUp() {
        dynamo = mock(DynamoDbClient.class);
        gateway = new DynamoExecutionItemGateway();
        gateway.dynamo = dynamo;
        gateway.codec = TestCodec.codec();
        gateway.tableName = TABLE;
    }

    // Leitura forte: o caso de uso le e escreve condicionado ao que leu, e uma leitura eventual faria a condicao
    // falhar sem ninguem ter mexido no item.
    @Test
    void readsTheStateWithAStronglyConsistentGet() {
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder()
                .item(ExecutionItemMapper.toStateItem(ExecutionFixtures.queued()))
                .build());

        Optional<ExecutionItem> found = gateway.findByWorkOrderId(ExecutionFixtures.WORK_ORDER_ID);

        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamo).getItem(captor.capture());
        assertTrue(captor.getValue().consistentRead());
        assertEquals(TABLE, captor.getValue().tableName());
        assertEquals("WO#42", captor.getValue().key().get(ExecutionItemMapper.PK).s());
        assertTrue(found.isPresent());
        assertEquals(ExecutionStatus.DIAGNOSIS_QUEUED, found.orElseThrow().status());
    }

    @Test
    void reportsNoStateForAWorkOrderThatWasNeverQueued() {
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());

        assertTrue(gateway.findByWorkOrderId(ExecutionFixtures.WORK_ORDER_ID).isEmpty());
    }

    @Test
    void readsTheBranchQueueOrderedByTheWaitingTime() {
        when(dynamo.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(ExecutionItemMapper.toStateItem(ExecutionFixtures.queued()))
                .build());

        List<ExecutionItem> queue = gateway.queueOf(ExecutionFixtures.BRANCH,
                ExecutionStatus.DIAGNOSIS_QUEUED, 25);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamo).query(captor.capture());
        QueryRequest request = captor.getValue();
        assertEquals(ExecutionItemMapper.BRANCH_STATUS_INDEX, request.indexName());
        assertEquals("#partition = :partition", request.keyConditionExpression());
        assertEquals("SP-01#DIAGNOSIS_QUEUED",
                request.expressionAttributeValues().get(":partition").s());
        assertTrue(request.scanIndexForward());
        assertEquals(25, request.limit());
        assertEquals(1, queue.size());
    }

    @Test
    void createsTheStateTheStepAndTheInboxInOneTransaction() {
        ExecutionWriteResult result = gateway.apply(ExecutionWrite.create(ExecutionFixtures.queued(),
                step(ExecutionStatus.DIAGNOSIS_QUEUED), ExecutionFixtures.MESSAGE_ID,
                ExecutionFixtures.NOW.plusSeconds(60)));

        assertEquals(ExecutionWriteResult.APPLIED, result);
        List<TransactWriteItem> writes = captured();
        assertEquals(3, writes.size());
        Put state = writes.get(0).put();
        assertEquals("attribute_not_exists(#partition)", state.conditionExpression());
        assertEquals(ExecutionItemMapper.PK, state.expressionAttributeNames().get("#partition"));
        assertEquals("STATE", state.item().get(ExecutionItemMapper.SK).s());
        assertTrue(writes.get(1).put().item().get(ExecutionItemMapper.SK).s().startsWith("STEP#"));
        Put inbox = writes.get(2).put();
        assertEquals("attribute_not_exists(#sort)", inbox.conditionExpression());
        assertTrue(inbox.item().get(ExecutionItemMapper.SK).s().startsWith("INBOX#"));
    }

    @Test
    void conditionsTheTransitionOnTheStatusAndTheVersionItRead() {
        ExecutionItem previous = ExecutionFixtures.queued();
        ExecutionItem next = previous.cancel("CANCELED_BY_STAFF", ExecutionFixtures.NOW).orElseThrow();

        gateway.apply(ExecutionWrite.transitionFromCommand(previous, next,
                step(ExecutionStatus.CANCELED), event(), ExecutionFixtures.MESSAGE_ID,
                ExecutionFixtures.NOW.plusSeconds(60)));

        List<TransactWriteItem> writes = captured();
        assertEquals(4, writes.size());
        Put state = writes.get(0).put();
        assertEquals("#status = :status AND #version = :version", state.conditionExpression());
        assertEquals("DIAGNOSIS_QUEUED", state.expressionAttributeValues().get(":status").s());
        assertEquals("1", state.expressionAttributeValues().get(":version").n());
        assertEquals("CANCELED", state.item().get(ExecutionItemMapper.STATUS).s());
        assertTrue(writes.get(2).put().item().get(ExecutionItemMapper.SK).s().startsWith("OUTBOX#"));
    }

    // O evento so existe como item dentro da transacao: nao ha caminho deste gateway para o SNS, e e isso que
    // garante que nenhuma publicacao acontece fora da escrita do estado.
    @Test
    void writesTheEventIntoTheSameTransactionAndNowhereElse() {
        gateway.apply(ExecutionWrite.withoutStateChange(ExecutionFixtures.WORK_ORDER_ID,
                step(null), event(), ExecutionFixtures.MESSAGE_ID, ExecutionFixtures.NOW.plusSeconds(60)));

        List<TransactWriteItem> writes = captured();
        assertEquals(3, writes.size());
        assertTrue(writes.get(0).put().item().get(ExecutionItemMapper.SK).s().startsWith("STEP#"));
        Put outbox = writes.get(1).put();
        assertTrue(outbox.item().get(ExecutionItemMapper.SK).s().startsWith("OUTBOX#"));
        assertEquals("PENDING", outbox.item().get(ExecutionItemMapper.GSI2PK).s());
        assertEquals("{}", outbox.item().get("payload").s());
        assertTrue(writes.get(2).put().item().get(ExecutionItemMapper.SK).s().startsWith("INBOX#"));
    }

    @Test
    void writesWithoutAnInboxRowWhenTheChangeDidNotComeFromACommand() {
        ExecutionItem previous = ExecutionFixtures.queued();
        ExecutionItem next = previous.startDiagnosis(ExecutionFixtures.MECHANIC, ExecutionFixtures.NOW)
                .orElseThrow();

        gateway.apply(ExecutionWrite.transition(previous, next,
                step(ExecutionStatus.DIAGNOSIS_IN_PROGRESS), event()));

        List<TransactWriteItem> writes = captured();
        assertEquals(3, writes.size());
        assertFalse(writes.get(2).put().item().get(ExecutionItemMapper.SK).s().startsWith("INBOX#"));
    }

    @Test
    void reportsADuplicateWhenTheInboxConditionFails() {
        cancelWith(NONE, NONE, FAILED);

        assertEquals(ExecutionWriteResult.DUPLICATE, gateway.apply(create()));
    }

    @Test
    void reportsAConflictWhenTheStateMovedUnderIt() {
        cancelWith(FAILED, NONE, NONE);

        assertEquals(ExecutionWriteResult.CONFLICT, gateway.apply(create()));
    }

    // Se as duas condicoes falharem, a inbox vence: a mensagem ja foi processada, e chamar de conflito faria o
    // chamador recusar algo que tinha dado certo.
    @Test
    void reportsADuplicateWhenBothConditionsFail() {
        cancelWith(FAILED, NONE, FAILED);

        assertEquals(ExecutionWriteResult.DUPLICATE, gateway.apply(create()));
    }

    @Test
    void propagatesACancellationThatIsNotAConditionFailure() {
        cancelWith(NONE, "ThroughputExceeded", NONE);

        assertThrows(TransactionCanceledException.class, () -> gateway.apply(create()));
    }

    private void cancelWith(String... codes) {
        List<CancellationReason> reasons = java.util.Arrays.stream(codes)
                .map(code -> CancellationReason.builder().code(code).build())
                .toList();
        when(dynamo.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenThrow(TransactionCanceledException.builder().cancellationReasons(reasons).build());
    }

    private List<TransactWriteItem> captured() {
        ArgumentCaptor<TransactWriteItemsRequest> captor =
                ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamo).transactWriteItems(captor.capture());
        return captor.getValue().transactItems();
    }

    private static ExecutionWrite create() {
        return ExecutionWrite.create(ExecutionFixtures.queued(), step(ExecutionStatus.DIAGNOSIS_QUEUED),
                ExecutionFixtures.MESSAGE_ID, ExecutionFixtures.NOW.plusSeconds(60));
    }

    private static ExecutionStep step(ExecutionStatus status) {
        return new ExecutionStep(ExecutionFixtures.NOW, SagaMessageType.CANCEL_DIAGNOSIS, status, "motivo",
                ExecutionStep.SAGA);
    }

    private static ExecutionEvent event() {
        return ExecutionEvent.replyTo(ExecutionFixtures.command(SagaMessageType.CANCEL_DIAGNOSIS),
                SagaMessageType.DIAGNOSIS_CANCELED, new EmptyPayload(), ExecutionFixtures.NOW);
    }
}
