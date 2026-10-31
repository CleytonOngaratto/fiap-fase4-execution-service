package br.com.fiap.postech.carworkshop.execution.domain.exception;

public class ExecutionItemNotFoundException extends BusinessException {

    public ExecutionItemNotFoundException(Long workOrderId) {
        super("Work Order " + workOrderId + " is not in the execution queue.");
    }
}
