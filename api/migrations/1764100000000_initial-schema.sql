-- Up Migration

-- Devices (phone, wall tablet) that may call the API. Only a SHA-256 hash of each key is stored.
CREATE TABLE devices (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name         text NOT NULL,
  key_hash     text NOT NULL UNIQUE,
  created_at   timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz,
  revoked_at   timestamptz
);
CREATE INDEX devices_name_idx ON devices (name);

-- Global change counter shared by all synced tables. Clients pull "everything with
-- version > my cursor"; see shopping-items.repository.ts for why this is safe.
CREATE SEQUENCE change_seq;

CREATE TABLE shopping_items (
  id          uuid PRIMARY KEY,               -- generated on the device, so offline creates work
  name        text NOT NULL,
  store       text,
  modified_at timestamptz NOT NULL,           -- device edit time, used for last-write-wins
  deleted     boolean NOT NULL DEFAULT false, -- tombstone so deletes reach the other device
  version     bigint NOT NULL DEFAULT nextval('change_seq'),
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX shopping_items_version_idx ON shopping_items (version);

-- Down Migration
DROP TABLE shopping_items;
DROP SEQUENCE change_seq;
DROP TABLE devices;
