package br.com.fiap.postech.carworkshop.execution.usecase.port.out;

import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionItem;
import br.com.fiap.postech.carworkshop.execution.domain.entity.ExecutionStep;
import br.com.fiap.postech.carworkshop.execution.domain.message.ExecutionEvent;

import java.time.Instant;
import java.util.UUID;

public record ExecutionWrite(Long workOrderId,
                             ExecutionItem previous,
                             ExecutionItem next,
                             ExecutionStep step,
                             ExecutionEvent event,
                             UUID inboxMessageId,
                             Instant inboxExpiresAt) {

    public static ExecutionWrite create(ExecutionItem state, ExecutionStep step, UUID inboxMessageId,
                                        Instant inboxExpiresAt) {
        return new ExecutionWrite(state.workOrderId(), null, state, step, null, inboxMessageId, inboxExpiresAt);
    }

    public static ExecutionWrite transition(ExecutionItem previous, ExecutionItem next, ExecutionStep step,
                                            ExecutionEvent event) {
        return new ExecutionWrite(previous.workOrderId(), previous, next, step, event, null, null);
    }

    public static ExecutionWrite transitionFromCommand(ExecutionItem previous, ExecutionItem next,
                                                       ExecutionStep step, ExecutionEvent event,
                                                       UUID inboxMessageId, Instant inboxExpiresAt) {
        return new ExecutionWrite(previous.workOrderId(), previous, next, step, event, inboxMessageId,
                inboxExpiresAt);
    }

    public static ExecutionWrite withoutStateChange(Long workOrderId, ExecutionStep step,
                                                    ExecutionEvent event, UUID inboxMessageId,
                                                    Instant inboxExpiresAt) {
        return new ExecutionWrite(workOrderId, null, null, step, event, inboxMessageId, inboxExpiresAt);
    }

    public boolean createsState() {
        return previous == null && next != null;
    }

    public boolean updatesState() {
        return previous != null && next != null;
    }
}
