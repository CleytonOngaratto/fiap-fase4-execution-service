package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import java.net.URI;
import java.util.Map;

public class ElasticMqResource implements QuarkusTestResourceLifecycleManager {

    public static final String COMMANDS_QUEUE = "execution-commands.fifo";

    private static final DockerImageName IMAGE =
            DockerImageName.parse("softwaremill/elasticmq-native:1.7.1");
    private static final int SQS_PORT = 9324;

    private GenericContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new GenericContainer<>(IMAGE)
                .withExposedPorts(SQS_PORT)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("elasticmq.conf"), "/opt/elasticmq.conf")
                .waitingFor(Wait.forListeningPort());
        container.start();

        String endpoint = "http://" + container.getHost() + ":" + container.getMappedPort(SQS_PORT);
        return Map.of(
                "quarkus.sqs.endpoint-override", endpoint,
                "execution.commands.queue-url", queueUrl(endpoint, COMMANDS_QUEUE));
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    // A URL vem do proprio ElasticMQ em vez de montada a mao: o formato dela nao e contrato publico.
    private static String queueUrl(String endpoint, String queueName) {
        try (SqsClient sqs = client(endpoint)) {
            return sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
        }
    }

    static SqsClient client(String endpoint) {
        return SqsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("elasticmq", "elasticmq")))
                .build();
    }
}
