-- Serox storage schema version 1. The migration runner advances PRAGMA user_version.

CREATE TABLE history (
    id TEXT PRIMARY KEY NOT NULL,
    url TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    visited_at INTEGER NOT NULL
);

CREATE INDEX history_visited_at_idx ON history (visited_at DESC);
CREATE INDEX history_url_visited_at_idx ON history (url, visited_at DESC);

CREATE TABLE bookmarks (
    id TEXT PRIMARY KEY NOT NULL,
    url TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    folder_id TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX bookmarks_created_at_idx ON bookmarks (created_at DESC);

CREATE TABLE sessions (
    id TEXT PRIMARY KEY NOT NULL,
    profile_id TEXT NOT NULL DEFAULT 'default',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE tabs (
    id TEXT PRIMARY KEY NOT NULL,
    session_id TEXT NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    active INTEGER NOT NULL DEFAULT 0 CHECK (active IN (0, 1)),
    UNIQUE (session_id, position)
);

CREATE INDEX tabs_session_position_idx ON tabs (session_id, position);

CREATE TABLE settings (
    key TEXT PRIMARY KEY NOT NULL,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE conversations (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE messages (
    id TEXT PRIMARY KEY NOT NULL,
    conversation_id TEXT NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX messages_conversation_created_idx ON messages (conversation_id, created_at);

CREATE TABLE search_cache (
    cache_key TEXT PRIMARY KEY NOT NULL,
    response TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL CHECK (expires_at >= created_at)
);

CREATE INDEX search_cache_expires_at_idx ON search_cache (expires_at);

CREATE TABLE recent_searches (
    query TEXT PRIMARY KEY NOT NULL,
    used_at INTEGER NOT NULL
);

CREATE INDEX recent_searches_used_at_idx ON recent_searches (used_at DESC);
