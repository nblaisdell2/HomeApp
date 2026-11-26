// Writes the OpenAPI spec to ../openapi/openapi.json (input for the Android client generator).
// Run after changing any route or schema: npm run openapi:export
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildApp } from '../app.js'

const outFile = fileURLToPath(new URL('../../../openapi/openapi.json', import.meta.url))

// The pg pool connects lazily, so building the app needs no database.
const app = await buildApp({ config: { logLevel: 'warn', isProduction: true } })
await app.ready()
mkdirSync(dirname(outFile), { recursive: true })
writeFileSync(outFile, JSON.stringify(app.swagger(), null, 2) + '\n')
await app.close()
console.log(`Wrote ${outFile}`)
