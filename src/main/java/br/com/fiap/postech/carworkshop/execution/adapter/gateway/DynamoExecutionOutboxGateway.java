package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionOutboxPort;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.PendingEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class DynamoExecutionOutboxGateway implements ExecutionOutboxPort {

    @Inject
    DynamoDbClient dynamo;

    @ConfigProperty(name = "execution.table-name")
    String tableName;

    @Override
    public List<PendingEvent> claimPending(int limit) {
        return dynamo.query(QueryRequest.builder()
                        .tableName(tableName)
                        .indexName(ExecutionItemMapper.OUTBOX_PENDING_INDEX)
                        .keyConditionExpression("#partition = :partition")
                        .expressionAttributeNames(Map.of("#partition", ExecutionItemMapper.GSI2PK))
                        .expressionAttributeValues(Map.of(":partition",
                                AttributeValue.fromS(ExecutionItemMapper.PENDING_PARTITION)))
                        .scanIndexForward(true)
                        .limit(limit)
                        .build())
                .items().stream()
                .map(ExecutionItemMapper::toPendingEvent)
                .toList();
    }

    // Tirar as chaves do indice e o que faz a mensagem sair da fila de pendencias; a condicao resolve a corrida
    // entre replicas, porque o DynamoDB nao tem SKIP LOCKED e as duas leem o mesmo lote.
    @Override
    public boolean markSent(Long workOrderId, UUID messageId, Instant sentAt) {
        try {
            dynamo.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                            ExecutionItemMapper.PK,
                            AttributeValue.fromS(ExecutionItemMapper.partitionOf(workOrderId)),
                            ExecutionItemMapper.SK,
                            AttributeValue.fromS(ExecutionItemMapper.outboxSortKey(messageId))))
                    .updateExpression("SET #sentAt = :sentAt REMOVE #pendingPartition, #pendingSort")
                    .conditionExpression("attribute_not_exists(#sentAt)")
                    .expressionAttributeNames(Map.of(
                            "#sentAt", ExecutionItemMapper.SENT_AT,
                            "#pendingPartition", ExecutionItemMapper.GSI2PK,
                            "#pendingSort", ExecutionItemMapper.GSI2SK))
                    .expressionAttributeValues(Map.of(":sentAt", AttributeValue.fromS(sentAt.toString())))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
