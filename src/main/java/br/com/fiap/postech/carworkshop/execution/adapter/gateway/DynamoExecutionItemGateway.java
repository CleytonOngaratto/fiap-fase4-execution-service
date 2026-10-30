package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStep;
import br.com.fiap.postech.carworkshop.execution.domain.message.ExecutionEvent;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionItemRepositoryPort;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWrite;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class DynamoExecutionItemGateway implements ExecutionItemRepositoryPort {

    private static final String CONDITION_FAILED = "ConditionalCheckFailed";

    @Inject
    DynamoDbClient dynamo;

    @Inject
    SagaEnvelopeCodec codec;

    @ConfigProperty(name = "execution.table-name")
    String tableName;

    // Leitura forte: o caso de uso le o item e escreve condicionado ao que leu, e uma leitura eventual faria a
    // condicao falhar sem ninguem ter mexido no item.
    @Override
    public Optional<ExecutionItem> findByWorkOrderId(Long workOrderId) {
        GetItemResponse response = dynamo.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(stateKey(workOrderId))
                .consistentRead(true)
                .build());
        return response.hasItem() ? Optional.of(ExecutionItemMapper.toDomain(response.item())) : Optional.empty();
    }

    @Override
    public List<ExecutionItem> queueOf(String branchId, ExecutionStatus status, int limit) {
        return dynamo.query(QueryRequest.builder()
                        .tableName(tableName)
                        .indexName(ExecutionItemMapper.BRANCH_STATUS_INDEX)
                        .keyConditionExpression("#partition = :partition")
                        .expressionAttributeNames(Map.of("#partition", ExecutionItemMapper.GSI1PK))
                        .expressionAttributeValues(Map.of(":partition",
                                AttributeValue.fromS(ExecutionItemMapper.queuePartitionOf(branchId, status))))
                        .scanIndexForward(true)
                        .limit(limit)
                        .build())
                .items().stream()
                .map(ExecutionItemMapper::toDomain)
                .toList();
    }

    @Override
    public ExecutionWriteResult apply(ExecutionWrite write) {
        List<TransactWriteItem> writes = new ArrayList<>();
        int stateIndex = -1;
        if (write.createsState()) {
            stateIndex = writes.size();
            writes.add(stateIfAbsent(write.next()));
        } else if (write.updatesState()) {
            stateIndex = writes.size();
            writes.add(stateIfUnchanged(write.previous(), write.next()));
        }
        writes.add(step(write.workOrderId(), write.step()));
        if (write.event() != null) {
            writes.add(outbox(write.event()));
        }
        int inboxIndex = -1;
        if (write.inboxMessageId() != null) {
            inboxIndex = writes.size();
            writes.add(inbox(write));
        }

        try {
            dynamo.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(writes).build());
            return ExecutionWriteResult.APPLIED;
        } catch (TransactionCanceledException e) {
            return outcomeOf(e, stateIndex, inboxIndex);
        }
    }

    // Put do item inteiro, nao Update de campos: assim campo novo do agregado nunca fica de fora da gravacao.
    private TransactWriteItem stateIfAbsent(ExecutionItem state) {
        return TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableName)
                        .item(ExecutionItemMapper.toStateItem(state))
                        .conditionExpression("attribute_not_exists(#partition)")
                        .expressionAttributeNames(Map.of("#partition", ExecutionItemMapper.PK))
                        .build())
                .build();
    }

    private TransactWriteItem stateIfUnchanged(ExecutionItem previous, ExecutionItem next) {
        return TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableName)
                        .item(ExecutionItemMapper.toStateItem(next))
                        .conditionExpression("#status = :status AND #version = :version")
                        .expressionAttributeNames(Map.of(
                                "#status", ExecutionItemMapper.STATUS,
                                "#version", ExecutionItemMapper.VERSION))
                        .expressionAttributeValues(Map.of(
                                ":status", AttributeValue.fromS(previous.status().name()),
                                ":version", AttributeValue.fromN(String.valueOf(previous.version()))))
                        .build())
                .build();
    }

    private TransactWriteItem step(Long workOrderId, ExecutionStep step) {
        return TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableName)
                        .item(ExecutionItemMapper.toStepItem(workOrderId, step))
                        .build())
                .build();
    }

    private TransactWriteItem outbox(ExecutionEvent event) {
        return TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableName)
                        .item(ExecutionItemMapper.toOutboxItem(event, codec.writePayload(event.payload())))
                        .build())
                .build();
    }

    private TransactWriteItem inbox(ExecutionWrite write) {
        return TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableName)
                        .item(ExecutionItemMapper.toInboxItem(write.workOrderId(), write.inboxMessageId(),
                                write.step().action(), write.step().occurredAt(), write.inboxExpiresAt()))
                        .conditionExpression("attribute_not_exists(#sort)")
                        .expressionAttributeNames(Map.of("#sort", ExecutionItemMapper.SK))
                        .build())
                .build();
    }

    // A inbox vence o estado: se as duas condicoes falharem, a mensagem ja foi processada, e tratar como
    // conflito faria o consumidor responder 409 para algo que ja tinha dado certo.
    private static ExecutionWriteResult outcomeOf(TransactionCanceledException cancellation, int stateIndex,
                                                  int inboxIndex) {
        List<CancellationReason> reasons = cancellation.cancellationReasons();
        if (failedAt(reasons, inboxIndex)) {
            return ExecutionWriteResult.DUPLICATE;
        }
        if (failedAt(reasons, stateIndex)) {
            return ExecutionWriteResult.CONFLICT;
        }
        throw cancellation;
    }

    private static boolean failedAt(List<CancellationReason> reasons, int index) {
        return index >= 0 && index < reasons.size() && CONDITION_FAILED.equals(reasons.get(index).code());
    }

    private Map<String, AttributeValue> stateKey(Long workOrderId) {
        return Map.of(
                ExecutionItemMapper.PK, AttributeValue.fromS(ExecutionItemMapper.partitionOf(workOrderId)),
                ExecutionItemMapper.SK, AttributeValue.fromS(ExecutionItemMapper.STATE_SK));
    }
}
