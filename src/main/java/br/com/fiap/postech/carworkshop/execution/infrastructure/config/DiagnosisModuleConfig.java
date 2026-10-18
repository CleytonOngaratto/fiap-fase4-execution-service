package br.com.fiap.postech.carworkshop.execution.infrastructure.config;

import br.com.fiap.postech.carworkshop.execution.usecase.interactor.DiagnosisInteractor;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.DiagnosisUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.DiagnosisEventPublisherPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

@ApplicationScoped
public class DiagnosisModuleConfig {

    @Produces
    @ApplicationScoped
    public DiagnosisUseCase diagnosisUseCase(DiagnosisEventPublisherPort publisher) {
        return new DiagnosisInteractor(publisher, Clock.systemUTC());
    }
}
