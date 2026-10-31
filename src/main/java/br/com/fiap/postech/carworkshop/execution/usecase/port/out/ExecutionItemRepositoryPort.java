package br.com.fiap.postech.carworkshop.execution.usecase.port.out;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;

import java.util.List;
import java.util.Optional;

public interface ExecutionItemRepositoryPort {

    Optional<ExecutionItem> findByWorkOrderId(Long workOrderId);

    List<ExecutionItem> queueOf(String branchId, ExecutionStatus status, int limit);

    ExecutionWriteResult apply(ExecutionWrite write);
}
