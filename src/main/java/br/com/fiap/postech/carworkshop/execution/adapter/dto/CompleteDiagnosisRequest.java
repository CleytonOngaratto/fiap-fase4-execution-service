package br.com.fiap.postech.carworkshop.execution.adapter.dto;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;

import java.util.List;

public record CompleteDiagnosisRequest(String description, List<DiagnosedItemRequest> items) {

    public List<DiagnosedItem> toDomain() {
        return items == null ? List.of() : items.stream().map(DiagnosedItemRequest::toDomain).toList();
    }
}
