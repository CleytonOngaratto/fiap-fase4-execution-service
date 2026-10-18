resource "aws_sqs_queue" "commands_dlq" {
  name                        = local.commands_dlq_name
  fifo_queue                  = true
  content_based_deduplication = false
  message_retention_seconds   = local.dlq_retention_seconds

  tags = { Name = local.commands_dlq_name }
}

resource "aws_sqs_queue" "commands" {
  name                        = local.commands_queue_name
  fifo_queue                  = true
  content_based_deduplication = false
  visibility_timeout_seconds  = local.visibility_timeout_seconds
  receive_wait_time_seconds   = local.receive_wait_time_seconds

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.commands_dlq.arn
    maxReceiveCount     = local.max_receive_count
  })

  tags = { Name = local.commands_queue_name }
}
