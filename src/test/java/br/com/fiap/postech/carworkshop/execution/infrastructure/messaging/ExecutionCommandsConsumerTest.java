package br.com.fiap.postech.carworkshop.execution.infrastructure.messaging;

import br.com.fiap.postech.carworkshop.execution.adapter.gateway.SagaEnvelopeCodec;
import br.com.fiap.postech.carworkshop.execution.adapter.gateway.TestCodec;
import br.com.fiap.postech.carworkshop.execution.domain.message.SagaMessageType;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

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
    private ExecutionCommandsConsumer consumer;

    @BeforeEach
    void setUp() {
        sqs = mock(SqsClient.class);
        handler = mock(ExecutionCommandHandler.class);
        SagaEnvelopeCodec codec = TestCodec.codec();
        consumer = new ExecutionCommandsConsumer();
        consumer.sqs = sqs;
        consumer.codec = codec;
        consumer.handler = handler;
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

    // O sagaId so alimenta o log: um valor malformado ou ausente nao pode derrubar o processamento.
    @Test
    void processesACommandWhoseSagaIdentifierIsMalformedOrAbsent() {
        when(handler.apply(any(JsonNode.class), anyString())).thenReturn(ExecutionWriteResult.APPLIED);

        consumer.consume(message("{\"type\":\"CancelDiagnosis\",\"sagaId\":\"nao-e-uuid\","
                + "\"workOrderId\":42,\"payload\":{}}"));
        consumer.consume(message("{\"type\":\"CancelDiagnosis\",\"workOrderId\":42,\"payload\":{}}"));

        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    private static Message message(String body) {
        return Message.builder().body(body).receiptHandle(RECEIPT).build();
    }

    private static String body(String type) {
        return "{\"messageId\":\"0b8f1c2e-0000-4000-8000-000000000002\",\"type\":\"" + type + "\","
                + "\"sagaId\":\"6f1d0f3a-0000-4000-8000-000000000001\",\"workOrderId\":42,"
                + "\"correlationId\":\"EVIDENCIA-B6-001\",\"payload\":{}}";
    }
}
