data "aws_ssm_parameter" "vpc_id" {
  name = "/fase4/vpc/id"
}

data "aws_ssm_parameter" "alb_listener_arn" {
  name = "/fase4/alb/listener-arn"
}
