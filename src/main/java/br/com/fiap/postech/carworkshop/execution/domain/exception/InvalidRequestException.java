package br.com.fiap.postech.carworkshop.execution.domain.exception;

public class InvalidRequestException extends BusinessException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
