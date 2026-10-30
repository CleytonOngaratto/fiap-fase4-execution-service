package br.com.fiap.postech.carworkshop.execution.domain.message;

public record FailurePayload(String reason) implements EventPayload {
}
