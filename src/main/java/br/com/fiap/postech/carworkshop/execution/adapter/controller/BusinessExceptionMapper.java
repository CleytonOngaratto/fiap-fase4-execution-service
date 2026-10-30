package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import br.com.fiap.postech.carworkshop.execution.adapter.presenter.ErrorResponse;
import br.com.fiap.postech.carworkshop.execution.domain.exception.BusinessException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.CatalogUnavailableException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.ExecutionConflictException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.ExecutionItemNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Override
    public Response toResponse(BusinessException exception) {
        int status = statusOf(exception);
        return Response.status(status).entity(new ErrorResponse(status, exception.getMessage())).build();
    }

    static int statusOf(BusinessException exception) {
        if (exception instanceof ExecutionItemNotFoundException) {
            return Response.Status.NOT_FOUND.getStatusCode();
        }
        if (exception instanceof ExecutionConflictException) {
            return Response.Status.CONFLICT.getStatusCode();
        }
        if (exception instanceof CatalogUnavailableException) {
            return Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
        }
        return Response.Status.BAD_REQUEST.getStatusCode();
    }
}
