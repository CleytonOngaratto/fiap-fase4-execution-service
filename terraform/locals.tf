locals {
  service       = "execution"
  path_prefix   = "/execution/v1"
  rule_priority = 300

  commands_queue_name = "execution-commands.fifo"
  commands_dlq_name   = "execution-commands-dlq.fifo"

  visibility_timeout_seconds = 30
  receive_wait_time_seconds  = 20
  max_receive_count          = 3
  dlq_retention_seconds      = 1209600

  tags = {
    Project   = var.project
    ManagedBy = "terraform"
    Repo      = "fiap-fase4-execution-service"
  }

  vpc_id       = nonsensitive(data.aws_ssm_parameter.vpc_id.value)
  listener_arn = nonsensitive(data.aws_ssm_parameter.alb_listener_arn.value)
}
