package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import br.com.fiap.postech.carworkshop.execution.adapter.dto.CompleteDiagnosisRequest;
import br.com.fiap.postech.carworkshop.execution.adapter.dto.DiagnosedItemRequest;
import br.com.fiap.postech.carworkshop.execution.adapter.dto.FailureRequest;
import br.com.fiap.postech.carworkshop.execution.domain.entity.DiagnosedItem;
import br.com.fiap.postech.carworkshop.execution.domain.exception.InvalidRequestException;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.MechanicUseCase;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderExecutionControllerUnitTest {

    private static final Long WORK_ORDER = ExecutionFixtures.WORK_ORDER_ID;
    private static final String TRACE = ExecutionFixtures.CORRELATION_ID;

    private MechanicUseCase useCase;
    private WorkOrderExecutionController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(MechanicUseCase.class);
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getName()).thenReturn(ExecutionFixtures.MECHANIC);
        controller = new WorkOrderExecutionController();
        controller.useCase = useCase;
        controller.jwt = jwt;
    }

    @Test
    void acceptsEachActionAndPassesTheMechanicAndTheTrace() {
        assertEquals(202, controller.startDiagnosis(WORK_ORDER, TRACE).getStatus());
        verify(useCase).startDiagnosis(WORK_ORDER, ExecutionFixtures.MECHANIC, TRACE);

        assertEquals(202, controller.startRepair(WORK_ORDER, TRACE).getStatus());
        verify(useCase).startRepair(WORK_ORDER, ExecutionFixtures.MECHANIC, TRACE);

        assertEquals(202, controller.completeRepair(WORK_ORDER, TRACE).getStatus());
        verify(useCase).completeRepair(WORK_ORDER, ExecutionFixtures.MECHANIC, TRACE);
    }

    @Test
    void translatesTheDiagnosisBodyIntoDomainItems() {
        CompleteDiagnosisRequest request = new CompleteDiagnosisRequest("Amortecedor gasto",
                List.of(new DiagnosedItemRequest(DiagnosedItem.PART, 12L, 3)));

        assertEquals(202, controller.completeDiagnosis(WORK_ORDER, request, TRACE).getStatus());

        verify(useCase).completeDiagnosis(WORK_ORDER, "Amortecedor gasto",
                List.of(new DiagnosedItem(DiagnosedItem.PART, 12L, 3)), ExecutionFixtures.MECHANIC, TRACE);
    }

    @Test
    void readsAnAbsentQuantityAsZeroSoTheDomainRefusesIt() {
        CompleteDiagnosisRequest request = new CompleteDiagnosisRequest("Amortecedor",
                List.of(new DiagnosedItemRequest(DiagnosedItem.PART, 12L, null)));

        assertEquals(0, request.toDomain().get(0).quantity());
    }

    @Test
    void readsAnAbsentItemListAsEmpty() {
        assertEquals(List.of(), new CompleteDiagnosisRequest("Amortecedor", null).toDomain());
    }

    @Test
    void refusesAnEmptyDiagnosisBody() {
        assertThrows(InvalidRequestException.class, () -> controller.completeDiagnosis(WORK_ORDER, null, TRACE));
    }

    @Test
    void refusesAFailureWithoutAReason() {
        assertThrows(InvalidRequestException.class,
                () -> controller.failDiagnosis(WORK_ORDER, null, TRACE));
        assertThrows(InvalidRequestException.class,
                () -> controller.failDiagnosis(WORK_ORDER, new FailureRequest(null), TRACE));
        assertThrows(InvalidRequestException.class,
                () -> controller.failRepair(WORK_ORDER, new FailureRequest(" "), TRACE));
    }

    @Test
    void acceptsAFailureWithAReason() {
        assertEquals(202,
                controller.failDiagnosis(WORK_ORDER, new FailureRequest("sem reparo viavel"), TRACE)
                        .getStatus());
        verify(useCase).failDiagnosis(WORK_ORDER, "sem reparo viavel", ExecutionFixtures.MECHANIC, TRACE);

        assertEquals(202,
                controller.failRepair(WORK_ORDER, new FailureRequest("parafuso quebrou"), TRACE).getStatus());
        verify(useCase).failRepair(WORK_ORDER, "parafuso quebrou", ExecutionFixtures.MECHANIC, TRACE);
    }
}
