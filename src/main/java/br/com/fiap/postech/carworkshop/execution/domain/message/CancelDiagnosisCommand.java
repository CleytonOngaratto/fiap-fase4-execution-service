package br.com.fiap.postech.carworkshop.execution.domain.message;

public record CancelDiagnosisCommand(SagaEnvelope envelope, String reason) {
}
