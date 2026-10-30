#!/bin/sh
set -eu

: "${TABLE_NAME:=execution}"
: "${ENDPOINT:=http://execution_db:8000}"

if aws dynamodb describe-table --table-name "$TABLE_NAME" --endpoint-url "$ENDPOINT" >/dev/null 2>&1; then
  echo "tabela $TABLE_NAME ja existe"
  exit 0
fi

aws dynamodb create-table \
  --table-name "$TABLE_NAME" \
  --endpoint-url "$ENDPOINT" \
  --billing-mode PAY_PER_REQUEST \
  --attribute-definitions \
    AttributeName=pk,AttributeType=S \
    AttributeName=sk,AttributeType=S \
    AttributeName=gsi1pk,AttributeType=S \
    AttributeName=gsi1sk,AttributeType=S \
    AttributeName=gsi2pk,AttributeType=S \
    AttributeName=gsi2sk,AttributeType=S \
  --key-schema \
    AttributeName=pk,KeyType=HASH \
    AttributeName=sk,KeyType=RANGE \
  --global-secondary-indexes \
    'IndexName=gsi-branch-status,KeySchema=[{AttributeName=gsi1pk,KeyType=HASH},{AttributeName=gsi1sk,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
    'IndexName=gsi-outbox-pending,KeySchema=[{AttributeName=gsi2pk,KeyType=HASH},{AttributeName=gsi2sk,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
  >/dev/null

echo "tabela $TABLE_NAME criada"
