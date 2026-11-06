package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DeadLetterQueueGauge {

    static final String NAME = "saga.dlq.messages";

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueGauge.class);

    @Inject
    SqsClient sqs;

    @Inject
    MeterRegistry registry;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "execution.commands.queue-url")
    String queueUrl;

    private final Map<String, Double> depths = new ConcurrentHashMap<>();
    private volatile DeadLetterQueue deadLetterQueue;

    // Falha de leitura vira NaN, nunca 0: zero falso pareceria uma fila saudavel.
    @Scheduled(every = "30s", identity = "execution-dlq-gauge",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void measure() {
        try {
            DeadLetterQueue queue = deadLetterQueue();
            report(queue.name(), Double.parseDouble(attribute(queue.url(),
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)));
        } catch (RuntimeException e) {
            depths.replaceAll((name, depth) -> Double.NaN);
            log.warn("Dead letter queue of {} could not be read; reporting it as unknown: {}", queueUrl,
                    e.toString());
        }
    }

    private DeadLetterQueue deadLetterQueue() {
        if (deadLetterQueue == null) {
            String name = nameOf(attribute(queueUrl, QueueAttributeName.REDRIVE_POLICY));
            String url = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
            deadLetterQueue = new DeadLetterQueue(name, url);
        }
        return deadLetterQueue;
    }

    private String nameOf(String redrivePolicy) {
        if (redrivePolicy == null) {
            throw new IllegalStateException("the queue has no redrive policy");
        }
        try {
            String arn = objectMapper.readTree(redrivePolicy).path("deadLetterTargetArn").asText("");
            return arn.substring(arn.lastIndexOf(':') + 1);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("the redrive policy is not JSON", e);
        }
    }

    private String attribute(String url, QueueAttributeName name) {
        return sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(url).attributeNames(name).build())
                .attributes().get(name);
    }

    private void report(String queue, double depth) {
        if (depths.put(queue, depth) == null) {
            Gauge.builder(NAME, depths, all -> all.getOrDefault(queue, Double.NaN))
                    .tag("queue", queue)
                    .register(registry);
        }
    }

    private record DeadLetterQueue(String name, String url) {
    }
}
