CREATE TABLE IF NOT EXISTS project_document_content (
  document_id UUID PRIMARY KEY REFERENCES project_document(id) ON DELETE CASCADE,
  content BYTEA NOT NULL
);
