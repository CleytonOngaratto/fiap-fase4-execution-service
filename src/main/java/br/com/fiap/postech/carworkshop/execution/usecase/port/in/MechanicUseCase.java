package br.com.fiap.postech.carworkshop.execution.usecase.port.in;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;

import java.util.List;

public interface MechanicUseCase {

    List<ExecutionItem> queue(String branchId, ExecutionStatus status, int limit);

    void startDiagnosis(Long workOrderId, String mechanic, String correlationId);

    void completeDiagnosis(Long workOrderId, String description, List<DiagnosedItem> items, String mechanic,
                           String correlationId);

    void failDiagnosis(Long workOrderId, String reason, String mechanic, String correlationId);

    void startRepair(Long workOrderId, String mechanic, String correlationId);

    void completeRepair(Long workOrderId, String mechanic, String correlationId);

    void failRepair(Long workOrderId, String reason, String mechanic, String correlationId);
}
