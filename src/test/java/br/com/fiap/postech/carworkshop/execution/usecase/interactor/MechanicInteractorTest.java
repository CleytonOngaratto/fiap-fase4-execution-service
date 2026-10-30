package br.com.fiap.postech.carworkshop.execution.usecase.interactor;

import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.exception.CatalogUnavailableException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.ExecutionConflictException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.ExecutionItemNotFoundException;
import br.com.fiap.postech.carworkshop.execution.domain.exception.InvalidDiagnosisException;
import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisCompletedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.DiagnosisStartedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.FailurePayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.RepairCompletedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.RepairStartedPayload;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.support.RecordingCatalog;
import br.com.fiap.postech.carworkshop.execution.support.RecordingRepository;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWrite;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicInteractorTest {

    private RecordingRepository repository;
    private RecordingCatalog catalog;
    private MechanicInteractor interactor;

    @BeforeEach
    void setUp() {
        repository = new RecordingRepository();
        catalog = new RecordingCatalog();
        interactor = new MechanicInteractor(repository, catalog,
                Clock.fixed(ExecutionFixtures.NOW, ZoneOffset.UTC));
    }

    @Test
    void readsTheQueueOfABranch() {
        repository.queue = List.of(ExecutionFixtures.queued());

        List<ExecutionItem> queue = interactor.queue(ExecutionFixtures.BRANCH,
                ExecutionStatus.DIAGNOSIS_QUEUED, 25);

        assertEquals(1, queue.size());
    }

    @Test
    void startsTheDiagnosisAndPublishesTheEventWithTheMechanic() {
        repository.stored = ExecutionFixtures.queued();

        interactor.startDiagnosis(ExecutionFixtures.WORK_ORDER_ID, ExecutionFixtures.MECHANIC,
                ExecutionFixtures.CORRELATION_ID);

        ExecutionWrite write = repository.onlyWrite();
        assertEquals(ExecutionStatus.DIAGNOSIS_IN_PROGRESS, write.next().status());
        assertEquals(SagaMessageType.DIAGNOSIS_STARTED, write.event().envelope().type());
        assertEquals(ExecutionFixtures.CORRELATION_ID, write.event().envelope().correlationId());
        assertEquals(ExecutionFixtures.SAGA_ID, write.event().envelope().sagaId());
        assertNull(write.event().envelope().causationId());
        DiagnosisStartedPayload payload =
                assertInstanceOf(DiagnosisStartedPayload.class, write.event().payload());
        assertEquals(ExecutionFixtures.MECHANIC, payload.mechanic());
        assertEquals(ExecutionFixtures.MECHANIC, write.step().actor());
        assertNull(write.inboxMessageId());
    }

    @Test
    void completesTheDiagnosisWithTheDescriptionAndTheItems() {
        repository.stored = diagnosisInProgress();

        interactor.completeDiagnosis(ExecutionFixtures.WORK_ORDER_ID, "Amortecedor gasto",
                ExecutionFixtures.diagnosedItems(), ExecutionFixtures.MECHANIC,
                ExecutionFixtures.CORRELATION_ID);

        ExecutionWrite write = repository.onlyWrite();
        assertEquals(ExecutionStatus.DIAGNOSIS_DONE, write.next().status());
        assertEquals(SagaMessageType.DIAGNOSIS_COMPLETED, write.event().envelope().type());
        DiagnosisCompletedPayload payload =
                assertInstanceOf(DiagnosisCompletedPayload.class, write.event().payload());
        assertEquals("Amortecedor gasto", payload.description());
        assertEquals(ExecutionFixtures.diagnosedItems(), payload.items());
        assertEquals(ExecutionFixtures.diagnosedItems(), catalog.validated);
    }

    @Test
    void walksTheRepairToThePivot() {
        repository.stored = repairQueued();
        interactor.startRepair(ExecutionFixtures.WORK_ORDER_ID, ExecutionFixtures.MECHANIC, null);
        assertEquals(SagaMessageType.REPAIR_STARTED, repository.onlyWrite().event().envelope().type());
        assertInstanceOf(RepairStartedPayload.class, repository.onlyWrite().event().payload());

        repository.writes.clear();
        repository.stored = repairInProgress();
        interactor.completeRepair(ExecutionFixtures.WORK_ORDER_ID, ExecutionFixtures.MECHANIC, null);
        assertEquals(ExecutionStatus.REPAIR_DONE, repository.onlyWrite().next().status());
        assertEquals(SagaMessageType.REPAIR_COMPLETED, repository.onlyWrite().event().envelope().type());
        assertInstanceOf(RepairCompletedPayload.class, repository.onlyWrite().event().payload());
    }

    @Test
    void publishesTheFailureEventsWithTheReason() {
        repository.stored = diagnosisInProgress();
        interactor.failDiagnosis(ExecutionFixtures.WORK_ORDER_ID, "sem reparo viavel",
                ExecutionFixtures.MECHANIC, null);
        assertEquals(SagaMessageType.DIAGNOSIS_FAILED, repository.onlyWrite().event().envelope().type());
        assertEquals("sem reparo viavel",
                assertInstanceOf(FailurePayload.class, repository.onlyWrite().event().payload()).reason());

        repository.writes.clear();
        repository.stored = repairInProgress();
        interactor.failRepair(ExecutionFixtures.WORK_ORDER_ID, "parafuso quebrou",
                ExecutionFixtures.MECHANIC, null);
        assertEquals(SagaMessageType.REPAIR_FAILED, repository.onlyWrite().event().envelope().type());
        assertEquals(ExecutionStatus.REPAIR_FAILED, repository.onlyWrite().next().status());
    }

    @Test
    void reportsAWorkOrderThatIsNotInTheQueue() {
        assertThrows(ExecutionItemNotFoundException.class, () -> interactor.startDiagnosis(
                ExecutionFixtures.WORK_ORDER_ID, ExecutionFixtures.MECHANIC, null));
    }

    // Transicao pedida por pessoa recusa com conflito, ao contrario da dirigida pela saga, que devolve vazio.
    @Test
    void refusesATransitionTheCurrentStateDoesNotAllow() {
        repository.stored = ExecutionFixtures.queued();

        ExecutionConflictException thrown = assertThrows(ExecutionConflictException.class,
                () -> interactor.completeRepair(ExecutionFixtures.WORK_ORDER_ID,
                        ExecutionFixtures.MECHANIC, null));

        assertTrue(thrown.getMessage().contains("DIAGNOSIS_QUEUED"));
        assertTrue(repository.writes.isEmpty());
    }

    @Test
    void refusesWhenAnotherWriterMovedTheItemFirst() {
        repository.stored = ExecutionFixtures.queued();
        repository.result = ExecutionWriteResult.CONFLICT;

        assertThrows(ExecutionConflictException.class, () -> interactor.startDiagnosis(
                ExecutionFixtures.WORK_ORDER_ID, ExecutionFixtures.MECHANIC, null));
    }

    @Test
    void refusesADiagnosisThatIsNotWellFormed() {
        repository.stored = diagnosisInProgress();

        assertThrows(InvalidDiagnosisException.class, () -> interactor.completeDiagnosis(
                ExecutionFixtures.WORK_ORDER_ID, " ", ExecutionFixtures.diagnosedItems(),
                ExecutionFixtures.MECHANIC, null));
        assertThrows(InvalidDiagnosisException.class, () -> interactor.completeDiagnosis(
                ExecutionFixtures.WORK_ORDER_ID, "Amortecedor", List.of(),
                ExecutionFixtures.MECHANIC, null));
        assertThrows(InvalidDiagnosisException.class, () -> interactor.completeDiagnosis(
                ExecutionFixtures.WORK_ORDER_ID, "Amortecedor",
                List.of(new DiagnosedItem("WHATEVER", 7L, 1)), ExecutionFixtures.MECHANIC, null));

        assertTrue(repository.writes.isEmpty());
    }

    // O catalogo e consultado antes de qualquer escrita: item invalido ou OS fora do ar nao entram na saga.
    @Test
    void doesNotWriteAnythingWhenTheCatalogRefusesOrIsUnavailable() {
        repository.stored = diagnosisInProgress();
        catalog.refusal = new CatalogUnavailableException("circuito aberto");

        assertThrows(CatalogUnavailableException.class, () -> interactor.completeDiagnosis(
                ExecutionFixtures.WORK_ORDER_ID, "Amortecedor gasto", ExecutionFixtures.diagnosedItems(),
                ExecutionFixtures.MECHANIC, null));

        assertTrue(repository.writes.isEmpty());
    }

    private static ExecutionItem diagnosisInProgress() {
        return ExecutionFixtures.queued()
                .startDiagnosis(ExecutionFixtures.MECHANIC, ExecutionFixtures.NOW).orElseThrow();
    }

    private static ExecutionItem repairQueued() {
        return diagnosisInProgress()
                .completeDiagnosis("Amortecedor gasto", ExecutionFixtures.diagnosedItems(),
                        ExecutionFixtures.NOW).orElseThrow()
                .queueForRepair(ExecutionFixtures.repairItems(), ExecutionFixtures.NOW).orElseThrow();
    }

    private static ExecutionItem repairInProgress() {
        return repairQueued().startRepair(ExecutionFixtures.MECHANIC, ExecutionFixtures.NOW).orElseThrow();
    }
}
