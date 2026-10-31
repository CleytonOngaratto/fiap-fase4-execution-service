package br.com.fiap.postech.carworkshop.execution.infrastructure.config;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.CircuitBreaker;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.ExecutionEventPublisher;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.LoggingExecutionEventGateway;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.OsCatalogGateway;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.SnsExecutionEventGateway;
import br.com.fiap.postech.carworkshop.execution.usecase.interactor.ExecutionCommandInteractor;
import br.com.fiap.postech.carworkshop.execution.usecase.interactor.MechanicInteractor;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.ExecutionCommandUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.MechanicUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.CatalogValidationPort;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionItemRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sns.SnsClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@ApplicationScoped
public class ExecutionModuleConfig {

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "execution.events.topic-arn")
    String topicArn;

    @ConfigProperty(name = "execution.events.publish-to-log")
    boolean publishToLog;

    @ConfigProperty(name = "execution.inbox.retention")
    Duration inboxRetention;

    @ConfigProperty(name = "execution.catalog.base-url")
    String catalogBaseUrl;

    @ConfigProperty(name = "execution.catalog.service-token")
    String catalogServiceToken;

    @ConfigProperty(name = "execution.catalog.timeout")
    Duration catalogTimeout;

    @ConfigProperty(name = "execution.catalog.max-attempts")
    int catalogMaxAttempts;

    @ConfigProperty(name = "execution.catalog.circuit.failure-threshold")
    int catalogFailureThreshold;

    @ConfigProperty(name = "execution.catalog.circuit.open-duration")
    Duration catalogCircuitOpenDuration;

    @Produces
    @ApplicationScoped
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Produces
    @ApplicationScoped
    public ExecutionCommandUseCase executionCommandUseCase(ExecutionItemRepositoryPort items, Clock clock) {
        return new ExecutionCommandInteractor(items, clock, inboxRetention);
    }

    @Produces
    @ApplicationScoped
    public MechanicUseCase mechanicUseCase(ExecutionItemRepositoryPort items, CatalogValidationPort catalog,
                                           Clock clock) {
        return new MechanicInteractor(items, catalog, clock);
    }

    @Produces
    @ApplicationScoped
    public CatalogValidationPort catalogValidationPort(Clock clock) {
        return new OsCatalogGateway(catalogBaseUrl, catalogServiceToken, catalogTimeout, catalogMaxAttempts,
                catalogHttpClient(), objectMapper,
                new CircuitBreaker(catalogFailureThreshold, catalogCircuitOpenDuration), clock);
    }

    // HTTP_1_1 explicito: o default do HttpClient e HTTP_2, e o upgrade h2c em texto claro contra o ALB interno
    // fica sem resposta nenhuma ate o timeout, entao toda validacao de item responderia 503 ao mecanico.
    HttpClient catalogHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(catalogTimeout)
                .build();
    }

    @Produces
    @ApplicationScoped
    public ExecutionEventPublisher executionEventPublisher(SnsClient sns) {
        return publishToLog
                ? new LoggingExecutionEventGateway()
                : new SnsExecutionEventGateway(sns, topicArn);
    }
}
