package br.com.fiap.postech.carworkshop.execution.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceInfoTest {

    @Test
    void keepsNameAndVersion() {
        ServiceInfo info = new ServiceInfo("execution-service", "1.0.0");

        assertEquals("execution-service", info.name());
        assertEquals("1.0.0", info.version());
    }

    @Test
    void rejectsMissingNameOrVersion() {
        assertThrows(NullPointerException.class, () -> new ServiceInfo(null, "1.0.0"));
        assertThrows(NullPointerException.class, () -> new ServiceInfo("execution-service", null));
    }
}
