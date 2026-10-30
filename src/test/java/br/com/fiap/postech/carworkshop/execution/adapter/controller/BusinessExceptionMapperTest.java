package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import br.com.fiap.postech.carworkshop.execution.adapter.presenter.ErrorResponse;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.exception.CatalogUnavailableException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.ExecutionConflictException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.ExecutionItemNotFoundException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.InvalidDiagnosisException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.InvalidRequestException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessExceptionMapperTest {

    @Test
    void mapsEachBusinessFailureToItsStatus() {
        assertEquals(404, BusinessExceptionMapper.statusOf(new ExecutionItemNotFoundException(42L)));
        assertEquals(409, BusinessExceptionMapper.statusOf(
                new ExecutionConflictException(ExecutionStatus.REPAIR_DONE)));
        assertEquals(503, BusinessExceptionMapper.statusOf(new CatalogUnavailableException("fora do ar")));
        assertEquals(400, BusinessExceptionMapper.statusOf(new InvalidDiagnosisException("item invalido")));
        assertEquals(400, BusinessExceptionMapper.statusOf(new InvalidRequestException("filial ausente")));
    }

    @Test
    void answersWithTheStatusAndTheMessage() {
        Response response = new BusinessExceptionMapper()
                .toResponse(new ExecutionConflictException(ExecutionStatus.CANCELED));

        assertEquals(409, response.getStatus());
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals(409, body.status());
        assertEquals("The execution item is CANCELED: this action is no longer possible.", body.error());
    }
}
