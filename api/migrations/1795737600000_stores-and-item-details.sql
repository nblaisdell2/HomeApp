-- Up Migration

-- Stores become a managed, synced list (HOME-66) instead of free text on each item.
CREATE TABLE stores (
  id          uuid PRIMARY KEY,               -- generated on the device, so offline creates work
  name        text NOT NULL,
  sort_order  integer NOT NULL DEFAULT 0,
  modified_at timestamptz NOT NULL,           -- device edit time, used for last-write-wins
  deleted     boolean NOT NULL DEFAULT false, -- tombstone so deletes reach the other device
  version     bigint NOT NULL DEFAULT nextval('change_seq'),
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX stores_version_idx ON stores (version);

-- Existing free-text stores get a deterministic id: md5('home-store:' || lower(trim(name))).
-- The Android Room migration derives the same id from its local copy, so both sides agree
-- without a round trip (see LegacyStoreIds.kt). The display name is the spelling on the most
-- recently edited item.
INSERT INTO stores (id, name, sort_order, modified_at)
SELECT md5('home-store:' || lower(btrim(store)))::uuid,
       (array_agg(btrim(store) ORDER BY modified_at DESC, btrim(store)))[1],
       0,
       now()
  FROM shopping_items
 WHERE store IS NOT NULL AND btrim(store) <> ''
 GROUP BY lower(btrim(store));

-- No foreign key on store_id: with offline last-write-wins sync an item can briefly reference
-- a store the server hasn't seen yet, and that must never block the item's upload. Unknown or
-- deleted stores show as "Anywhere".
ALTER TABLE shopping_items
  ADD COLUMN store_id     uuid,
  ADD COLUMN aisle        text,
  ADD COLUMN note         text,
  ADD COLUMN purchased_at timestamptz;     -- set when checked off in shopping mode (history)

UPDATE shopping_items
   SET store_id = md5('home-store:' || lower(btrim(store)))::uuid
 WHERE store IS NOT NULL AND btrim(store) <> '';

ALTER TABLE shopping_items DROP COLUMN store;

-- Down Migration
ALTER TABLE shopping_items ADD COLUMN store text;
UPDATE shopping_items i SET store = s.name FROM stores s WHERE s.id = i.store_id;
ALTER TABLE shopping_items
  DROP COLUMN purchased_at,
  DROP COLUMN note,
  DROP COLUMN aisle,
  DROP COLUMN store_id;
DROP TABLE stores;
