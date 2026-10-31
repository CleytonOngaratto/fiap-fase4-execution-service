package br.com.fiap.postech.carworkshop.execution.domain.message;

public record StartDiagnosisCommand(SagaEnvelope envelope, String vehiclePlate, String complaint) {
}
