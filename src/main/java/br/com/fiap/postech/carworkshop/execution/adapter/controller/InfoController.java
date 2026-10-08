package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import br.com.fiap.postech.carworkshop.execution.adapter.presenter.InfoResponse;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.ServiceInfoUseCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/info")
@Tag(name = "Info")
@Produces(MediaType.APPLICATION_JSON)
public class InfoController {

    @Inject
    ServiceInfoUseCase useCase;

    @GET
    @Operation(summary = "Service name and version")
    public InfoResponse info() {
        return InfoResponse.from(useCase.getInfo());
    }
}
