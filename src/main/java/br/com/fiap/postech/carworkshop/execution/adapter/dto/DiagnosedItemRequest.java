package br.com.fiap.postech.carworkshop.execution.adapter.dto;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;

public record DiagnosedItemRequest(String type, Long catalogId, Integer quantity) {

    public DiagnosedItem toDomain() {
        return new DiagnosedItem(type, catalogId, quantity == null ? 0 : quantity);
    }
}
