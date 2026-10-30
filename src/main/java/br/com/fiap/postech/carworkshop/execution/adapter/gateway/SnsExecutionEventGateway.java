package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.HashMap;
import java.util.Map;

// Classe comum, nao bean: com @ApplicationScoped ela seria um segundo bean de ExecutionEventPublisher e
// colidiria com o producer que escolhe entre SNS e log por perfil.
public class SnsExecutionEventGateway implements ExecutionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsExecutionEventGateway.class);

    private final SnsClient sns;
    private final String topicArn;

    public SnsExecutionEventGateway(SnsClient sns, String topicArn) {
        this.sns = sns;
        this.topicArn = topicArn;
    }

    @Override
    public void publish(OutboundMessage message) {
        sns.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(message.body())
                .messageGroupId(String.valueOf(message.workOrderId()))
                .messageDeduplicationId(message.messageId().toString())
                .messageAttributes(attributesFor(message))
                .build());
        log.info("{} published for Work Order {}.", message.type(), message.workOrderId());
    }

    private Map<String, MessageAttributeValue> attributesFor(OutboundMessage message) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put(SagaEnvelopeCodec.FIELD_TYPE, text(message.type()));
        attributes.put(SagaEnvelopeCodec.FIELD_VERSION, number(message.version()));
        attributes.put(SagaEnvelopeCodec.FIELD_SAGA_ID, text(message.sagaId().toString()));
        if (message.correlationId() != null && !message.correlationId().isBlank()) {
            attributes.put(SagaEnvelopeCodec.FIELD_CORRELATION_ID, text(message.correlationId()));
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
