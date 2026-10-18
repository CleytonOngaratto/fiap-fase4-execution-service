package br.com.fiap.postech.carworkshop.execution.domain.message;

public final class SagaMessageType {

    public static final String START_DIAGNOSIS = "StartDiagnosis";
    public static final String DIAGNOSIS_STARTED = "DiagnosisStarted";

    private SagaMessageType() {
    }
}
