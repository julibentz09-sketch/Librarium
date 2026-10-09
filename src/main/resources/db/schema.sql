-- Esquema de Librarium (DER)
--   AUTOR (1) ── (N) OBRA
--   AUTOR (1) ── (N) MULTIMEDIA
--   AUTOR (1) ── (1) MARCADOR   imagen que la cámara reconoce

CREATE TABLE IF NOT EXISTS autor (
  id               TEXT PRIMARY KEY,
  nombre           TEXT NOT NULL,
  fecha_nacimiento TEXT,
  lugar_nacimiento TEXT,
  fallecimiento    TEXT,
  destacado        TEXT,
  biografia        TEXT NOT NULL,
  foto             TEXT NOT NULL,
  orden            INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS obra (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  autor_id    TEXT NOT NULL REFERENCES autor(id) ON DELETE CASCADE,
  titulo      TEXT NOT NULL,
  anio        INTEGER,
  genero      TEXT,
  descripcion TEXT,
  orden       INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS multimedia (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  autor_id    TEXT NOT NULL REFERENCES autor(id) ON DELETE CASCADE,
  tipo        TEXT NOT NULL CHECK (tipo IN ('imagen', 'audio', 'video')),
  url         TEXT,
  descripcion TEXT
);

CREATE TABLE IF NOT EXISTS marcador (
  id       TEXT PRIMARY KEY,
  autor_id TEXT NOT NULL UNIQUE REFERENCES autor(id) ON DELETE CASCADE,
  imagen   TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_obra_autor ON obra(autor_id);
CREATE INDEX IF NOT EXISTS idx_multimedia_autor ON multimedia(autor_id);
