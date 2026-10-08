package br.com.fiap.postech.carworkshop.execution.adapter.presenter;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ServiceInfo;

public record InfoResponse(String name, String version) {

    public static InfoResponse from(ServiceInfo info) {
        return new InfoResponse(info.name(), info.version());
    }
}
