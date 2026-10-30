package br.com.fiap.postech.carworkshop.execution.usecase.port.in;

import br.com.fiap.postech.carworkshop.execution.domain.message.CancelDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartDiagnosisCommand;
import br.com.fiap.postech.carworkshop.execution.domain.message.StartRepairCommand;
import br.com.fiap.postech.carworkshop.execution.usecase.port.out.ExecutionWriteResult;

public interface ExecutionCommandUseCase {

    ExecutionWriteResult queueDiagnosis(StartDiagnosisCommand command);

    ExecutionWriteResult cancelDiagnosis(CancelDiagnosisCommand command);

    ExecutionWriteResult queueRepair(StartRepairCommand command);
}
