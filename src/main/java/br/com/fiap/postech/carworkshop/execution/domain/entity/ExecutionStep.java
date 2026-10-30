package br.com.fiap.postech.carworkshop.execution.domain.entity;

import java.time.Instant;

public record ExecutionStep(Instant occurredAt,
                            String action,
                            ExecutionStatus status,
                            String detail,
                            String actor) {

    public static final String SAGA = "saga";
}
