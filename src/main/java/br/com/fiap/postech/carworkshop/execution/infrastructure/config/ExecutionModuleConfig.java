package br.com.fiap.postech.carworkshop.execution.infrastructure.config;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.ExecutionEventPublisher;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.LoggingExecutionEventGateway;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.SnsExecutionEventGateway;
import br.com.fiap.postech.carworkshop.execution.usecase.interactor.ExecutionCommandInteractor;
import br.com.fiap.postech.carworkshop.execution.usecase.port.in.ExecutionCommandUseCase;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionItemRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sns.SnsClient;

import java.time.Clock;
import java.time.Duration;

@ApplicationScoped
public class ExecutionModuleConfig {

    @ConfigProperty(name = "execution.events.topic-arn")
    String topicArn;

    @ConfigProperty(name = "execution.events.publish-to-log")
    boolean publishToLog;

    @ConfigProperty(name = "execution.inbox.retention")
    Duration inboxRetention;

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
    public ExecutionEventPublisher executionEventPublisher(SnsClient sns) {
        return publishToLog
                ? new LoggingExecutionEventGateway()
                : new SnsExecutionEventGateway(sns, topicArn);
    }
}
