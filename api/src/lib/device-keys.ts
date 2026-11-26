import { createHash, randomBytes } from 'node:crypto'

const PREFIX = 'home_'

/** A new random device API key. Shown once when issued; only its hash is stored. */
export function generateDeviceKey(): string {
  return PREFIX + randomBytes(32).toString('base64url')
}

export function hashDeviceKey(key: string): string {
  return createHash('sha256').update(key).digest('hex')
}
