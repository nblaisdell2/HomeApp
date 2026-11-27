import { existsSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { buildApp, type App } from '../src/app.js'
import { generateDeviceKey } from '../src/lib/device-keys.js'
import { insertDevice } from '../src/repositories/devices.repository.js'

if (existsSync('.env')) process.loadEnvFile('.env')

export function testDatabaseUrl() {
  return process.env.TEST_DATABASE_URL ?? 'postgres://home:home@localhost:5433/home_test'
}

export async function buildTestApp(): Promise<App> {
  const app = await buildApp({
    config: { databaseUrl: testDatabaseUrl(), logLevel: 'silent', sseHeartbeatMs: 200 },
  })
  await app.ready()
  return app
}

export async function resetData(app: App) {
  await app.pg.query('TRUNCATE shopping_items, stores, devices')
}

/** Creates a device and returns its Authorization header. */
export async function issueTestKey(app: App, name = `device-${randomUUID().slice(0, 8)}`) {
  const key = generateDeviceKey()
  await insertDevice(app.pg.pool, name, key)
  return { name, key, headers: { authorization: `Bearer ${key}` } }
}
