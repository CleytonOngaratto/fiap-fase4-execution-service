package br.com.fiap.postech.carworkshop.execution.usecase.interactor;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ServiceInfo;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.ServiceInfoUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ServiceMetadataPort;

/**
 * Pure Java use case: no {@code jakarta.*}, dependencies by constructor. Wired by
 * {@code InfoModuleConfig}, as in the OS Service modules.
 */
public class ServiceInfoInteractor implements ServiceInfoUseCase {

    private final ServiceMetadataPort metadata;

    public ServiceInfoInteractor(ServiceMetadataPort metadata) {
        this.metadata = metadata;
    }

    @Override
    public ServiceInfo getInfo() {
        return new ServiceInfo(metadata.name(), metadata.version());
    }
}
