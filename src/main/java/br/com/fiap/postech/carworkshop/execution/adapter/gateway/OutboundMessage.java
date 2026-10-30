package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import java.util.UUID;

public record OutboundMessage(UUID messageId,
                              String type,
                              int version,
                              Long workOrderId,
                              UUID sagaId,
                              String correlationId,
                              String body) {
}
