locals {
  service       = "execution"
  path_prefix   = "/execution/v1"
  rule_priority = 300

  tags = {
    Project   = var.project
    ManagedBy = "terraform"
    Repo      = "fiap-fase4-execution-service"
  }

  vpc_id       = nonsensitive(data.aws_ssm_parameter.vpc_id.value)
  listener_arn = nonsensitive(data.aws_ssm_parameter.alb_listener_arn.value)
}
