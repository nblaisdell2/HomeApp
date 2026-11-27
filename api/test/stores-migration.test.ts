import { randomUUID } from 'node:crypto'
import pg from 'pg'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { runMigrations } from '../src/lib/migrate.js'
import { testDatabaseUrl } from './helpers.js'

/**
 * The stores migration turns free-text item stores into rows in `stores` with deterministic
 * ids. The Android Room migration (LegacyStoreIds.kt) derives the same ids on the device, so
 * this pins the formula with a known value that the Android unit test also checks.
 */
const COSTCO_ID = '75f0a4c6-1f22-bad4-e2d2-a99e048314f3'

let client: pg.Client

beforeAll(async () => {
  client = new pg.Client({ connectionString: testDatabaseUrl() })
  await client.connect()
})
afterAll(async () => {
  // Leave the database fully migrated for the other test files.
  await runMigrations(testDatabaseUrl(), 'up', () => {})
  await client.end()
})

describe('stores-and-item-details migration', () => {
  it('derives legacy store ids from md5(home-store:<lower(trim(name))>)', async () => {
    const { rows } = await client.query<{ id: string }>(
      `SELECT md5('home-store:' || lower(btrim($1::text)))::uuid AS id`,
      ['  Costco '],
    )
    expect(rows[0]!.id).toBe(COSTCO_ID)
  })

  it('moves free-text stores into the stores table, merging case variants (newest spelling wins)', async () => {
    await client.query('TRUNCATE shopping_items, stores')
    await runMigrations(testDatabaseUrl(), 'down', () => {})

    const costcoA = randomUUID()
    const costcoB = randomUUID()
    const anywhere = randomUUID()
    const blank = randomUUID()
    await client.query(
      `INSERT INTO shopping_items (id, name, store, modified_at) VALUES
         ($1, 'Milk', 'Costco', now()), ($2, 'Eggs', ' costco ', now() - interval '1 day'),
         ($3, 'Bread', NULL, now()), ($4, 'Jam', '  ', now())`,
      [costcoA, costcoB, anywhere, blank],
    )

    await runMigrations(testDatabaseUrl(), 'up', () => {})

    const stores = await client.query('SELECT id, name, deleted FROM stores')
    expect(stores.rows).toEqual([{ id: COSTCO_ID, name: 'Costco', deleted: false }])

    const items = await client.query<{ id: string; store_id: string | null }>(
      'SELECT id, store_id FROM shopping_items',
    )
    const storeOf = Object.fromEntries(items.rows.map((r) => [r.id, r.store_id]))
    expect(storeOf[costcoA]).toBe(COSTCO_ID)
    expect(storeOf[costcoB]).toBe(COSTCO_ID)
    expect(storeOf[anywhere]).toBeNull()
    expect(storeOf[blank]).toBeNull()

    await client.query('TRUNCATE shopping_items, stores')
  })
})
