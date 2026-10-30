package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.ExecutionEventPublisher;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.OutboundMessage;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.TestCodec;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.support.ExecutionFixtures;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionOutboxPort;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.PendingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OutboxRelayServiceTest {

    private ExecutionOutboxPort outbox;
    private ExecutionEventPublisher publisher;
    private OutboxRelayService relay;

    @BeforeEach
    void setUp() {
        outbox = mock(ExecutionOutboxPort.class);
        publisher = mock(ExecutionEventPublisher.class);
        relay = new OutboxRelayService();
        relay.outbox = outbox;
        relay.publisher = publisher;
        relay.codec = TestCodec.codec();
        relay.clock = Clock.fixed(ExecutionFixtures.NOW, ZoneOffset.UTC);
        relay.batchSize = 10;
    }

    // O scheduler esta desligado no perfil de teste, entao o metodo e chamado direto e o teste afirma o efeito:
    // envelope publicado e mensagem marcada como enviada.
    @Test
    void publishesThePendingEventAndMarksItAsSent() {
        when(outbox.claimPending(10)).thenReturn(List.of(pending()));
        when(outbox.markSent(anyLong(), any(UUID.class), any())).thenReturn(true);

        relay.drain();

        ArgumentCaptor<OutboundMessage> captor = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(publisher).publish(captor.capture());
        OutboundMessage message = captor.getValue();
        assertEquals(ExecutionFixtures.MESSAGE_ID, message.messageId());
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED, message.type());
        assertEquals(1, message.version());
        assertEquals(ExecutionFixtures.WORK_ORDER_ID, message.workOrderId());
        assertEquals(ExecutionFixtures.SAGA_ID, message.sagaId());
        assertEquals(ExecutionFixtures.CORRELATION_ID, message.correlationId());
        assertEquals(SagaMessageType.DIAGNOSIS_CANCELED,
                TestCodec.codec().decode(message.body()).path("type").asText());
        verify(outbox).markSent(ExecutionFixtures.WORK_ORDER_ID, ExecutionFixtures.MESSAGE_ID,
                ExecutionFixtures.NOW);
    }

    @Test
    void publishesNothingWhenThereIsNoPendingEvent() {
        when(outbox.claimPending(10)).thenReturn(List.of());

        relay.drain();

        verifyNoInteractions(publisher);
    }

    // Publica e so depois marca: marcar primeiro perderia a mensagem se o publish falhasse.
    @Test
    void leavesTheEventPendingWhenThePublishFails() {
        when(outbox.claimPending(10)).thenReturn(List.of(pending()));
        doThrow(new IllegalStateException("sns down")).when(publisher).publish(any());

        relay.drain();

        verify(outbox, never()).markSent(anyLong(), any(UUID.class), any());
    }

    // Para o lote na primeira falha: a ordem por ordem de servico importa, e nao se publica o evento seguinte
    // da mesma OS sem o anterior ter saido.
    @Test
    void stopsTheBatchAtTheFirstFailure() {
        when(outbox.claimPending(10)).thenReturn(List.of(pending(), pending()));
        doThrow(new IllegalStateException("sns down")).when(publisher).publish(any());

        relay.drain();

        verify(publisher, times(1)).publish(any());
    }

    @Test
    void keepsGoingWhenAnotherReplicaHadAlreadyMarkedTheEvent() {
        when(outbox.claimPending(10)).thenReturn(List.of(pending(), pending()));
        when(outbox.markSent(anyLong(), any(UUID.class), eq(ExecutionFixtures.NOW))).thenReturn(false);

        relay.drain();

        verify(publisher, times(2)).publish(any());
    }

    private static PendingEvent pending() {
        return new PendingEvent(ExecutionFixtures.MESSAGE_ID, SagaMessageType.DIAGNOSIS_CANCELED, 1,
                ExecutionFixtures.NOW, ExecutionFixtures.SAGA_ID, ExecutionFixtures.WORK_ORDER_ID,
                ExecutionFixtures.BRANCH, ExecutionFixtures.CORRELATION_ID, null, "{}");
    }
}
