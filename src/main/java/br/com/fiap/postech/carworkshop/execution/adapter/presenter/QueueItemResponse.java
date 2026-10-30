package br.com.fiap.postech.carworkshop.execution.adapter.presenter;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;

import java.time.Instant;

public record QueueItemResponse(Long workOrderId,
                                String branchId,
                                String status,
                                Instant enqueuedAt,
                                String vehiclePlate,
                                String complaint,
                                String mechanic,
                                String diagnosisDescription,
                                int itemCount) {

    public static QueueItemResponse from(ExecutionItem item) {
        return new QueueItemResponse(item.workOrderId(), item.branchId(), item.status().name(),
                item.enqueuedAt(), item.vehiclePlate(), item.complaint(), item.mechanic(),
                item.diagnosisDescription(),
                item.repairItems().isEmpty() ? item.diagnosedItems().size() : item.repairItems().size());
    }
}
