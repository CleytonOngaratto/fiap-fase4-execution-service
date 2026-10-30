package br.com.fiap.postech.carworkshop.execution.domain.entity;

import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionItemTest {

    private static final Instant LATER = ExecutionFixtures.NOW.plusSeconds(600);

    @Test
    void entersTheQueueOnTheDiagnosisCommand() {
        ExecutionItem item = ExecutionFixtures.queued();

        assertEquals(ExecutionStatus.DIAGNOSIS_QUEUED, item.status());
        assertEquals(ExecutionFixtures.NOW, item.enqueuedAt());
        assertEquals(ExecutionFixtures.PLATE, item.vehiclePlate());
        assertEquals(ExecutionFixtures.COMPLAINT, item.complaint());
        assertEquals(1L, item.version());
        assertNull(item.mechanic());
    }

    @Test
    void walksTheWholeHappyPath() {
        ExecutionItem inProgress = ExecutionFixtures.queued()
                .startDiagnosis(ExecutionFixtures.MECHANIC, LATER).orElseThrow();
        assertEquals(ExecutionStatus.DIAGNOSIS_IN_PROGRESS, inProgress.status());
        assertEquals(ExecutionFixtures.MECHANIC, inProgress.mechanic());
        assertEquals(2L, inProgress.version());

        ExecutionItem done = inProgress.completeDiagnosis("Amortecedor gasto",
                ExecutionFixtures.diagnosedItems(), LATER).orElseThrow();
        assertEquals(ExecutionStatus.DIAGNOSIS_DONE, done.status());
        assertEquals(ExecutionFixtures.diagnosedItems(), done.diagnosedItems());

        ExecutionItem repairQueued = done.queueForRepair(ExecutionFixtures.repairItems(), LATER).orElseThrow();
        assertEquals(ExecutionStatus.REPAIR_QUEUED, repairQueued.status());
        assertEquals(ExecutionFixtures.repairItems(), repairQueued.repairItems());
        assertNull(repairQueued.mechanic());

        ExecutionItem repairing = repairQueued.startRepair("mecanico.luis", LATER).orElseThrow();
        assertEquals(ExecutionStatus.REPAIR_IN_PROGRESS, repairing.status());
        assertEquals("mecanico.luis", repairing.mechanic());

        ExecutionItem finished = repairing.completeRepair(LATER).orElseThrow();
        assertEquals(ExecutionStatus.REPAIR_DONE, finished.status());
        assertEquals(6L, finished.version());
    }

    // O reparo entra na fila com enqueuedAt novo: a fila de reparo e outra particao do indice e tem a propria
    // ordem de entrada, senao um reparo herdaria a antiguidade do diagnostico.
    @Test
    void theRepairQueueRestartsTheWaitingClock() {
        ExecutionItem done = diagnosisDone();

        ExecutionItem queued = done.queueForRepair(ExecutionFixtures.repairItems(), LATER).orElseThrow();

        assertEquals(LATER, queued.enqueuedAt());
        assertEquals(ExecutionFixtures.NOW, done.enqueuedAt());
    }

    @Test
    void recordsTheReasonWhenTheDiagnosisOrTheRepairFails() {
        ExecutionItem failedDiagnosis = ExecutionFixtures.queued()
                .startDiagnosis(ExecutionFixtures.MECHANIC, LATER).orElseThrow()
                .failDiagnosis("sem reparo viavel", LATER).orElseThrow();
        assertEquals(ExecutionStatus.DIAGNOSIS_FAILED, failedDiagnosis.status());
        assertEquals("sem reparo viavel", failedDiagnosis.statusReason());

        ExecutionItem failedRepair = diagnosisDone()
                .queueForRepair(ExecutionFixtures.repairItems(), LATER).orElseThrow()
                .startRepair(ExecutionFixtures.MECHANIC, LATER).orElseThrow()
                .failRepair("parafuso quebrou", LATER).orElseThrow();
        assertEquals(ExecutionStatus.REPAIR_FAILED, failedRepair.status());
        assertEquals("parafuso quebrou", failedRepair.statusReason());
    }

    @Test
    void refusesATransitionOutOfOrder() {
        assertTrue(ExecutionFixtures.queued().completeDiagnosis("x", List.of(), LATER).isEmpty());
        assertTrue(ExecutionFixtures.queued().failDiagnosis("x", LATER).isEmpty());
        assertTrue(ExecutionFixtures.queued().queueForRepair(List.of(), LATER).isEmpty());
        assertTrue(ExecutionFixtures.queued().startRepair("x", LATER).isEmpty());
        assertTrue(ExecutionFixtures.queued().completeRepair(LATER).isEmpty());
        assertTrue(ExecutionFixtures.queued().failRepair("x", LATER).isEmpty());
        assertTrue(diagnosisDone().startDiagnosis("x", LATER).isEmpty());
    }

    @Test
    void cancelsFromEveryStateThatStillHasSomethingToUndo() {
        assertEquals(ExecutionStatus.CANCELED, cancel(ExecutionFixtures.queued()).status());
        assertEquals(ExecutionStatus.CANCELED, cancel(diagnosisInProgress()).status());
        assertEquals(ExecutionStatus.CANCELED, cancel(diagnosisDone()).status());
        assertEquals(ExecutionStatus.CANCELED, cancel(repairQueued()).status());
        assertEquals(ExecutionStatus.CANCELED, cancel(repairInProgress()).status());
        assertEquals("CANCELED_BY_STAFF", cancel(ExecutionFixtures.queued()).statusReason());
    }

    // Estado que ja terminou nao vira CANCELED: perderia o registro de por que terminou. Quem responde o evento
    // de confirmacao e o caso de uso, nao a entidade.
    @Test
    void doesNotCancelAStateThatAlreadyEnded() {
        assertTrue(repairDone().cancel("CANCELED_BY_STAFF", LATER).isEmpty());
        assertTrue(diagnosisFailed().cancel("CANCELED_BY_STAFF", LATER).isEmpty());
        assertTrue(repairFailed().cancel("CANCELED_BY_STAFF", LATER).isEmpty());
        assertTrue(cancel(ExecutionFixtures.queued()).cancel("CANCELED_BY_STAFF", LATER).isEmpty());
    }

    @Test
    void keepsTheItemCollectionsImmutableAndNeverNull() {
        ExecutionItem item = new ExecutionItem(1L, "SP-01", ExecutionFixtures.SAGA_ID,
                ExecutionStatus.DIAGNOSIS_QUEUED, null, ExecutionFixtures.NOW, "AAA0A00", "ruido", null, null,
                null, null, 1L, ExecutionFixtures.NOW);

        assertTrue(item.diagnosedItems().isEmpty());
        assertTrue(item.repairItems().isEmpty());
    }

    private static ExecutionItem cancel(ExecutionItem item) {
        return item.cancel("CANCELED_BY_STAFF", LATER).orElseThrow();
    }

    private static ExecutionItem diagnosisInProgress() {
        return ExecutionFixtures.queued().startDiagnosis(ExecutionFixtures.MECHANIC, LATER).orElseThrow();
    }

    private static ExecutionItem diagnosisDone() {
        return diagnosisInProgress()
                .completeDiagnosis("Amortecedor gasto", ExecutionFixtures.diagnosedItems(), LATER)
                .orElseThrow();
    }

    private static ExecutionItem diagnosisFailed() {
        return diagnosisInProgress().failDiagnosis("sem reparo viavel", LATER).orElseThrow();
    }

    private static ExecutionItem repairQueued() {
        return diagnosisDone().queueForRepair(ExecutionFixtures.repairItems(), LATER).orElseThrow();
    }

    private static ExecutionItem repairInProgress() {
        return repairQueued().startRepair(ExecutionFixtures.MECHANIC, LATER).orElseThrow();
    }

    private static ExecutionItem repairDone() {
        return repairInProgress().completeRepair(LATER).orElseThrow();
    }

    private static ExecutionItem repairFailed() {
        return repairInProgress().failRepair("parafuso quebrou", LATER).orElseThrow();
    }
}
