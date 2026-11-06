package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterQueueGaugeTest {

    private static final String QUEUE_URL = "http://localhost:9324/000000000000/execution-commands.fifo";
    private static final String DLQ = "execution-commands-dlq.fifo";
    private static final String DLQ_URL = "http://localhost:9324/000000000000/" + DLQ;
    private static final String REDRIVE_POLICY =
            "{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:" + DLQ + "\",\"maxReceiveCount\":3}";

    private SqsClient sqs;
    private MeterRegistry registry;
    private DeadLetterQueueGauge gauge;

    @BeforeEach
    void setUp() {
        sqs = mock(SqsClient.class);
        registry = new SimpleMeterRegistry();
        gauge = new DeadLetterQueueGauge();
        gauge.sqs = sqs;
        gauge.registry = registry;
        gauge.objectMapper = new ObjectMapper();
        gauge.queueUrl = QUEUE_URL;
        when(sqs.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl(DLQ_URL).build());
    }

    @Test
    void reportsTheDepthOfTheQueueNamedByTheRedrivePolicy() {
        answer(QUEUE_URL, QueueAttributeName.REDRIVE_POLICY, REDRIVE_POLICY);
        answer(DLQ_URL, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "3");

        gauge.measure();

        assertEquals(3.0, depth());
        ArgumentCaptor<GetQueueUrlRequest> captor = ArgumentCaptor.forClass(GetQueueUrlRequest.class);
        verify(sqs).getQueueUrl(captor.capture());
        assertEquals(DLQ, captor.getValue().queueName());
    }

    @Test
    void findsTheDeadLetterQueueOnlyOnce() {
        answer(QUEUE_URL, QueueAttributeName.REDRIVE_POLICY, REDRIVE_POLICY);
        answer(DLQ_URL, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0");

        gauge.measure();
        gauge.measure();

        verify(sqs, times(1)).getQueueUrl(any(GetQueueUrlRequest.class));
        assertEquals(0.0, depth());
    }

    @Test
    void reportsUnknownWhenTheDepthCannotBeRead() {
        answer(QUEUE_URL, QueueAttributeName.REDRIVE_POLICY, REDRIVE_POLICY);
        answer(DLQ_URL, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "3");
        gauge.measure();
        when(sqs.getQueueAttributes(attributesOf(DLQ_URL))).thenThrow(new IllegalStateException("sqs down"));

        assertDoesNotThrow(() -> gauge.measure());

        assertTrue(Double.isNaN(depth()));
    }

    @Test
    void reportsNothingForAQueueWithoutARedrivePolicy() {
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class)))
                .thenReturn(GetQueueAttributesResponse.builder().build());

        assertDoesNotThrow(() -> gauge.measure());

        assertNull(registry.find(DeadLetterQueueGauge.NAME).gauge());
        verify(sqs, never()).getQueueUrl(any(GetQueueUrlRequest.class));
    }

    @Test
    void reportsNothingWhenTheRedrivePolicyIsNotJson() {
        answer(QUEUE_URL, QueueAttributeName.REDRIVE_POLICY, "not json");

        assertDoesNotThrow(() -> gauge.measure());

        assertNull(registry.find(DeadLetterQueueGauge.NAME).gauge());
        verify(sqs, never()).getQueueUrl(any(GetQueueUrlRequest.class));
    }

    private void answer(String url, QueueAttributeName name, String value) {
        when(sqs.getQueueAttributes(attributesOf(url)))
                .thenReturn(GetQueueAttributesResponse.builder().attributes(Map.of(name, value)).build());
    }

    private static GetQueueAttributesRequest attributesOf(String url) {
        return argThat((GetQueueAttributesRequest request) -> request != null && url.equals(request.queueUrl()));
    }

    private double depth() {
        return registry.get(DeadLetterQueueGauge.NAME).tag("queue", DLQ).gauge().value();
    }
}
