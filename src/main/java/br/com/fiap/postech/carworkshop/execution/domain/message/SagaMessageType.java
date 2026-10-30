package br.com.fiap.postech.carworkshop.execution.domain.message;

public final class SagaMessageType {

    public static final String START_DIAGNOSIS = "StartDiagnosis";
    public static final String CANCEL_DIAGNOSIS = "CancelDiagnosis";
    public static final String START_REPAIR = "StartRepair";

    public static final String DIAGNOSIS_STARTED = "DiagnosisStarted";
    public static final String DIAGNOSIS_COMPLETED = "DiagnosisCompleted";
    public static final String DIAGNOSIS_FAILED = "DiagnosisFailed";
    public static final String DIAGNOSIS_CANCELED = "DiagnosisCanceled";
    public static final String REPAIR_STARTED = "RepairStarted";
    public static final String REPAIR_COMPLETED = "RepairCompleted";
    public static final String REPAIR_FAILED = "RepairFailed";

    private SagaMessageType() {
    }
}
