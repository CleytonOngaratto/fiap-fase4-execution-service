# O provider avisa que hash_key/range_key estão deprecados em favor de key_schema, mas key_schema só existe
# dentro de global_secondary_index nesta versão (medido em `terraform providers schema -json`): no nível da
# tabela não há alternativa, e o aviso não falha o validate.
resource "aws_dynamodb_table" "execution" {
  name         = local.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  range_key    = "sk"

  attribute {
    name = "pk"
    type = "S"
  }

  attribute {
    name = "sk"
    type = "S"
  }

  attribute {
    name = "gsi1pk"
    type = "S"
  }

  attribute {
    name = "gsi1sk"
    type = "S"
  }

  attribute {
    name = "gsi2pk"
    type = "S"
  }

  attribute {
    name = "gsi2sk"
    type = "S"
  }

  # Projeção ALL porque a consulta da fila devolve o item de execução inteiro ao mecânico; com KEYS_ONLY cada
  # linha da fila custaria um GetItem a mais.
  global_secondary_index {
    name            = "gsi-branch-status"
    hash_key        = "gsi1pk"
    range_key       = "gsi1sk"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "gsi-outbox-pending"
    hash_key        = "gsi2pk"
    range_key       = "gsi2sk"
    projection_type = "ALL"
  }

  # Só as linhas da inbox carregam expiresAt; estado, histórico e outbox não expiram.
  ttl {
    attribute_name = "expiresAt"
    enabled        = true
  }

  tags = { Name = local.table_name }
}
