package br.com.fiap.postech.carworkshop.execution.infrastructure.config;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.LoggingExecutionEventGateway;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.OsCatalogGateway;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.SnsExecutionEventGateway;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.CatalogValidationPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.fiap.postech.carworkshop.execution.support.RecordingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ExecutionModuleConfigTest {

    private ExecutionModuleConfig config;

    @BeforeEach
    void setUp() {
        config = new ExecutionModuleConfig();
        config.objectMapper = new ObjectMapper();
        config.topicArn = "arn:aws:sns:us-east-1:000000000000:execution-events.fifo";
        config.inboxRetention = Duration.ofDays(7);
        config.catalogBaseUrl = "http://localhost:9999";
        config.catalogServiceToken = "test-service-token";
        config.catalogTimeout = Duration.ofSeconds(2);
        config.catalogMaxAttempts = 2;
        config.catalogFailureThreshold = 3;
        config.catalogCircuitOpenDuration = Duration.ofSeconds(30);
    }

    @Test
    void producesTheMechanicUseCaseAndTheCatalogClient() {
        CatalogValidationPort catalog = config.catalogValidationPort(Clock.systemUTC());

        assertInstanceOf(OsCatalogGateway.class, catalog);
        assertNotNull(config.mechanicUseCase(new RecordingRepository(), catalog, Clock.systemUTC()));
    }

    @Test
    void producesAClock() {
        assertNotNull(config.clock());
    }

    @Test
    void producesTheCommandUseCase() {
        assertNotNull(config.executionCommandUseCase(new RecordingRepository(), Clock.systemUTC()));
    }

    // O ElasticMQ nao emula SNS: o perfil local troca o publicador por um que registra em log, e e por isto que
    // a escolha e de configuracao e nao de anotacao.
    @Test
    void choosesThePublisherByProfile() {
        config.publishToLog = true;
        assertInstanceOf(LoggingExecutionEventGateway.class,
                config.executionEventPublisher(mock(SnsClient.class)));

        config.publishToLog = false;
        assertInstanceOf(SnsExecutionEventGateway.class,
                config.executionEventPublisher(mock(SnsClient.class)));
    }
}
