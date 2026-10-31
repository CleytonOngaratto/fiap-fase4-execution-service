package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import br.com.fiap.postech.carworkshop.execution.adapter.presenter.QueueItemResponse;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.exception.InvalidRequestException;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.MechanicUseCase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/queue")
@Tag(name = "Execution queue")
@Produces(MediaType.APPLICATION_JSON)
public class ExecutionQueueController {

    private static final int MAX_LIMIT = 100;

    @Inject
    MechanicUseCase useCase;

    @GET
    @RolesAllowed("ADMIN")
    @Operation(summary = "Execution queue of a branch, oldest first")
    public List<QueueItemResponse> queue(@QueryParam("branchId") String branchId,
                                         @QueryParam("status") @DefaultValue("DIAGNOSIS_QUEUED") String status,
                                         @QueryParam("limit") @DefaultValue("25") int limit) {
        return useCase.queue(required(branchId), statusOf(status), boundedLimit(limit)).stream()
                .map(QueueItemResponse::from)
                .toList();
    }

    private static String required(String branchId) {
        if (branchId == null || branchId.isBlank()) {
            throw new InvalidRequestException("The branch is required to read the queue.");
        }
        return branchId;
    }

    private static ExecutionStatus statusOf(String status) {
        try {
            return ExecutionStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown execution status " + status + ".");
        }
    }

    private static int boundedLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("The limit has to be between 1 and " + MAX_LIMIT + ".");
        }
        return limit;
    }
}
