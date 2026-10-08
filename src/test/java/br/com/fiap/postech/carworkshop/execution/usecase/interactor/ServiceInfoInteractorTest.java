package br.com.fiap.postech.carworkshop.execution.usecase.interactor;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ServiceInfo;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ServiceMetadataPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceInfoInteractorTest {

    @Test
    void getInfo_buildsServiceInfoFromMetadata() {
        ServiceMetadataPort metadata = new ServiceMetadataPort() {
            @Override
            public String name() {
                return "execution-service";
            }

            @Override
            public String version() {
                return "1.2.3";
            }
        };

        ServiceInfo info = new ServiceInfoInteractor(metadata).getInfo();

        assertEquals(new ServiceInfo("execution-service", "1.2.3"), info);
    }
}
