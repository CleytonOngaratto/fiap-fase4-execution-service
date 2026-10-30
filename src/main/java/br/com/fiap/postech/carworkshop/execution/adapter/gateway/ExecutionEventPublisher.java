package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

public interface ExecutionEventPublisher {

    void publish(OutboundMessage message);
}
