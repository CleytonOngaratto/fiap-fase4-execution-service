package br.com.fiap.postech.carworkshop.execution.domain.entity;

import java.util.Objects;

/**
 * Identity of the running service (name and version). Plain Java: the domain knows no framework.
 */
public record ServiceInfo(String name, String version) {

    public ServiceInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
    }
}
