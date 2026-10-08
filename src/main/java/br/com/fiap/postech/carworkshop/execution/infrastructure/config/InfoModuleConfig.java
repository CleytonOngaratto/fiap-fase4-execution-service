package br.com.fiap.postech.carworkshop.execution.infrastructure.config;

import br.com.fiap.postech.carworkshop.execution.usecase.interactor.ServiceInfoInteractor;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.ServiceInfoUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ServiceMetadataPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class InfoModuleConfig {

    @Produces
    @ApplicationScoped
    public ServiceInfoUseCase serviceInfoUseCase(ServiceMetadataPort metadata) {
        return new ServiceInfoInteractor(metadata);
    }
}
