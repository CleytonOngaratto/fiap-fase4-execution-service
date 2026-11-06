package br.com.fiap.postech.carworkshop.execution.infrastructure.observability;

import com.newrelic.api.agent.Agent;
import com.newrelic.api.agent.ErrorApi;
import com.newrelic.api.agent.Headers;
import com.newrelic.api.agent.Transaction;
import com.newrelic.api.agent.TransactionNamePriority;
import com.newrelic.api.agent.TransportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewRelicTraceContextTest {

    private Agent agent;
    private Transaction transaction;
    private NewRelicTraceContext trace;

    @BeforeEach
    void setUp() {
        agent = mock(Agent.class);
        transaction = mock(Transaction.class);
        when(agent.getTransaction()).thenReturn(transaction);
        trace = new NewRelicTraceContext(() -> agent);
    }

    @Test
    void capturesOnlyTheTraceHeadersTheAgentInserted() {
        doAnswer(invocation -> {
            Headers headers = invocation.getArgument(0);
            headers.setHeader("traceparent", "00-trace-span-01");
            headers.setHeader("tracestate", "nr=state");
            headers.setHeader("newrelic", "payload");
            headers.setHeader("x-other", "ignored");
            return null;
        }).when(transaction).insertDistributedTraceHeaders(any(Headers.class));

        assertEquals(Map.of("traceparent", "00-trace-span-01", "tracestate", "nr=state", "newrelic", "payload"),
                trace.capture());
    }

    @Test
    void capturesNothingWhenTheAgentFails() {
        doThrow(new IllegalStateException("agent down"))
                .when(transaction).insertDistributedTraceHeaders(any(Headers.class));

        assertTrue(trace.capture().isEmpty());
    }

    @Test
    void acceptsOnlyTheTraceHeadersAsAQueueTransport() {
        trace.accept(Map.of("traceparent", "00-trace-span-01", "type", "DiagnosisStarted"));

        ArgumentCaptor<Headers> captor = ArgumentCaptor.forClass(Headers.class);
        verify(transaction).acceptDistributedTraceHeaders(eq(TransportType.Queue), captor.capture());
        assertEquals("00-trace-span-01", captor.getValue().getHeader("traceparent"));
        assertNull(captor.getValue().getHeader("type"));
    }

    @Test
    void acceptsNothingWhenNoTraceHeaderCame() {
        trace.accept(Map.of("type", "DiagnosisStarted"));

        verify(transaction, never()).acceptDistributedTraceHeaders(any(), any());
    }

    @Test
    void keepsGoingWhenTheAgentRefusesTheHeaders() {
        doThrow(new IllegalStateException("agent down"))
                .when(transaction).acceptDistributedTraceHeaders(any(), any());

        assertDoesNotThrow(() -> trace.accept(Map.of("traceparent", "00-trace-span-01")));
    }

    @Test
    void namesTheTransactionWithACustomName() {
        trace.nameTransaction("Message", "execution-commands/StartDiagnosis");

        verify(transaction).setTransactionName(TransactionNamePriority.CUSTOM_HIGH, true, "Message",
                "execution-commands/StartDiagnosis");
    }

    @Test
    void keepsGoingWhenTheTransactionCannotBeNamed() {
        doThrow(new IllegalStateException("agent down"))
                .when(transaction).setTransactionName(TransactionNamePriority.CUSTOM_HIGH, true, "Message", "x");

        assertDoesNotThrow(() -> trace.nameTransaction("Message", "x"));
    }

    @Test
    void ignoresTheTransactionWhenAsked() {
        trace.ignoreTransaction();

        verify(transaction).ignore();
    }

    @Test
    void keepsGoingWhenTheTransactionCannotBeIgnored() {
        doThrow(new IllegalStateException("agent down")).when(transaction).ignore();

        assertDoesNotThrow(() -> trace.ignoreTransaction());
    }

    @Test
    void reportsTheErrorToTheCurrentTransaction() {
        ErrorApi errors = mock(ErrorApi.class);
        when(agent.getErrorApi()).thenReturn(errors);
        IllegalStateException error = new IllegalStateException("boom");

        trace.noticeError(error);

        verify(errors).noticeError(error);
    }

    @Test
    void keepsGoingWhenTheErrorCannotBeReported() {
        when(agent.getErrorApi()).thenThrow(new IllegalStateException("agent down"));

        assertDoesNotThrow(() -> trace.noticeError(new IllegalStateException("boom")));
    }

    @Test
    void addsAttributesWithoutFailingWhenThereIsNoAgent() {
        assertDoesNotThrow(() -> {
            trace.addAttribute("sagaId", null);
            trace.addAttribute("sagaId", " ");
            trace.addAttribute("sagaId", "22222222-2222-2222-2222-222222222222");
        });
    }

    @Test
    void capturesNothingWithTheAgentOfTheApiWhenNoAgentIsAttached() {
        assertTrue(new NewRelicTraceContext().capture().isEmpty());
    }

    @Test
    void reportsErrorsWithoutFailingWhenNoAgentIsAttached() {
        assertDoesNotThrow(() -> new NewRelicTraceContext().noticeError(new IllegalStateException("boom")));
    }
}
