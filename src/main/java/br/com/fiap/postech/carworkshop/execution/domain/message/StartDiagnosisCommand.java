package br.com.fiap.postech.carworkshop.execution.domain.message;

import java.util.UUID;

public record StartDiagnosisCommand(UUID messageId,
                                    Long workOrderId,
                                    UUID sagaId,
                                    String correlationId,
                                    String vehiclePlate) {
}
