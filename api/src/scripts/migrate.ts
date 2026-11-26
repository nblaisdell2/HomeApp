// Usage: npm run migrate          (apply all pending migrations)
//        npm run migrate:down     (roll back the most recent one)
// On Railway this runs as the pre-deploy command: node dist/scripts/migrate.js up
import { loadConfig } from '../config.js'
import { runMigrations } from '../lib/migrate.js'

const direction = process.argv[2] === 'down' ? 'down' : 'up'
const applied = await runMigrations(loadConfig().databaseUrl, direction)
console.log(applied.length ? `Migrated ${direction}: ${applied.map((m) => m.name).join(', ')}` : 'No migrations to run')
