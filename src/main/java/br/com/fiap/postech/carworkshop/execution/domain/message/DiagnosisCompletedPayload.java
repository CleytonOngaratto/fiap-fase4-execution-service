package br.com.fiap.postech.carworkshop.execution.domain.message;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;

import java.util.List;

public record DiagnosisCompletedPayload(String description, List<DiagnosedItem> items) implements EventPayload {
}
