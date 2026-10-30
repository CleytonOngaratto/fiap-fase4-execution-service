package br.com.fiap.postech.carworkshop.execution.adapter.gateway;

import java.time.Duration;
import java.time.Instant;

public class CircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;

    private int consecutiveFailures;
    private Instant openUntil;

    public CircuitBreaker(int failureThreshold, Duration openDuration) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    public synchronized boolean isOpen(Instant now) {
        return openUntil != null && now.isBefore(openUntil);
    }

    // Ao abrir, o contador fica a uma falha do limite: depois da janela o circuito deixa passar uma chamada de
    // prova e uma unica falha o reabre, em vez de exigir o limite inteiro outra vez.
    public synchronized void recordFailure(Instant now) {
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            openUntil = now.plus(openDuration);
            consecutiveFailures = failureThreshold - 1;
        }
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openUntil = null;
    }
}
