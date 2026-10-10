#!/bin/sh
# HU-38: crea la tabla de registro de prompt-response en DynamoDB Local.
# Es idempotente: si la tabla ya existe, no hace nada.
set -eu

ENDPOINT="http://dynamodb-local:8000"
TABLA="${DYNAMODB_TABLE:-chatbot-interacciones}"

echo "Esperando a DynamoDB Local..."
intentos=0
until aws dynamodb list-tables --endpoint-url "$ENDPOINT" >/dev/null 2>&1; do
  intentos=$((intentos + 1))
  if [ "$intentos" -ge 30 ]; then
    echo "DynamoDB Local no respondió a tiempo"
    exit 1
  fi
  sleep 2
done

if aws dynamodb describe-table --table-name "$TABLA" --endpoint-url "$ENDPOINT" >/dev/null 2>&1; then
  echo "La tabla $TABLA ya existe"
else
  aws dynamodb create-table \
    --table-name "$TABLA" \
    --attribute-definitions AttributeName=id,AttributeType=S \
    --key-schema AttributeName=id,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --endpoint-url "$ENDPOINT" >/dev/null
  echo "Tabla $TABLA creada"
fi
