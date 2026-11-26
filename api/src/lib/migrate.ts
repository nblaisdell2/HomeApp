import { fileURLToPath } from 'node:url'
import { runner } from 'node-pg-migrate'

/** api/migrations, resolved the same way from src/lib and dist/lib. */
export const MIGRATIONS_DIR = fileURLToPath(new URL('../../migrations', import.meta.url))

export async function runMigrations(
  databaseUrl: string,
  direction: 'up' | 'down' = 'up',
  log: (msg: string) => void = console.log,
) {
  return runner({
    databaseUrl,
    dir: MIGRATIONS_DIR,
    direction,
    count: direction === 'up' ? Infinity : 1,
    migrationsTable: 'pgmigrations',
    log,
  })
}
