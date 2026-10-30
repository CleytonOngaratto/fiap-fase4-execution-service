package br.com.fiap.postech.carworkshop.execution.domain.message;

import br.com.fiap.postech.carworkshop.execution.domain.entity.RepairItem;

import java.util.List;

public record StartRepairCommand(SagaEnvelope envelope, List<RepairItem> items) {
}
