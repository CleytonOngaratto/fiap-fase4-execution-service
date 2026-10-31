package br.com.fiap.postech.carworkshop.execution.usecase.interactor;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.message.CancelDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartRepairCommand;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.support.RecordingRepository;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWrite;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionCommandInteractorTest {

    private RecordingRepository repository;
    private ExecutionCommandInteractor interactor;

    @BeforeEach
    void setUp() {
        repository = new RecordingRepository();
        interactor = new ExecutionCommandInteractor(repository,
                Clock.fixed(ExecutionFixtures.NOW, ZoneOffset.UTC), Duration.ofDays(7));
    }

    @Test
    void putsTheWorkOrderInTheDiagnosisQueue() {
        ExecutionWriteResult result = interactor.queueDiagnosis(startDiagnosis());

        assertEquals(ExecutionWriteResult.APPLIED, result);
        ExecutionWrite write = repository.onlyWrite();
        assertTrue(write.createsState());
        assertEquals(ExecutionStatus.DIAGNOSIS_QUEUED, write.next().status());
        assertEquals(ExecutionFixtures.BRANCH, write.next().branchId());
        assertEquals(ExecutionFixtures.SAGA_ID, write.next().sagaId());
        assertEquals(ExecutionFixtures.PLATE, write.next().vehiclePlate());
        assertEquals(ExecutionFixtures.COMPLAINT, write.next().complaint());
        assertNull(write.event());
        assertEquals(ExecutionFixtures.MESSAGE_ID, write.inboxMessageId());
        assertEquals(ExecutionFixtures.NOW.plus(Duration.ofDays(7)), write.inboxExpiresAt());
    }

    // Idempotente pela chave de negocio: o comando repetido nao recria o item nem reinicia a fila, mas ainda
    // assim registra a passagem.
    @Test
    void doesNotQueueTheSameWorkOrderTwice() {
        repository.stored = ExecutionFixtures.queued();

        interactor.queueDiagnosis(startDiagnosis());

        ExecutionWrite write = repository.onlyWrite();
        assertFalse(write.createsState());
        assertFalse(write.updatesState());
        assertNull(write.event());
        assertNotNull(write.inboxMessageId());
    }

    // O coracao da divida que o bloco fecha: sem item nenhum o cancelamento e no-op, mas a resposta sai, senao o
    // orquestrador fica em COMPENSATING esperando uma confirmacao que nunca vem.
    @Test
    void answersTheCancellationEvenWhenThereIsNoDiagnosisAtAll() {
        ExecutionWriteResult result = interactor.cancelDiagnosis(cancelDiagnosis("PAYMENT_EXPIRED"));

        assertEquals(ExecutionWriteResult.APPLIED, result);
        ExecutionWrite write = repository.onlyWrite();
        assertFalse(write.createsState());
        assertFalse(write.updatesState());
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED, write.event().envelope().type());
        assertEquals(ExecutionFixtures.WORK_ORDER_ID, write.workOrderId());
        assertEquals(SagaMessageType.CANCEL_DIAGNOSIS, write.step().action());
        assertNull(write.step().status());
    }

    @Test
    void cancelsTheItemAndAnswersWhenTheDiagnosisIsStillOpen() {
        repository.stored = ExecutionFixtures.queued();

        interactor.cancelDiagnosis(cancelDiagnosis("CANCELED_BY_STAFF"));

        ExecutionWrite write = repository.onlyWrite();
        assertTrue(write.updatesState());
        assertEquals(ExecutionStatus.CANCELED, write.next().status());
        assertEquals("CANCELED_BY_STAFF", write.next().statusReason());
        assertEquals(ExecutionStatus.DIAGNOSIS_QUEUED, write.previous().status());
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED, write.event().envelope().type());
    }

    // Depois de uma falha registrada o item nao vira CANCELED - perderia o motivo -, e a resposta sai do mesmo
    // jeito: o orquestrador manda CancelDiagnosis mesmo depois de DiagnosisFailed e de RepairFailed.
    @Test
    void answersTheCancellationWithoutTouchingAnItemThatAlreadyEnded() {
        repository.stored = ExecutionFixtures.queued()
                .startDiagnosis(ExecutionFixtures.MECHANIC, ExecutionFixtures.NOW).orElseThrow()
                .failDiagnosis("sem reparo viavel", ExecutionFixtures.NOW).orElseThrow();

        interactor.cancelDiagnosis(cancelDiagnosis("DIAGNOSIS_FAILED"));

        ExecutionWrite write = repository.onlyWrite();
        assertFalse(write.updatesState());
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED, write.event().envelope().type());
        assertEquals(ExecutionStatus.DIAGNOSIS_FAILED, write.step().status());
    }

    // messageId derivado do comando: a republicacao do mesmo evento cai na deduplicacao do SNS e na inbox do
    // orquestrador em vez de virar uma segunda confirmacao.
    @Test
    void derivesTheReplyIdentifierFromTheCommand() {
        interactor.cancelDiagnosis(cancelDiagnosis("PAYMENT_EXPIRED"));
        UUID first = repository.onlyWrite().event().envelope().messageId();

        repository.writes.clear();
        interactor.cancelDiagnosis(cancelDiagnosis("PAYMENT_EXPIRED"));

        assertEquals(first, repository.onlyWrite().event().envelope().messageId());
        assertEquals(ExecutionFixtures.MESSAGE_ID, repository.onlyWrite().event().envelope().causationId());
    }

    @Test
    void putsTheWorkOrderInTheRepairQueueAndAnswersNothing() {
        repository.stored = diagnosisDone();

        interactor.queueRepair(startRepair());

        ExecutionWrite write = repository.onlyWrite();
        assertTrue(write.updatesState());
        assertEquals(ExecutionStatus.REPAIR_QUEUED, write.next().status());
        assertEquals(ExecutionFixtures.repairItems(), write.next().repairItems());
        assertNull(write.event());
    }

    @Test
    void recordsTheRepairCommandItCannotApply() {
        repository.stored = ExecutionFixtures.queued();

        interactor.queueRepair(startRepair());

        ExecutionWrite write = repository.onlyWrite();
        assertFalse(write.updatesState());
        assertNull(write.event());
        assertEquals(ExecutionStatus.DIAGNOSIS_QUEUED, write.step().status());
    }

    @Test
    void recordsARepairCommandForAWorkOrderItDoesNotKnow() {
        interactor.queueRepair(startRepair());

        ExecutionWrite write = repository.onlyWrite();
        assertFalse(write.createsState());
        assertNull(write.event());
        assertNull(write.step().status());
    }

    @Test
    void reportsTheResultTheRepositoryGives() {
        repository.result = ExecutionWriteResult.DUPLICATE;

        assertEquals(ExecutionWriteResult.DUPLICATE, interactor.queueDiagnosis(startDiagnosis()));
    }

    private static ExecutionItem diagnosisDone() {
        return ExecutionFixtures.queued()
                .startDiagnosis(ExecutionFixtures.MECHANIC, ExecutionFixtures.NOW).orElseThrow()
                .completeDiagnosis("Amortecedor gasto", ExecutionFixtures.diagnosedItems(),
                        ExecutionFixtures.NOW).orElseThrow();
    }

    private static StartDiagnosisCommand startDiagnosis() {
        return new StartDiagnosisCommand(ExecutionFixtures.command(SagaMessageType.START_DIAGNOSIS),
                ExecutionFixtures.PLATE, ExecutionFixtures.COMPLAINT);
    }

    private static CancelDiagnosisCommand cancelDiagnosis(String reason) {
        return new CancelDiagnosisCommand(ExecutionFixtures.command(SagaMessageType.CANCEL_DIAGNOSIS), reason);
    }

    private static StartRepairCommand startRepair() {
        return new StartRepairCommand(ExecutionFixtures.command(SagaMessageType.START_REPAIR),
                ExecutionFixtures.repairItems());
    }
}
