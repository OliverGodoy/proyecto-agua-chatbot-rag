-- HU-30: Vector database (Postgres + pgvector)
-- Se ejecuta automáticamente solo la primera vez que se crea el volumen.

-- 1. Habilitar la extensión vector
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Tabla de fragmentos del contenido de la oficina de agua
--    La dimensión del embedding (1536) debe coincidir con el modelo
--    definido en EMBEDDING_PROVIDER.
CREATE TABLE IF NOT EXISTS fragmentos (
    id          BIGSERIAL     PRIMARY KEY,
    documento   VARCHAR(255)  NOT NULL,
    seccion     VARCHAR(255),
    contenido   TEXT          NOT NULL,
    embedding   VECTOR(1536),
    metadata    JSONB         NOT NULL DEFAULT '{}'::jsonb,
    creado_en   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- 3. Índice para búsqueda por similitud (distancia coseno)
CREATE INDEX IF NOT EXISTS idx_fragmentos_embedding
    ON fragmentos USING hnsw (embedding vector_cosine_ops);

-- 4. Índice para filtrar por documento de origen
CREATE INDEX IF NOT EXISTS idx_fragmentos_documento
    ON fragmentos (documento);
