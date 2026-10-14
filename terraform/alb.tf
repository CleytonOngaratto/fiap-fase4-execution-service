resource "aws_lb_target_group" "service" {
  name        = "${var.project}-${local.service}"
  target_type = "ip"
  protocol    = "HTTP"
  port        = 8080
  vpc_id      = local.vpc_id

  deregistration_delay = 30

  health_check {
    path                = "${local.path_prefix}/q/health/ready"
    matcher             = "200"
    interval            = 10
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = { Name = "${var.project}-${local.service}" }
}

resource "aws_lb_listener_rule" "service" {
  listener_arn = local.listener_arn
  priority     = local.rule_priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.service.arn
  }

  condition {
    path_pattern {
      values = ["${local.path_prefix}/*"]
    }
  }

  tags = { Name = "${var.project}-${local.service}" }
}
