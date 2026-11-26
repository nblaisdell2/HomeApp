import type { ClientBase, Pool, PoolClient } from 'pg'
import { hashDeviceKey } from '../lib/device-keys.js'

type Db = Pool | ClientBase

export interface Device {
  id: string
  name: string
  createdAt: Date
  lastSeenAt: Date | null
  revokedAt: Date | null
}

interface DeviceRow {
  id: string
  name: string
  created_at: Date
  last_seen_at: Date | null
  revoked_at: Date | null
}

const COLUMNS = 'id, name, created_at, last_seen_at, revoked_at'

const toDevice = (r: DeviceRow): Device => ({
  id: r.id,
  name: r.name,
  createdAt: r.created_at,
  lastSeenAt: r.last_seen_at,
  revokedAt: r.revoked_at,
})

/** Looks up a non-revoked device by its raw API key, and records when it was last seen. */
export async function findActiveDeviceByKey(db: Db, key: string): Promise<Device | null> {
  const { rows } = await db.query<DeviceRow>(
    `UPDATE devices
        SET last_seen_at = now()
      WHERE key_hash = $1 AND revoked_at IS NULL
      RETURNING ${COLUMNS}`,
    [hashDeviceKey(key)],
  )
  return rows[0] ? toDevice(rows[0]) : null
}

export async function insertDevice(db: Db, name: string, key: string): Promise<Device> {
  const { rows } = await db.query<DeviceRow>(
    `INSERT INTO devices (name, key_hash) VALUES ($1, $2) RETURNING ${COLUMNS}`,
    [name, hashDeviceKey(key)],
  )
  return toDevice(rows[0]!)
}

/** Revokes every active key issued to `name`. Returns how many were revoked. */
export async function revokeDevice(db: Db, name: string): Promise<number> {
  const { rowCount } = await db.query(
    `UPDATE devices SET revoked_at = now() WHERE name = $1 AND revoked_at IS NULL`,
    [name],
  )
  return rowCount ?? 0
}

export async function listDevices(db: Db): Promise<Device[]> {
  const { rows } = await db.query<DeviceRow>(`SELECT ${COLUMNS} FROM devices ORDER BY created_at`)
  return rows.map(toDevice)
}
