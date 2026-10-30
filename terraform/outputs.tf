output "target_group_arn" {
  description = "Target group dos pods, ligado a eles pelo TargetGroupBinding."
  value       = aws_lb_target_group.service.arn
}

output "listener_rule_path" {
  description = "Caminho que a regra do ALB interno encaminha para este serviço."
  value       = "${local.path_prefix}/*"
}

output "commands_queue_url" {
  description = "Fila de comandos que o orquestrador do OS Service escreve e este serviço consome."
  value       = aws_sqs_queue.commands.url
}

output "commands_dlq_url" {
  description = "DLQ da fila de comandos; mensagem aqui significa comando que falhou o bastante para sair da fila."
  value       = aws_sqs_queue.commands_dlq.url
}

output "dynamodb_table_name" {
  description = "Tabela única do Execution: estado, histórico de etapas, inbox e outbox."
  value       = aws_dynamodb_table.execution.name
}

output "dynamodb_table_arn" {
  description = "ARN da tabela, para conferência na auditoria da conta."
  value       = aws_dynamodb_table.execution.arn
}
