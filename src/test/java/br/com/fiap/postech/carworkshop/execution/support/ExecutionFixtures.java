package br.com.fiap.postech.carworkshop.execution.support;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.RepairItem;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaEnvelope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExecutionFixtures {

    public static final Long WORK_ORDER_ID = 42L;
    public static final String BRANCH = "SP-01";
    public static final String PLATE = "ABC1D23";
    public static final String COMPLAINT = "Barulho na suspensao";
    public static final String CORRELATION_ID = "EVIDENCIA-B6-001";
    public static final String MECHANIC = "mecanico.joana";
    public static final UUID SAGA_ID = UUID.fromString("6f1d0f3a-0000-4000-8000-000000000001");
    public static final UUID MESSAGE_ID = UUID.fromString("0b8f1c2e-0000-4000-8000-000000000002");
    public static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private ExecutionFixtures() {
    }

    public static SagaEnvelope command(String type) {
        return new SagaEnvelope(MESSAGE_ID, type, SagaEnvelope.CURRENT_VERSION, NOW, SAGA_ID, WORK_ORDER_ID,
                BRANCH, CORRELATION_ID, null);
    }

    public static ExecutionItem queued() {
        return ExecutionItem.queuedForDiagnosis(WORK_ORDER_ID, BRANCH, SAGA_ID, PLATE, COMPLAINT, NOW);
    }

    public static List<DiagnosedItem> diagnosedItems() {
        return List.of(new DiagnosedItem(DiagnosedItem.SERVICE, 7L, 1),
                new DiagnosedItem(DiagnosedItem.PART, 12L, 3));
    }

    public static List<RepairItem> repairItems() {
        return List.of(new RepairItem(DiagnosedItem.SERVICE, 7L, "Troca de oleo", 1),
                new RepairItem(DiagnosedItem.PART, 12L, "Filtro de oleo", 3));
    }
}
