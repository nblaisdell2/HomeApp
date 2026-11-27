import type { ClientBase, Pool, PoolClient } from 'pg'
import type { Store, StoreUpsert } from '../schemas/shopping.js'
import { CHANGE_LOCK_KEY } from './shopping-items.repository.js'

type Db = Pool | ClientBase

interface Row {
  id: string
  name: string
  sort_order: number
  modified_at: Date
  deleted: boolean
  version: number
}

const COLUMNS = 'id, name, sort_order, modified_at, deleted, version'

const toStore = (r: Row): Store => ({
  id: r.id,
  name: r.name,
  sortOrder: r.sort_order,
  modifiedAt: r.modified_at.toISOString(),
  deleted: r.deleted,
  version: r.version,
})

/** Stores changed after `since` (including tombstones), oldest change first. */
export async function listStoreChangesSince(db: Db, since: number, limit: number) {
  const { rows } = await db.query<Row>(
    `SELECT ${COLUMNS} FROM stores WHERE version > $1 ORDER BY version LIMIT $2`,
    [since, limit + 1],
  )
  const hasMore = rows.length > limit
  const stores = rows.slice(0, limit).map(toStore)
  const cursor = stores.length ? stores[stores.length - 1]!.version : since
  return { stores, cursor, hasMore }
}

/**
 * Inserts or updates a store with last-write-wins on `modifiedAt`, under the same rules as
 * upsertItem. Must run inside a transaction.
 */
export async function upsertStore(
  client: PoolClient,
  id: string,
  input: StoreUpsert,
): Promise<{ applied: boolean; store: Store }> {
  await client.query('SELECT pg_advisory_xact_lock($1)', [CHANGE_LOCK_KEY])

  const written = await client.query<Row>(
    `INSERT INTO stores (id, name, sort_order, modified_at, deleted)
     VALUES ($1, $2, $3, $4, $5)
     ON CONFLICT (id) DO UPDATE
        SET name = EXCLUDED.name,
            sort_order = EXCLUDED.sort_order,
            modified_at = EXCLUDED.modified_at,
            deleted = EXCLUDED.deleted,
            version = nextval('change_seq')
      WHERE stores.modified_at < EXCLUDED.modified_at
     RETURNING ${COLUMNS}`,
    [id, input.name, input.sortOrder, input.modifiedAt, input.deleted ?? false],
  )
  if (written.rows[0]) return { applied: true, store: toStore(written.rows[0]) }

  const current = await client.query<Row>(`SELECT ${COLUMNS} FROM stores WHERE id = $1`, [id])
  return { applied: false, store: toStore(current.rows[0]!) }
}
