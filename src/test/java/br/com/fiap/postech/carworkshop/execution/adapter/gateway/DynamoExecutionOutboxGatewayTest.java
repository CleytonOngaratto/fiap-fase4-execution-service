package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.message.EmptyPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.ExecutionEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.PendingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamoExecutionOutboxGatewayTest {

    private DynamoDbClient dynamo;
    private DynamoExecutionOutboxGateway gateway;

    @BeforeEach
    void setUp() {
        dynamo = mock(DynamoDbClient.class);
        gateway = new DynamoExecutionOutboxGateway();
        gateway.dynamo = dynamo;
        gateway.tableName = "execution";
    }

    @Test
    void readsThePendingEventsOldestFirst() {
        ExecutionEvent event = ExecutionEvent.replyTo(
                ExecutionFixtures.command(SagaMessageType.CANCEL_DIAGNOSIS),
                SagaMessageType.DIAGNOSIS_CANCELED, new EmptyPayload(), ExecutionFixtures.NOW);
        when(dynamo.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(ExecutionItemMapper.toOutboxItem(event, "{}"))
                .build());

        List<PendingEvent> pending = gateway.claimPending(10);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamo).query(captor.capture());
        assertEquals(ExecutionItemMapper.OUTBOX_PENDING_INDEX, captor.getValue().indexName());
        assertEquals("PENDING", captor.getValue().expressionAttributeValues().get(":partition").s());
        assertTrue(captor.getValue().scanIndexForward());
        assertEquals(10, captor.getValue().limit());
        assertEquals(1, pending.size());
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED, pending.get(0).type());
    }

    // Tirar as chaves do indice e o que faz a mensagem sair da fila de pendencias; a condicao resolve a corrida
    // entre replicas, porque o DynamoDB nao tem SKIP LOCKED.
    @Test
    void marksTheEventAsSentAndTakesItOutOfThePendingIndex() {
        boolean marked = gateway.markSent(ExecutionFixtures.WORK_ORDER_ID, ExecutionFixtures.MESSAGE_ID,
                ExecutionFixtures.NOW);

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamo).updateItem(captor.capture());
        UpdateItemRequest request = captor.getValue();
        assertEquals("SET #sentAt = :sentAt REMOVE #pendingPartition, #pendingSort",
                request.updateExpression());
        assertEquals("attribute_not_exists(#sentAt)", request.conditionExpression());
        assertEquals("2026-10-30T12:00:00Z", request.expressionAttributeValues().get(":sentAt").s());
        assertEquals("OUTBOX#" + ExecutionFixtures.MESSAGE_ID,
                request.key().get(ExecutionItemMapper.SK).s());
        assertTrue(marked);
    }

    @Test
    void reportsThatAnotherReplicaHadAlreadyMarkedTheEvent() {
        when(dynamo.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("already sent").build());

        assertFalse(gateway.markSent(ExecutionFixtures.WORK_ORDER_ID, ExecutionFixtures.MESSAGE_ID,
                ExecutionFixtures.NOW));
    }
}
