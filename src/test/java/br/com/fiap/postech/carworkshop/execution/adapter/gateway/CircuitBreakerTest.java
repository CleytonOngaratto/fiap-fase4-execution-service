package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Test
    void staysClosedBelowTheThreshold() {
        CircuitBreaker circuit = new CircuitBreaker(3, Duration.ofSeconds(30));

        circuit.recordFailure(NOW);
        circuit.recordFailure(NOW);

        assertFalse(circuit.isOpen(NOW));
    }

    @Test
    void opensAtTheThresholdAndClosesWhenTheWindowPasses() {
        CircuitBreaker circuit = new CircuitBreaker(3, Duration.ofSeconds(30));

        circuit.recordFailure(NOW);
        circuit.recordFailure(NOW);
        circuit.recordFailure(NOW);

        assertTrue(circuit.isOpen(NOW));
        assertTrue(circuit.isOpen(NOW.plusSeconds(29)));
        assertFalse(circuit.isOpen(NOW.plusSeconds(30)));
    }

    // Meia-abertura: passada a janela, uma unica falha reabre o circuito, sem esperar o limite inteiro.
    @Test
    void reopensOnTheFirstFailureAfterTheWindow() {
        CircuitBreaker circuit = new CircuitBreaker(3, Duration.ofSeconds(30));
        circuit.recordFailure(NOW);
        circuit.recordFailure(NOW);
        circuit.recordFailure(NOW);
        Instant later = NOW.plusSeconds(31);

        circuit.recordFailure(later);

        assertTrue(circuit.isOpen(later));
    }

    @Test
    void closesOnASuccess() {
        CircuitBreaker circuit = new CircuitBreaker(2, Duration.ofSeconds(30));
        circuit.recordFailure(NOW);
        circuit.recordFailure(NOW);

        circuit.recordSuccess();

        assertFalse(circuit.isOpen(NOW));
    }

    @Test
    void forgetsIsolatedFailuresAfterASuccess() {
        CircuitBreaker circuit = new CircuitBreaker(2, Duration.ofSeconds(30));

        circuit.recordFailure(NOW);
        circuit.recordSuccess();
        circuit.recordFailure(NOW);

        assertFalse(circuit.isOpen(NOW));
    }
}
