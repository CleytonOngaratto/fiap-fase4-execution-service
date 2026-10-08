package br.com.fiap.postech.carworkshop.execution.domain.entity;

import java.util.Objects;

public record ServiceInfo(String name, String version) {

    public ServiceInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
    }
}
