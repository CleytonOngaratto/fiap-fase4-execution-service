package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import br.com.fiap.postech.carworkshop.execution.adapter.dto.CompleteDiagnosisRequest;
import br.com.fiap.postech.carworkshop.execution.adapter.dto.FailureRequest;
import br.com.fiap.postech.carworkshop.execution.domain.exception.InvalidRequestException;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.MechanicUseCase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/work-orders")
@Tag(name = "Mechanic")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkOrderExecutionController {

    private static final String TRACE_HEADER = "X-Trace-Id";

    @Inject
    MechanicUseCase useCase;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/{id}/diagnosis/start")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Mechanic starts the diagnosis")
    public Response startDiagnosis(@PathParam("id") Long id,
                                   @HeaderParam(TRACE_HEADER) String correlationId) {
        useCase.startDiagnosis(id, mechanic(), correlationId);
        return Response.accepted().build();
    }

    @POST
    @Path("/{id}/diagnosis/complete")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Mechanic completes the diagnosis with a description and the catalog items")
    public Response completeDiagnosis(@PathParam("id") Long id, CompleteDiagnosisRequest request,
                                      @HeaderParam(TRACE_HEADER) String correlationId) {
        CompleteDiagnosisRequest body = required(request);
        useCase.completeDiagnosis(id, body.description(), body.toDomain(), mechanic(), correlationId);
        return Response.accepted().build();
    }

    @POST
    @Path("/{id}/diagnosis/fail")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Mechanic records that there is no viable repair")
    public Response failDiagnosis(@PathParam("id") Long id, FailureRequest request,
                                 @HeaderParam(TRACE_HEADER) String correlationId) {
        useCase.failDiagnosis(id, reasonOf(request), mechanic(), correlationId);
        return Response.accepted().build();
    }

    @POST
    @Path("/{id}/repair/start")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Mechanic starts the repair")
    public Response startRepair(@PathParam("id") Long id,
                                @HeaderParam(TRACE_HEADER) String correlationId) {
        useCase.startRepair(id, mechanic(), correlationId);
        return Response.accepted().build();
    }

    @POST
    @Path("/{id}/repair/complete")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Mechanic completes the repair")
    public Response completeRepair(@PathParam("id") Long id,
                                   @HeaderParam(TRACE_HEADER) String correlationId) {
        useCase.completeRepair(id, mechanic(), correlationId);
        return Response.accepted().build();
    }

    @POST
    @Path("/{id}/repair/fail")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Mechanic records that the repair failed")
    public Response failRepair(@PathParam("id") Long id, FailureRequest request,
                               @HeaderParam(TRACE_HEADER) String correlationId) {
        useCase.failRepair(id, reasonOf(request), mechanic(), correlationId);
        return Response.accepted().build();
    }

    private String mechanic() {
        return MechanicIdentity.of(jwt);
    }

    private static CompleteDiagnosisRequest required(CompleteDiagnosisRequest request) {
        if (request == null) {
            throw new InvalidRequestException("The diagnosis body is required.");
        }
        return request;
    }

    private static String reasonOf(FailureRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new InvalidRequestException("The reason is required.");
        }
        return request.reason();
    }
}
