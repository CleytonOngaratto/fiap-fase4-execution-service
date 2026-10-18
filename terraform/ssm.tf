resource "aws_ssm_parameter" "target_group_arn" {
  name  = "/fase4/${local.service}/alb/target-group-arn"
  type  = "String"
  value = aws_lb_target_group.service.arn
}

resource "aws_ssm_parameter" "commands_queue_url" {
  name  = "/fase4/${local.service}/sqs/commands-queue-url"
  type  = "String"
  value = aws_sqs_queue.commands.url
}
