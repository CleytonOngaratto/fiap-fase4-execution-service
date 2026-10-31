package br.com.fiap.postech.carworkshop.execution.domain.entity;

public enum ExecutionStatus {
    DIAGNOSIS_QUEUED(true),
    DIAGNOSIS_IN_PROGRESS(true),
    DIAGNOSIS_DONE(true),
    DIAGNOSIS_FAILED(false),
    REPAIR_QUEUED(true),
    REPAIR_IN_PROGRESS(true),
    REPAIR_DONE(false),
    REPAIR_FAILED(false),
    CANCELED(false);

    private final boolean cancelable;

    ExecutionStatus(boolean cancelable) {
        this.cancelable = cancelable;
    }

    public boolean isCancelable() {
        return cancelable;
    }
}
