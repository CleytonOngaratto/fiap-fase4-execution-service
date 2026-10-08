package br.com.fiap.postech.carworkshop.execution.adapter.presenter;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ServiceInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InfoResponseTest {

    @Test
    void from_copiesNameAndVersion() {
        InfoResponse response = InfoResponse.from(new ServiceInfo("execution-service", "1.2.3"));

        assertEquals(new InfoResponse("execution-service", "1.2.3"), response);
    }
}
