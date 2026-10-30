package br.com.fiap.postech.carworkshop.execution.domain.exception;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;

public class ExecutionConflictException extends BusinessException {

    public ExecutionConflictException(ExecutionStatus current) {
        super("The execution item is " + current + ": this action is no longer possible.");
    }
}
