package br.com.fiap.postech.carworkshop.execution.domain.message;

public sealed interface EventPayload permits DiagnosisStartedPayload, DiagnosisCompletedPayload,
        RepairStartedPayload, RepairCompletedPayload, FailurePayload, EmptyPayload {
}
