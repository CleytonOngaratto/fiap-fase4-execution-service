package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedEvent;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.DiagnosisEventPublisherPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class SnsDiagnosisEventGateway implements DiagnosisEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(SnsDiagnosisEventGateway.class);

    @Inject
    SnsClient sns;

    @Inject
    SagaEnvelopeCodec codec;

    @ConfigProperty(name = "execution.events.topic-arn")
    String topicArn;

    @Override
    public void publish(DiagnosisStartedEvent event) {
        sns.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(codec.encode(event))
                .messageGroupId(String.valueOf(event.workOrderId()))
                .messageDeduplicationId(event.messageId().toString())
                .messageAttributes(attributesFor(event))
                .build());
        log.info("{} published for Work Order {}.", event.type(), event.workOrderId());
    }

    private Map<String, MessageAttributeValue> attributesFor(DiagnosisStartedEvent event) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put(SagaEnvelopeCodec.FIELD_TYPE, text(event.type()));
        attributes.put(SagaEnvelopeCodec.FIELD_VERSION, number(event.version()));
        attributes.put(SagaEnvelopeCodec.FIELD_SAGA_ID, text(event.sagaId().toString()));
        if (event.correlationId() != null && !event.correlationId().isBlank()) {
            attributes.put(SagaEnvelopeCodec.FIELD_CORRELATION_ID, text(event.correlationId()));
        }
        return attributes;
    }

    private static MessageAttributeValue text(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private static MessageAttributeValue number(int value) {
        return MessageAttributeValue.builder().dataType("Number").stringValue(String.valueOf(value)).build();
    }
}
