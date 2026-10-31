package br.com.fiap.postech.carworkshop.execution.usecase.port.out;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;

import java.util.List;

public interface CatalogValidationPort {

    void assertKnown(List<DiagnosedItem> items);
}
