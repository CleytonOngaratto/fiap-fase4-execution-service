package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import br.com.fiap.postech.carworkshop.execution.adapter.presenter.InfoResponse;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ServiceInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InfoControllerUnitTest {

    @Test
    void info_presentsTheUseCaseResult() {
        InfoController controller = new InfoController();
        controller.useCase = () -> new ServiceInfo("execution-service", "1.2.3");

        assertEquals(new InfoResponse("execution-service", "1.2.3"), controller.info());
    }
}
