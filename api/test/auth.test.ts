import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import type { App } from '../src/app.js'
import { revokeDevice } from '../src/repositories/devices.repository.js'
import { buildTestApp, issueTestKey, resetData } from './helpers.js'

let app: App
beforeAll(async () => {
  app = await buildTestApp()
})
afterAll(() => app.close())
beforeEach(() => resetData(app))

describe('GET /health', () => {
  it('is public and reports the database as connected', async () => {
    const res = await app.inject({ method: 'GET', url: '/health' })
    expect(res.statusCode).toBe(200)
    expect(res.json()).toMatchObject({ status: 'ok', database: 'connected' })
  })
})

describe('GET /v1/health (device API key)', () => {
  it('rejects a request without a key', async () => {
    const res = await app.inject({ method: 'GET', url: '/v1/health' })
    expect(res.statusCode).toBe(401)
    expect(res.json()).toMatchObject({ message: 'Missing API key' })
  })

  it('rejects an unknown key', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/v1/health',
      headers: { authorization: 'Bearer home_not-a-real-key' },
    })
    expect(res.statusCode).toBe(401)
  })

  it('accepts a valid key and names the device', async () => {
    const { headers, name } = await issueTestKey(app, 'tablet')
    const res = await app.inject({ method: 'GET', url: '/v1/health', headers })
    expect(res.statusCode).toBe(200)
    expect(res.json()).toMatchObject({ status: 'ok', device: name })
  })

  it('rejects a revoked key', async () => {
    const { headers } = await issueTestKey(app, 'phone')
    await revokeDevice(app.pg.pool, 'phone')
    const res = await app.inject({ method: 'GET', url: '/v1/health', headers })
    expect(res.statusCode).toBe(401)
  })

  it('protects every /v1 route', async () => {
    const res = await app.inject({ method: 'GET', url: '/v1/shopping-items' })
    expect(res.statusCode).toBe(401)
  })
})
