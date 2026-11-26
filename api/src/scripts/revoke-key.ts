// Revokes a device's API key(s), or lists devices when no name is given.
// Usage: npm run revoke-key -- --name phone
//        npm run revoke-key            (list devices)
import { parseArgs } from 'node:util'
import pg from 'pg'
import { loadConfig } from '../config.js'
import { listDevices, revokeDevice } from '../repositories/devices.repository.js'

const { values } = parseArgs({ options: { name: { type: 'string' } } })

const client = new pg.Client({ connectionString: loadConfig().databaseUrl })
await client.connect()
try {
  if (values.name) {
    const count = await revokeDevice(client, values.name)
    console.log(count ? `Revoked ${count} key(s) for "${values.name}".` : `No active key for "${values.name}".`)
  } else {
    console.table(
      (await listDevices(client)).map((d) => ({
        name: d.name,
        created: d.createdAt.toISOString(),
        lastSeen: d.lastSeenAt?.toISOString() ?? '',
        revoked: d.revokedAt?.toISOString() ?? '',
      })),
    )
  }
} finally {
  await client.end()
}
