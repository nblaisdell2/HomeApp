import pg from 'pg'
import { runMigrations } from '../src/lib/migrate.js'
import { testDatabaseUrl } from './helpers.js'

/** Recreates the test database and migrates it once per `npm test` run. */
export default async function setup() {
  const url = new URL(testDatabaseUrl())
  const dbName = url.pathname.slice(1)
  const admin = new URL(url)
  admin.pathname = '/postgres'

  const client = new pg.Client({ connectionString: admin.toString() })
  await client.connect()
  try {
    await client.query(`DROP DATABASE IF EXISTS "${dbName}" WITH (FORCE)`)
    await client.query(`CREATE DATABASE "${dbName}"`)
  } finally {
    await client.end()
  }
  await runMigrations(url.toString(), 'up', () => {})
}
