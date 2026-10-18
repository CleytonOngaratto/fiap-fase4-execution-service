package br.com.fiap.postech.carworkshop.execution.usecase.port.in;

import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;

public interface DiagnosisUseCase {

    void start(StartDiagnosisCommand command);
}
