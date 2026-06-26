PRAGMA foreign_keys = ON;

CREATE TABLE system_setting (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
);
CREATE INDEX idx_system_setting_updated_id ON system_setting (updated_at, key);

CREATE TABLE account (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL DEFAULT '',
  password_hash TEXT NOT NULL,
  password_algorithm TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER
);
CREATE INDEX idx_account_updated_id ON account (updated_at, id);
CREATE INDEX idx_account_deleted_at ON account (deleted_at);

CREATE TABLE account_setting (
  account_id TEXT NOT NULL,
  key TEXT NOT NULL,
  value TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  PRIMARY KEY (account_id, key),
  FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE
);
CREATE INDEX idx_account_setting_updated_id ON account_setting (updated_at, account_id, key);
CREATE INDEX idx_account_setting_deleted_at ON account_setting (deleted_at);

CREATE TABLE session (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL,
  refresh_token_hash TEXT NOT NULL UNIQUE,
  user_agent TEXT NOT NULL DEFAULT '',
  client_ip TEXT NOT NULL DEFAULT '',
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE
);
CREATE INDEX idx_session_account_id ON session (account_id);
CREATE INDEX idx_session_refresh_hash ON session (refresh_token_hash);
CREATE INDEX idx_session_expires_at ON session (expires_at);
CREATE INDEX idx_session_deleted_at ON session (deleted_at);

CREATE TABLE memo (
  id TEXT PRIMARY KEY,
  creator_id TEXT,
  content TEXT NOT NULL DEFAULT '',
  entry_date TEXT NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  pinned_at INTEGER,
  archived_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  FOREIGN KEY (creator_id) REFERENCES account(id) ON DELETE SET NULL
);
CREATE INDEX idx_memo_updated_id ON memo (updated_at, id);
CREATE INDEX idx_memo_entry_date ON memo (entry_date);
CREATE INDEX idx_memo_deleted_at ON memo (deleted_at);
CREATE INDEX idx_memo_archived_at ON memo (archived_at);
CREATE INDEX idx_memo_pinned_at ON memo (pinned_at);

CREATE TABLE attachments (
  id TEXT PRIMARY KEY,
  uid TEXT NOT NULL UNIQUE,
  creator_id TEXT,
  memo_id TEXT,
  storage_type TEXT NOT NULL DEFAULT 'local',
  storage_ref TEXT NOT NULL,
  filename TEXT NOT NULL,
  content_type TEXT NOT NULL DEFAULT 'application/octet-stream',
  size INTEGER NOT NULL DEFAULT 0,
  sha256 TEXT,
  width INTEGER,
  height INTEGER,
  status TEXT NOT NULL DEFAULT 'stored',
  mutation_id TEXT,
  idempotency_key TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  FOREIGN KEY (creator_id) REFERENCES account(id) ON DELETE SET NULL,
  FOREIGN KEY (memo_id) REFERENCES memo(id) ON DELETE SET NULL
);
CREATE INDEX idx_attachments_updated_id ON attachments (updated_at, id);
CREATE INDEX idx_attachments_deleted_at ON attachments (deleted_at);
CREATE INDEX idx_attachments_memo_id ON attachments (memo_id);
CREATE UNIQUE INDEX idx_attachments_creator_mutation ON attachments (creator_id, mutation_id) WHERE mutation_id IS NOT NULL;
CREATE UNIQUE INDEX idx_attachments_creator_idempotency ON attachments (creator_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE summaries (
  id TEXT PRIMARY KEY,
  creator_id TEXT,
  scope TEXT NOT NULL,
  period_type TEXT,
  start_date TEXT,
  end_date TEXT,
  title TEXT NOT NULL DEFAULT '',
  content TEXT NOT NULL DEFAULT '',
  source_memo_ids TEXT NOT NULL DEFAULT '[]',
  style TEXT NOT NULL DEFAULT 'brief',
  trigger TEXT NOT NULL DEFAULT 'manual',
  version INTEGER NOT NULL DEFAULT 1,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  FOREIGN KEY (creator_id) REFERENCES account(id) ON DELETE SET NULL
);
CREATE INDEX idx_summaries_updated_id ON summaries (updated_at, id);
CREATE INDEX idx_summaries_deleted_at ON summaries (deleted_at);
CREATE INDEX idx_summaries_scope_period ON summaries (scope, period_type, start_date, end_date);

CREATE TABLE ask_conversations (
  id TEXT PRIMARY KEY,
  creator_id TEXT,
  title TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'active',
  context_scope TEXT NOT NULL DEFAULT 'recent_30_days',
  head_message_id TEXT,
  pinned_at INTEGER,
  archived_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  FOREIGN KEY (creator_id) REFERENCES account(id) ON DELETE SET NULL
);
CREATE INDEX idx_ask_conversations_updated_id ON ask_conversations (updated_at, id);
CREATE INDEX idx_ask_conversations_deleted_at ON ask_conversations (deleted_at);
CREATE INDEX idx_ask_conversations_archived_at ON ask_conversations (archived_at);
CREATE INDEX idx_ask_conversations_pinned_at ON ask_conversations (pinned_at);

CREATE TABLE ask_messages (
  id TEXT PRIMARY KEY,
  conversation_id TEXT NOT NULL,
  role TEXT NOT NULL,
  content TEXT NOT NULL DEFAULT '',
  parent_id TEXT,
  fork_of_id TEXT,
  status TEXT NOT NULL DEFAULT 'complete',
  source_refs TEXT NOT NULL DEFAULT '[]',
  model TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  FOREIGN KEY (conversation_id) REFERENCES ask_conversations(id) ON DELETE CASCADE,
  FOREIGN KEY (parent_id) REFERENCES ask_messages(id) ON DELETE SET NULL,
  FOREIGN KEY (fork_of_id) REFERENCES ask_messages(id) ON DELETE SET NULL
);
CREATE INDEX idx_ask_messages_updated_id ON ask_messages (updated_at, id);
CREATE INDEX idx_ask_messages_deleted_at ON ask_messages (deleted_at);
CREATE INDEX idx_ask_messages_conversation_id ON ask_messages (conversation_id);
CREATE INDEX idx_ask_messages_parent_id ON ask_messages (parent_id);

CREATE TABLE memo_ai (
  memo_id TEXT PRIMARY KEY,
  summary TEXT,
  sentiment TEXT,
  provider TEXT NOT NULL DEFAULT '',
  model TEXT NOT NULL DEFAULT '',
  profile_id TEXT NOT NULL DEFAULT '',
  prompt_version TEXT NOT NULL DEFAULT '',
  source_memo_ids TEXT NOT NULL DEFAULT '[]',
  status TEXT NOT NULL DEFAULT 'pending',
  error_code TEXT,
  started_at INTEGER,
  finished_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  FOREIGN KEY (memo_id) REFERENCES memo(id) ON DELETE CASCADE
);
CREATE INDEX idx_memo_ai_updated_id ON memo_ai (updated_at, memo_id);
CREATE INDEX idx_memo_ai_deleted_at ON memo_ai (deleted_at);

CREATE VIRTUAL TABLE memo_fts USING fts5(
  memo_id UNINDEXED,
  content,
  summary,
  tokenize = 'unicode61'
);

CREATE TRIGGER memo_fts_memo_insert AFTER INSERT ON memo BEGIN
  INSERT INTO memo_fts (memo_id, content, summary)
  SELECT NEW.id, NEW.content, COALESCE((
    SELECT summary FROM memo_ai WHERE memo_id = NEW.id AND deleted_at IS NULL
  ), '')
  WHERE NEW.deleted_at IS NULL;
END;

CREATE TRIGGER memo_fts_memo_update AFTER UPDATE ON memo BEGIN
  DELETE FROM memo_fts WHERE memo_id = OLD.id;
  INSERT INTO memo_fts (memo_id, content, summary)
  SELECT NEW.id, NEW.content, COALESCE((
    SELECT summary FROM memo_ai WHERE memo_id = NEW.id AND deleted_at IS NULL
  ), '')
  WHERE NEW.deleted_at IS NULL;
END;

CREATE TRIGGER memo_fts_memo_delete AFTER DELETE ON memo BEGIN
  DELETE FROM memo_fts WHERE memo_id = OLD.id;
END;

CREATE TRIGGER memo_fts_ai_insert AFTER INSERT ON memo_ai BEGIN
  DELETE FROM memo_fts WHERE memo_id = NEW.memo_id;
  INSERT INTO memo_fts (memo_id, content, summary)
  SELECT memo.id, memo.content, COALESCE(NEW.summary, '')
  FROM memo
  WHERE memo.id = NEW.memo_id AND memo.deleted_at IS NULL AND NEW.deleted_at IS NULL;
END;

CREATE TRIGGER memo_fts_ai_update AFTER UPDATE ON memo_ai BEGIN
  DELETE FROM memo_fts WHERE memo_id = OLD.memo_id;
  INSERT INTO memo_fts (memo_id, content, summary)
  SELECT memo.id, memo.content, COALESCE(NEW.summary, '')
  FROM memo
  WHERE memo.id = NEW.memo_id AND memo.deleted_at IS NULL AND NEW.deleted_at IS NULL;
END;

CREATE TRIGGER memo_fts_ai_delete AFTER DELETE ON memo_ai BEGIN
  DELETE FROM memo_fts WHERE memo_id = OLD.memo_id;
  INSERT INTO memo_fts (memo_id, content, summary)
  SELECT memo.id, memo.content, ''
  FROM memo
  WHERE memo.id = OLD.memo_id AND memo.deleted_at IS NULL;
END;

CREATE TABLE ai_profile (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL,
  name TEXT NOT NULL,
  provider TEXT NOT NULL,
  base_url TEXT NOT NULL DEFAULT '',
  model TEXT NOT NULL DEFAULT '',
  temperature REAL NOT NULL DEFAULT 0.3,
  max_tokens INTEGER NOT NULL DEFAULT 1000,
  enabled INTEGER NOT NULL DEFAULT 0,
  active INTEGER NOT NULL DEFAULT 0,
  api_key_envelope TEXT,
  key_unavailable INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted_at INTEGER,
  FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_profile_account_id ON ai_profile (account_id);
CREATE INDEX idx_ai_profile_updated_id ON ai_profile (updated_at, id);
CREATE INDEX idx_ai_profile_deleted_at ON ai_profile (deleted_at);

CREATE TABLE runtime_kv (
  namespace TEXT NOT NULL,
  key TEXT NOT NULL,
  value TEXT NOT NULL,
  expires_at INTEGER,
  PRIMARY KEY (namespace, key)
);
CREATE INDEX idx_runtime_kv_expires_at ON runtime_kv (expires_at);

CREATE TABLE sync_mutation (
  account_id TEXT NOT NULL,
  mutation_id TEXT NOT NULL,
  resource_type TEXT NOT NULL,
  resource_id TEXT NOT NULL DEFAULT '',
  result TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (account_id, mutation_id),
  FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE
);
CREATE INDEX idx_sync_mutation_created_at ON sync_mutation (created_at);
