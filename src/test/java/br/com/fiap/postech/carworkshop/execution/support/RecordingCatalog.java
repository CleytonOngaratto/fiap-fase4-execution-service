package br.com.fiap.postech.carworkshop.execution.support;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.CatalogValidationPort;

import java.util.ArrayList;
import java.util.List;

public final class RecordingCatalog implements CatalogValidationPort {

    public final List<DiagnosedItem> validated = new ArrayList<>();
    public RuntimeException refusal;

    @Override
    public void assertKnown(List<DiagnosedItem> items) {
        validated.addAll(items);
        if (refusal != null) {
            throw refusal;
        }
    }
}
