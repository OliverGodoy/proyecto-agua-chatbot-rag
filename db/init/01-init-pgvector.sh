#!/bin/bash
# HU-30: Vector database (Postgres + pgvector)
# Se ejecuta automáticamente solo la primera vez que se crea el volumen.
# La dimensión del embedding se toma de EMBEDDING_DIMENSION (.env) y debe
# coincidir con el modelo configurado en EMBEDDING_PROVIDER.
set -euo pipefail

DIM="${EMBEDDING_DIMENSION:-1536}"

psql -v ON_ERROR_STOP=1 -v dim="$DIM" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
-- 1. Habilitar la extensión vector
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Tabla de fragmentos del contenido de la oficina de agua
--    hash_contenido = SHA-256 (hex) del texto del fragmento, calculado por la ingesta.
--    UNIQUE (documento, hash_contenido) permite re-ejecutar la ingesta con
--    INSERT ... ON CONFLICT sin duplicar fragmentos (HU-33).
CREATE TABLE IF NOT EXISTS fragmentos (
    id              BIGSERIAL     PRIMARY KEY,
    documento       VARCHAR(255)  NOT NULL,
    seccion         VARCHAR(255),
    contenido       TEXT          NOT NULL,
    hash_contenido  CHAR(64)      NOT NULL,
    embedding       VECTOR(:dim),
    metadata        JSONB         NOT NULL DEFAULT '{}'::jsonb,
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_fragmentos_documento_hash UNIQUE (documento, hash_contenido),
    CONSTRAINT ck_fragmentos_hash_sha256 CHECK (hash_contenido ~ '^[0-9a-f]{64}$')
);

-- 3. Índice para búsqueda por similitud (distancia coseno)
CREATE INDEX IF NOT EXISTS idx_fragmentos_embedding
    ON fragmentos USING hnsw (embedding vector_cosine_ops);

-- 4. Índice para filtrar por documento de origen
CREATE INDEX IF NOT EXISTS idx_fragmentos_documento
    ON fragmentos (documento);
EOSQL
