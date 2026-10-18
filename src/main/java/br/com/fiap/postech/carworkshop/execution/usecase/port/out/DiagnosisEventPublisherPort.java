package br.com.fiap.postech.carworkshop.execution.usecase.port.out;

import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedEvent;

public interface DiagnosisEventPublisherPort {

    void publish(DiagnosisStartedEvent event);
}
