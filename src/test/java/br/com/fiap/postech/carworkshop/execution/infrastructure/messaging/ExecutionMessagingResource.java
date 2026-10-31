package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutionMessagingResource implements QuarkusTestResourceLifecycleManager {

    public static final String COMMANDS_QUEUE = "execution-commands.fifo";
    public static final String TABLE = "execution";

    private static final DockerImageName QUEUE_IMAGE =
            DockerImageName.parse("softwaremill/elasticmq-native:1.7.1");
    private static final DockerImageName TABLE_IMAGE =
            DockerImageName.parse("amazon/dynamodb-local:2.5.2");
    private static final int SQS_PORT = 9324;
    private static final int DYNAMODB_PORT = 8000;

    private GenericContainer<?> queue;
    private GenericContainer<?> table;

    @Override
    public Map<String, String> start() {
        queue = new GenericContainer<>(QUEUE_IMAGE)
                .withExposedPorts(SQS_PORT)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("elasticmq.conf"), "/opt/elasticmq.conf")
                .waitingFor(Wait.forListeningPort());
        queue.start();

        table = new GenericContainer<>(TABLE_IMAGE)
                .withExposedPorts(DYNAMODB_PORT)
                .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
                .waitingFor(Wait.forListeningPort());
        table.start();

        String queueEndpoint = "http://" + queue.getHost() + ":" + queue.getMappedPort(SQS_PORT);
        String tableEndpoint = "http://" + table.getHost() + ":" + table.getMappedPort(DYNAMODB_PORT);
        createTable(tableEndpoint);

        Map<String, String> overrides = new HashMap<>();
        overrides.put("quarkus.sqs.endpoint-override", queueEndpoint);
        overrides.put("execution.commands.queue-url", queueUrl(queueEndpoint));
        overrides.put("quarkus.dynamodb.endpoint-override", tableEndpoint);
        overrides.put("execution.table-name", TABLE);
        return overrides;
    }

    @Override
    public void stop() {
        if (table != null) {
            table.stop();
        }
        if (queue != null) {
            queue.stop();
        }
    }

    // Mesmas chaves e indices do terraform/dynamodb.tf: se os dois divergirem, o teste passa contra um esquema
    // que a nuvem nao tem.
    private static void createTable(String endpoint) {
        try (DynamoDbClient dynamo = dynamoClient(endpoint)) {
            dynamo.createTable(CreateTableRequest.builder()
                    .tableName(TABLE)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            attribute("pk"), attribute("sk"),
                            attribute("gsi1pk"), attribute("gsi1sk"),
                            attribute("gsi2pk"), attribute("gsi2sk"))
                    .keySchema(key("pk", KeyType.HASH), key("sk", KeyType.RANGE))
                    .globalSecondaryIndexes(
                            index("gsi-branch-status", "gsi1pk", "gsi1sk"),
                            index("gsi-outbox-pending", "gsi2pk", "gsi2sk"))
                    .build());
        }
    }

    private static AttributeDefinition attribute(String name) {
        return AttributeDefinition.builder().attributeName(name)
                .attributeType(ScalarAttributeType.S).build();
    }

    private static KeySchemaElement key(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }

    private static GlobalSecondaryIndex index(String name, String hash, String range) {
        return GlobalSecondaryIndex.builder()
                .indexName(name)
                .keySchema(key(hash, KeyType.HASH), key(range, KeyType.RANGE))
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }

    // A URL vem do proprio ElasticMQ em vez de montada a mao: o formato dela nao e contrato publico.
    private static String queueUrl(String endpoint) {
        try (SqsClient sqs = sqsClient(endpoint)) {
            return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(COMMANDS_QUEUE).build()).queueUrl();
        }
    }

    static SqsClient sqsClient(String endpoint) {
        return SqsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("elasticmq", "elasticmq")))
                .build();
    }

    // Credencial alfanumerica: com hifen ou sublinhado o DynamoDB Local responde "The Access Key ID or
    // security token is invalid", que parece credencial errada e e so o alfabeto que ele aceita.
    static DynamoDbClient dynamoClient(String endpoint) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dynamodblocal", "dynamodblocal")))
                .build();
    }
}
