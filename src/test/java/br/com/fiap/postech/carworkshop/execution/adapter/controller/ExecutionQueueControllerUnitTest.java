package br.com.fiap.postech.carworkshop.execution.adapter.controller;

import br.com.fiap.postech.carworkshop.execution.adapter.presenter.QueueItemResponse;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStatus;
import br.com.fiap.postech.carworkshop.execution.domain.exception.InvalidRequestException;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.MechanicUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionQueueControllerUnitTest {

    private MechanicUseCase useCase;
    private ExecutionQueueController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(MechanicUseCase.class);
        controller = new ExecutionQueueController();
        controller.useCase = useCase;
    }

    @Test
    void readsTheQueueWithTheDefaults() {
        when(useCase.queue(anyString(), any(ExecutionStatus.class), anyInt()))
                .thenReturn(List.of(ExecutionFixtures.queued()));

        List<QueueItemResponse> queue = controller.queue(ExecutionFixtures.BRANCH, "DIAGNOSIS_QUEUED", 25);

        verify(useCase).queue(ExecutionFixtures.BRANCH, ExecutionStatus.DIAGNOSIS_QUEUED, 25);
        assertEquals(1, queue.size());
        assertEquals(ExecutionFixtures.WORK_ORDER_ID, queue.get(0).workOrderId());
    }

    @Test
    void refusesAQueueWithoutABranch() {
        assertThrows(InvalidRequestException.class, () -> controller.queue(null, "DIAGNOSIS_QUEUED", 25));
        assertThrows(InvalidRequestException.class, () -> controller.queue(" ", "DIAGNOSIS_QUEUED", 25));
    }

    @Test
    void refusesAnUnknownStatus() {
        assertThrows(InvalidRequestException.class,
                () -> controller.queue(ExecutionFixtures.BRANCH, "PAINTING", 25));
    }

    @Test
    void refusesALimitOutsideTheBounds() {
        assertThrows(InvalidRequestException.class,
                () -> controller.queue(ExecutionFixtures.BRANCH, "DIAGNOSIS_QUEUED", 0));
        assertThrows(InvalidRequestException.class,
                () -> controller.queue(ExecutionFixtures.BRANCH, "DIAGNOSIS_QUEUED", 101));
    }
}
