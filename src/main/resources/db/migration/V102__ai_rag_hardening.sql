-- E1 RAG hardening: knowledge chunks + optional pgvector column + permissions

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pgvector extension unavailable: %', SQLERRM;
END $$;

CREATE TABLE IF NOT EXISTS ai_knowledge_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    document_id UUID NOT NULL REFERENCES ai_knowledge_documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64),
    token_estimate INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ai_knowledge_chunks_doc_idx UNIQUE (document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunks_org ON ai_knowledge_chunks(organization_id);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunks_doc ON ai_knowledge_chunks(document_id);

ALTER TABLE ai_embeddings ADD COLUMN IF NOT EXISTS document_id UUID;
ALTER TABLE ai_embeddings ADD COLUMN IF NOT EXISTS chunk_index INT;

DO $$
BEGIN
    ALTER TABLE ai_embeddings ADD COLUMN IF NOT EXISTS embedding vector(1536);
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Could not add embedding vector column: %', SQLERRM;
END $$;

INSERT INTO permissions (id, code, name, module, description) VALUES
    (gen_random_uuid(), 'AI_KNOWLEDGE', 'AI Knowledge', 'AI', 'Manage AI knowledge base'),
    (gen_random_uuid(), 'AI_AUTOMATION', 'AI Automation', 'AI', 'Manage AI automations')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ORGANIZATION_ADMIN'
  AND p.code IN ('AI_KNOWLEDGE', 'AI_AUTOMATION')
ON CONFLICT (role_id, permission_id) DO NOTHING;
