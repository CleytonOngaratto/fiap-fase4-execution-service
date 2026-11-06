package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.TestCodec;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.infrastructure.observability.LogContext;
import br.com.fiap.postech.carworkshop.execution.infrastructure.observability.LogLines;
import br.com.fiap.postech.carworkshop.execution.infrastructure.observability.TraceContext;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutionCommandsConsumerTest {

    private static final String QUEUE_URL = "http://localhost:9324/000000000000/execution-commands.fifo";
    private static final String RECEIPT = "receipt-1";

    private SqsClient sqs;
    private ExecutionCommandHandler handler;
    private TraceContext trace;
    private ExecutionCommandsConsumer consumer;

    @BeforeEach
    void setUp() {
        sqs = mock(SqsClient.class);
        handler = mock(ExecutionCommandHandler.class);
        trace = mock(TraceContext.class);
        SagaEnvelopeCodec codec = TestCodec.codec();
        consumer = new ExecutionCommandsConsumer();
        consumer.sqs = sqs;
        consumer.codec = codec;
        consumer.handler = handler;
        consumer.trace = trace;
        consumer.commandsQueueUrl = QUEUE_URL;
        consumer.batchSize = 10;
    }

    @Test
    void handsEveryReceivedMessageToTheHandler() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(message(body(SagaMessageType.START_DIAGNOSIS)),
                        message(body(SagaMessageType.CANCEL_DIAGNOSIS)))
                .build());
        when(handler.apply(any(JsonNode.class), anyString())).thenReturn(ExecutionWriteResult.APPLIED);

        consumer.poll();

        verify(handler).apply(any(JsonNode.class), eq(SagaMessageType.START_DIAGNOSIS));
        verify(handler).apply(any(JsonNode.class), eq(SagaMessageType.CANCEL_DIAGNOSIS));
        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void deletesTheMessageOfAnAppliedCommand() {
        when(handler.apply(any(JsonNode.class), anyString())).thenReturn(ExecutionWriteResult.APPLIED);

        consumer.consume(message(body(SagaMessageType.START_REPAIR)));

        verify(sqs).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL).receiptHandle(RECEIPT).build());
    }

    // Duplicata e conflito sao resultado de negocio: a mensagem sai da fila. Deixa-la travaria todas as
    // seguintes da mesma ordem de servico, porque a fila e FIFO por OS.
    @Test
    void deletesTheMessageOfADuplicateAndOfAConflict() {
        when(handler.apply(any(JsonNode.class), anyString())).thenReturn(ExecutionWriteResult.DUPLICATE);
        consumer.consume(message(body(SagaMessageType.CANCEL_DIAGNOSIS)));

        when(handler.apply(any(JsonNode.class), anyString())).thenReturn(ExecutionWriteResult.CONFLICT);
        consumer.consume(message(body(SagaMessageType.START_REPAIR)));

        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void dropsAnUnknownCommandTypeWithoutCallingTheHandler() {
        consumer.consume(message(body("PaintTheCar")));

        verifyNoInteractions(handler);
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void leavesAMessageThatIsNotJsonForTheRedrivePolicy() {
        consumer.consume(message("nao e json"));

        verifyNoInteractions(handler);
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void leavesAMessageWhoseCommandFailedForTheRedrivePolicy() {
        when(handler.apply(any(JsonNode.class), anyString()))
                .thenThrow(new IllegalStateException("dynamo down"));

        consumer.consume(message(body(SagaMessageType.START_DIAGNOSIS)));

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void acceptsTheTraceContextThatCameWithTheCommand() {
        when(handler.apply(any(JsonNode.class), anyString())).thenReturn(ExecutionWriteResult.APPLIED);

        consumer.consume(message(body(SagaMessageType.START_DIAGNOSIS)).toBuilder()
                .messageAttributes(Map.of(
                        "traceparent", text("00-trace-span-01"),
                        "newrelic", text("payload"),
                        "type", text(SagaMessageType.START_DIAGNOSIS)))
                .build());

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(trace).accept(captor.capture());
        assertEquals("00-trace-span-01", captor.getValue().get("traceparent"));
        assertEquals("payload", captor.getValue().get("newrelic"));
    }

    @Test
    void namesTheTransactionAfterTheQueueAndTheCommandType() {
        when(handler.apply(any(JsonNode.class), anyString())).thenReturn(ExecutionWriteResult.APPLIED);

        consumer.consume(message(body(SagaMessageType.START_DIAGNOSIS)));

        verify(trace).nameTransaction("Message", "execution-commands/" + SagaMessageType.START_DIAGNOSIS);
        verify(trace).addAttribute("messageType", SagaMessageType.START_DIAGNOSIS);
        verify(trace).addAttribute(LogContext.SAGA_ID, "6f1d0f3a-0000-4000-8000-000000000001");
        verify(trace).addAttribute("workOrderId", "42");
        verify(trace).addAttribute(LogContext.BRANCH_ID, "SP-01");
    }

    @Test
    void namesTheTransactionOfAnUnreadableCommand() {
        consumer.consume(message("not json"));

        verify(trace).nameTransaction("Message", "execution-commands/unreadable");
    }

    @Test
    void reportsTheFailureToTheTransactionOfTheCommand() {
        IllegalStateException failure = new IllegalStateException("dynamo down");
        when(handler.apply(any(JsonNode.class), anyString())).thenThrow(failure);

        consumer.consume(message(body(SagaMessageType.START_DIAGNOSIS)));

        verify(trace).noticeError(failure);
    }

    @Test
    void reportsAnUnreadableCommandToItsTransaction() {
        consumer.consume(message("not json"));

        verify(trace).noticeError(any(RuntimeException.class));
    }

    @Test
    void reportsNoErrorForACommandThatWasApplied() {
        when(handler.apply(any(JsonNode.class), anyString())).thenReturn(ExecutionWriteResult.APPLIED);

        consumer.consume(message(body(SagaMessageType.START_DIAGNOSIS)));

        verify(trace, never()).noticeError(any());
    }

    @Test
    void logsTheFailureWithTheContextOfTheCommand() {
        when(handler.apply(any(JsonNode.class), anyString()))
                .thenThrow(new IllegalStateException("dynamo down"));

        try (LogLines lines = LogLines.of(ExecutionCommandsConsumer.class)) {
            consumer.consume(message(body(SagaMessageType.START_DIAGNOSIS)));

            assertEquals(List.of(Map.of(
                    LogContext.CORRELATION_ID, "EVIDENCIA-B6-001",
                    LogContext.SAGA_ID, "6f1d0f3a-0000-4000-8000-000000000001",
                    LogContext.BRANCH_ID, "SP-01")), lines.errors());
        }
    }

    // O sagaId so alimenta o log: um valor malformado ou ausente nao pode derrubar o processamento.
    @Test
    void processesACommandWhoseSagaIdentifierIsMalformedOrAbsent() {
        when(handler.apply(any(JsonNode.class), anyString())).thenReturn(ExecutionWriteResult.APPLIED);

        consumer.consume(message("{\"type\":\"CancelDiagnosis\",\"sagaId\":\"nao-e-uuid\","
                + "\"workOrderId\":42,\"payload\":{}}"));
        consumer.consume(message("{\"type\":\"CancelDiagnosis\",\"workOrderId\":42,\"payload\":{}}"));

        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    private static MessageAttributeValue text(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private static Message message(String body) {
        return Message.builder().body(body).receiptHandle(RECEIPT).build();
    }

    private static String body(String type) {
        return "{\"messageId\":\"0b8f1c2e-0000-4000-8000-000000000002\",\"type\":\"" + type + "\","
                + "\"sagaId\":\"6f1d0f3a-0000-4000-8000-000000000001\",\"workOrderId\":42,\"branchId\":\"SP-01\","
                + "\"correlationId\":\"EVIDENCIA-B6-001\",\"payload\":{}}";
    }
}
