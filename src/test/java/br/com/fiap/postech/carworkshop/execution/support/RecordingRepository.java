package br.com.fiap.postech.carworkshop.execution.support;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionItemRepositoryPort;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWrite;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RecordingRepository implements ExecutionItemRepositoryPort {

    public final List<ExecutionWrite> writes = new ArrayList<>();
    public ExecutionItem stored;
    public List<ExecutionItem> queue = List.of();
    public ExecutionWriteResult result = ExecutionWriteResult.APPLIED;

    @Override
    public Optional<ExecutionItem> findByWorkOrderId(Long workOrderId) {
        return Optional.ofNullable(stored);
    }

    @Override
    public List<ExecutionItem> queueOf(String branchId, ExecutionStatus status, int limit) {
        return queue;
    }

    @Override
    public ExecutionWriteResult apply(ExecutionWrite write) {
        writes.add(write);
        return result;
    }

    public ExecutionWrite onlyWrite() {
        if (writes.size() != 1) {
            throw new IllegalStateException("Expected exactly one write, got " + writes.size() + ".");
        }
        return writes.get(0);
    }
}
