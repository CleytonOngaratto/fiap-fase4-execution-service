output "target_group_arn" {
  description = "Target group dos pods, ligado a eles pelo TargetGroupBinding."
  value       = aws_lb_target_group.service.arn
}

output "listener_rule_path" {
  description = "Caminho que a regra do ALB interno encaminha para este serviço."
  value       = "${local.path_prefix}/*"
}
