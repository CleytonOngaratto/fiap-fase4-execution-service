package br.com.fiap.postech.carworkshop.execution.infrastructure.observability;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceHeadersTest {

    @Test
    void keepsOnlyTheTraceHeadersThatHaveAValue() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-trace-span-01");
        headers.put("tracestate", " ");
        headers.put("newrelic", null);
        headers.put("type", "DiagnosisStarted");

        assertEquals(Map.of("traceparent", "00-trace-span-01"), TraceHeaders.only(headers));
    }

    @Test
    void keepsNothingWhenThereAreNoHeaders() {
        assertTrue(TraceHeaders.only(null).isEmpty());
    }

    @Test
    void roundTripsTheHeadersThroughJson() {
        Map<String, String> headers = Map.of("traceparent", "00-trace-span-01", "tracestate", "nr=state",
                "newrelic", "payload");

        assertEquals(headers, TraceHeaders.fromJson(TraceHeaders.toJson(headers)));
    }

    @Test
    void writesNoJsonWhenThereIsNothingToKeep() {
        assertNull(TraceHeaders.toJson(Map.of("type", "DiagnosisStarted")));
    }

    @Test
    void readsNothingFromAbsentOrCorruptJson() {
        assertTrue(TraceHeaders.fromJson(null).isEmpty());
        assertTrue(TraceHeaders.fromJson(" ").isEmpty());
        assertTrue(TraceHeaders.fromJson("{not json").isEmpty());
        assertTrue(TraceHeaders.fromJson("null").isEmpty());
        assertTrue(TraceHeaders.fromJson("[1, 2]").isEmpty());
    }

    @Test
    void dropsUnknownNamesAndEmptyValuesWhenReading() {
        assertEquals(Map.of("traceparent", "00-trace-span-01"),
                TraceHeaders.fromJson("{\"traceparent\":\"00-trace-span-01\",\"tracestate\":\"\",\"x\":\"y\"}"));
    }
}
