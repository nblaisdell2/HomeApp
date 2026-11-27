import { randomUUID } from 'node:crypto'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import type { App } from '../src/app.js'
import { buildTestApp, issueTestKey, resetData } from './helpers.js'

let app: App
let headers: Record<string, string>

beforeAll(async () => {
  app = await buildTestApp()
})
afterAll(() => app.close())
beforeEach(async () => {
  await resetData(app)
  headers = (await issueTestKey(app)).headers
})

const at = (minute: number) => new Date(Date.UTC(2026, 10, 26, 12, minute)).toISOString()

function put(id: string, body: Record<string, unknown>) {
  return app.inject({ method: 'PUT', url: `/v1/stores/${id}`, headers, payload: body })
}

function changes(since = 0, limit?: number) {
  const qs = new URLSearchParams({ since: String(since), ...(limit ? { limit: String(limit) } : {}) })
  return app.inject({ method: 'GET', url: `/v1/stores?${qs}`, headers })
}

describe('PUT /v1/stores/:id', () => {
  it('creates a store', async () => {
    const id = randomUUID()
    const res = await put(id, { name: 'Costco', sortOrder: 2, modifiedAt: at(0) })
    expect(res.statusCode).toBe(200)
    expect(res.json()).toMatchObject({
      applied: true,
      store: { id, name: 'Costco', sortOrder: 2, deleted: false, modifiedAt: at(0) },
    })
    expect(res.json().store.version).toBeGreaterThan(0)
  })

  it('applies a newer rename and bumps the version', async () => {
    const id = randomUUID()
    const first = (await put(id, { name: 'Costco', sortOrder: 0, modifiedAt: at(0) })).json()
    const second = (await put(id, { name: 'Costco Wholesale', sortOrder: 1, modifiedAt: at(5) })).json()
    expect(second.applied).toBe(true)
    expect(second.store).toMatchObject({ name: 'Costco Wholesale', sortOrder: 1 })
    expect(second.store.version).toBeGreaterThan(first.store.version)
  })

  it('ignores an older edit (last write wins) and returns the winner', async () => {
    const id = randomUUID()
    await put(id, { name: 'Aldi', sortOrder: 0, modifiedAt: at(5) })
    const stale = (await put(id, { name: 'ALDI', sortOrder: 0, modifiedAt: at(1) })).json()
    expect(stale.applied).toBe(false)
    expect(stale.store.name).toBe('Aldi')
  })

  it('treats a retry with the same modifiedAt as a no-op', async () => {
    const id = randomUUID()
    const first = (await put(id, { name: 'Aldi', sortOrder: 0, modifiedAt: at(0) })).json()
    const retry = (await put(id, { name: 'Aldi', sortOrder: 0, modifiedAt: at(0) })).json()
    expect(retry.applied).toBe(false)
    expect(retry.store.version).toBe(first.store.version)
  })

  it('soft-deletes with deleted=true', async () => {
    const id = randomUUID()
    await put(id, { name: 'Aldi', sortOrder: 0, modifiedAt: at(0) })
    const res = (await put(id, { name: 'Aldi', sortOrder: 0, modifiedAt: at(1), deleted: true })).json()
    expect(res.store.deleted).toBe(true)
  })

  it('validates the body and id', async () => {
    expect((await put(randomUUID(), { name: '', sortOrder: 0, modifiedAt: at(0) })).statusCode).toBe(400)
    expect((await put(randomUUID(), { name: 'x'.repeat(101), sortOrder: 0, modifiedAt: at(0) })).statusCode).toBe(400)
    expect((await put(randomUUID(), { name: 'Aldi', modifiedAt: at(0) })).statusCode).toBe(400)
    expect((await put('not-a-uuid', { name: 'Aldi', sortOrder: 0, modifiedAt: at(0) })).statusCode).toBe(400)
  })

  it('requires a device key', async () => {
    const res = await app.inject({
      method: 'PUT',
      url: `/v1/stores/${randomUUID()}`,
      payload: { name: 'Aldi', sortOrder: 0, modifiedAt: at(0) },
    })
    expect(res.statusCode).toBe(401)
  })
})

describe('GET /v1/stores?since=', () => {
  it('returns everything for since=0, including tombstones', async () => {
    const a = randomUUID()
    const b = randomUUID()
    await put(a, { name: 'Aldi', sortOrder: 0, modifiedAt: at(0) })
    await put(b, { name: 'Target', sortOrder: 1, modifiedAt: at(0) })
    await put(b, { name: 'Target', sortOrder: 1, modifiedAt: at(1), deleted: true })

    const body = (await changes(0)).json()
    expect(body.stores.map((s: { id: string }) => s.id).sort()).toEqual([a, b].sort())
    expect(body.stores.find((s: { id: string }) => s.id === b).deleted).toBe(true)
    expect(body.hasMore).toBe(false)
  })

  it('returns only changes after the cursor and pages with limit', async () => {
    for (let i = 0; i < 3; i++) await put(randomUUID(), { name: `Store ${i}`, sortOrder: i, modifiedAt: at(i) })
    const page1 = (await changes(0, 2)).json()
    expect(page1.stores).toHaveLength(2)
    expect(page1.hasMore).toBe(true)
    const page2 = (await changes(page1.cursor, 2)).json()
    expect(page2.stores).toHaveLength(1)
    expect(page2.hasMore).toBe(false)
    const empty = (await changes(page2.cursor)).json()
    expect(empty).toEqual({ stores: [], cursor: page2.cursor, hasMore: false })
  })

  it('shares the version sequence with items, so one cursor order is global', async () => {
    const store = (await put(randomUUID(), { name: 'Aldi', sortOrder: 0, modifiedAt: at(0) })).json()
    const item = (
      await app.inject({
        method: 'PUT',
        url: `/v1/shopping-items/${randomUUID()}`,
        headers,
        payload: { name: 'Milk', storeId: store.store.id, modifiedAt: at(0) },
      })
    ).json()
    expect(item.item.version).toBeGreaterThan(store.store.version)
  })
})
