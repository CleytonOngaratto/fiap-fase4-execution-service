package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Usado so nos perfis sem AWS: o ElasticMQ nao emula SNS, e apontar o relay para a fila de respostas do OS
// poria a fila de outro servico na configuracao deste, contra a regra de que o topico e o contrato.
public class LoggingExecutionEventGateway implements ExecutionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingExecutionEventGateway.class);

    @Override
    public void publish(OutboundMessage message) {
        log.info("{} for Work Order {} (saga {}): {}", message.type(), message.workOrderId(),
                message.sagaId(), message.body());
    }
}
