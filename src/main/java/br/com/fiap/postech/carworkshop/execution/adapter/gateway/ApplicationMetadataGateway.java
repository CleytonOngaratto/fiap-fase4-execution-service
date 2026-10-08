package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ServiceMetadataPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Reads the name and version Quarkus exposes as configuration ({@code quarkus.application.*}; the version
 * comes from the pom at build time).
 */
@ApplicationScoped
public class ApplicationMetadataGateway implements ServiceMetadataPort {

    @ConfigProperty(name = "quarkus.application.name")
    String name;

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @Override
    public String name() {
        return name;
    }

    @Override
    public String version() {
        return version;
    }
}
