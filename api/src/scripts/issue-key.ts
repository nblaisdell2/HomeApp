// Issues an API key for one device and prints it once. Only its hash is stored.
// Usage: npm run issue-key -- --name tablet [--rotate]
//   --rotate  revoke any existing key for that name first
import { parseArgs } from 'node:util'
import pg from 'pg'
import { loadConfig } from '../config.js'
import { generateDeviceKey } from '../lib/device-keys.js'
import { insertDevice, listDevices, revokeDevice } from '../repositories/devices.repository.js'

const { values } = parseArgs({
  options: { name: { type: 'string' }, rotate: { type: 'boolean', default: false } },
})
if (!values.name) {
  console.error('Usage: npm run issue-key -- --name <device-name> [--rotate]')
  process.exit(1)
}

const client = new pg.Client({ connectionString: loadConfig().databaseUrl })
await client.connect()
try {
  const active = (await listDevices(client)).filter((d) => d.name === values.name && !d.revokedAt)
  if (active.length && !values.rotate) {
    console.error(`Device "${values.name}" already has an active key. Pass --rotate to replace it.`)
    process.exitCode = 1
  } else {
    if (active.length) await revokeDevice(client, values.name)
    const key = generateDeviceKey()
    await insertDevice(client, values.name, key)
    console.log(`API key for "${values.name}" (shown once; store it on the device):\n\n${key}\n`)
  }
} finally {
  await client.end()
}
