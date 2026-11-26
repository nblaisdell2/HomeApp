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
  return app.inject({ method: 'PUT', url: `/v1/shopping-items/${id}`, headers, payload: body })
}

function changes(since = 0, limit?: number) {
  const qs = new URLSearchParams({ since: String(since), ...(limit ? { limit: String(limit) } : {}) })
  return app.inject({ method: 'GET', url: `/v1/shopping-items?${qs}`, headers })
}

describe('PUT /v1/shopping-items/:id', () => {
  it('creates an item', async () => {
    const id = randomUUID()
    const res = await put(id, { name: 'Milk', store: 'Aldi', modifiedAt: at(0) })
    expect(res.statusCode).toBe(200)
    expect(res.json()).toMatchObject({
      applied: true,
      item: { id, name: 'Milk', store: 'Aldi', deleted: false, modifiedAt: at(0) },
    })
    expect(res.json().item.version).toBeGreaterThan(0)
  })

  it('omits store when not set', async () => {
    const res = await put(randomUUID(), { name: 'Eggs', modifiedAt: at(0) })
    expect(res.json().item).not.toHaveProperty('store')
  })

  it('applies a newer edit and bumps the version', async () => {
    const id = randomUUID()
    const first = (await put(id, { name: 'Milk', modifiedAt: at(0) })).json()
    const second = (await put(id, { name: 'Oat milk', modifiedAt: at(5) })).json()
    expect(second.applied).toBe(true)
    expect(second.item.name).toBe('Oat milk')
    expect(second.item.version).toBeGreaterThan(first.item.version)
  })

  it('ignores an older edit (last write wins) and returns the winner', async () => {
    const id = randomUUID()
    await put(id, { name: 'Oat milk', modifiedAt: at(5) })
    const stale = (await put(id, { name: 'Milk', modifiedAt: at(1) })).json()
    expect(stale.applied).toBe(false)
    expect(stale.item.name).toBe('Oat milk')
  })

  it('treats a retry with the same modifiedAt as a no-op', async () => {
    const id = randomUUID()
    const first = (await put(id, { name: 'Milk', modifiedAt: at(0) })).json()
    const retry = (await put(id, { name: 'Milk', modifiedAt: at(0) })).json()
    expect(retry.applied).toBe(false)
    expect(retry.item.version).toBe(first.item.version)
  })

  it('soft-deletes with deleted=true', async () => {
    const id = randomUUID()
    await put(id, { name: 'Milk', modifiedAt: at(0) })
    const res = (await put(id, { name: 'Milk', modifiedAt: at(1), deleted: true })).json()
    expect(res.item.deleted).toBe(true)
  })

  it('validates the body and id', async () => {
    expect((await put(randomUUID(), { name: '', modifiedAt: at(0) })).statusCode).toBe(400)
    expect((await put(randomUUID(), { name: 'Milk' })).statusCode).toBe(400)
    expect((await put('not-a-uuid', { name: 'Milk', modifiedAt: at(0) })).statusCode).toBe(400)
  })
})

describe('GET /v1/shopping-items?since=', () => {
  it('returns everything for since=0, including tombstones', async () => {
    const a = randomUUID()
    const b = randomUUID()
    await put(a, { name: 'Milk', modifiedAt: at(0) })
    await put(b, { name: 'Bread', modifiedAt: at(0) })
    await put(b, { name: 'Bread', modifiedAt: at(1), deleted: true })

    const body = (await changes(0)).json()
    expect(body.items.map((i: { id: string }) => i.id).sort()).toEqual([a, b].sort())
    expect(body.items.find((i: { id: string }) => i.id === b).deleted).toBe(true)
    expect(body.hasMore).toBe(false)
  })

  it('returns only changes after the cursor', async () => {
    await put(randomUUID(), { name: 'Milk', modifiedAt: at(0) })
    const { cursor } = (await changes(0)).json()

    const later = randomUUID()
    await put(later, { name: 'Bread', modifiedAt: at(1) })
    const body = (await changes(cursor)).json()
    expect(body.items).toHaveLength(1)
    expect(body.items[0].id).toBe(later)
    expect(body.cursor).toBeGreaterThan(cursor)

    const empty = (await changes(body.cursor)).json()
    expect(empty).toEqual({ items: [], cursor: body.cursor, hasMore: false })
  })

  it('pages with limit and hasMore', async () => {
    for (let i = 0; i < 3; i++) await put(randomUUID(), { name: `Item ${i}`, modifiedAt: at(i) })
    const page1 = (await changes(0, 2)).json()
    expect(page1.items).toHaveLength(2)
    expect(page1.hasMore).toBe(true)
    const page2 = (await changes(page1.cursor, 2)).json()
    expect(page2.items).toHaveLength(1)
    expect(page2.hasMore).toBe(false)
  })
})
