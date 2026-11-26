import type { ClientBase, Pool, PoolClient } from 'pg'
import type { ShoppingItem, ShoppingItemUpsert } from '../schemas/shopping.js'

type Db = Pool | ClientBase

/**
 * Every write takes this transaction-scoped advisory lock before drawing a new `version`
 * from change_seq. Writes are therefore serialized, so versions become visible in commit
 * order and a client reading `version > cursor` can never skip a change that committed late.
 * Write volume is tiny (one person, two devices), so the serialization costs nothing.
 */
export const CHANGE_LOCK_KEY = 4_242_001

interface Row {
  id: string
  name: string
  store: string | null
  modified_at: Date
  deleted: boolean
  version: number
}

const COLUMNS = 'id, name, store, modified_at, deleted, version'

const toItem = (r: Row): ShoppingItem => ({
  id: r.id,
  name: r.name,
  ...(r.store != null ? { store: r.store } : {}),
  modifiedAt: r.modified_at.toISOString(),
  deleted: r.deleted,
  version: r.version,
})

/** Items changed after `since` (including tombstones), oldest change first. */
export async function listChangesSince(db: Db, since: number, limit: number) {
  const { rows } = await db.query<Row>(
    `SELECT ${COLUMNS} FROM shopping_items WHERE version > $1 ORDER BY version LIMIT $2`,
    [since, limit + 1],
  )
  const hasMore = rows.length > limit
  const items = rows.slice(0, limit).map(toItem)
  const cursor = items.length ? items[items.length - 1]!.version : since
  return { items, cursor, hasMore }
}

/**
 * Inserts or updates an item using last-write-wins on `modifiedAt`. Must run inside a
 * transaction (the advisory lock is transaction-scoped). An edit that is older than (or the
 * same age as) what the server holds is ignored, and the server's current item is returned.
 */
export async function upsertItem(
  client: PoolClient,
  id: string,
  input: ShoppingItemUpsert,
): Promise<{ applied: boolean; item: ShoppingItem }> {
  await client.query('SELECT pg_advisory_xact_lock($1)', [CHANGE_LOCK_KEY])

  const written = await client.query<Row>(
    `INSERT INTO shopping_items (id, name, store, modified_at, deleted)
     VALUES ($1, $2, $3, $4, $5)
     ON CONFLICT (id) DO UPDATE
        SET name = EXCLUDED.name,
            store = EXCLUDED.store,
            modified_at = EXCLUDED.modified_at,
            deleted = EXCLUDED.deleted,
            version = nextval('change_seq')
      WHERE shopping_items.modified_at < EXCLUDED.modified_at
     RETURNING ${COLUMNS}`,
    [id, input.name, input.store ?? null, input.modifiedAt, input.deleted ?? false],
  )
  if (written.rows[0]) return { applied: true, item: toItem(written.rows[0]) }

  const current = await client.query<Row>(`SELECT ${COLUMNS} FROM shopping_items WHERE id = $1`, [id])
  return { applied: false, item: toItem(current.rows[0]!) }
}
