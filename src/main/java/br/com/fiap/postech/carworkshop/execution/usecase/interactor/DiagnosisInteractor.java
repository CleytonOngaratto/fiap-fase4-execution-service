package br.com.fiap.postech.carworkshop.execution.usecase.interactor;

import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedEvent;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.DiagnosisUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.DiagnosisEventPublisherPort;

import java.time.Clock;
import java.time.Instant;

public class DiagnosisInteractor implements DiagnosisUseCase {

    private final DiagnosisEventPublisherPort publisher;
    private final Clock clock;

    public DiagnosisInteractor(DiagnosisEventPublisherPort publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public void start(StartDiagnosisCommand command) {
        publisher.publish(DiagnosisStartedEvent.from(command, Instant.now(clock)));
    }
}
