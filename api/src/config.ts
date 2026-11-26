import { existsSync } from 'node:fs'

// Load api/.env in local development. On Railway the variables come from the service settings.
if (existsSync('.env')) process.loadEnvFile('.env')

export interface Config {
  port: number
  host: string
  databaseUrl: string
  logLevel: string
  isProduction: boolean
  /**
   * Interval for SSE keep-alive comments. Keeps proxies from dropping an idle stream, and lets the
   * app spot a dead connection: it reconnects after 45s without any data (see LiveUpdates.kt).
   */
  sseHeartbeatMs: number
}

export function loadConfig(overrides: Partial<Config> = {}): Config {
  const env = process.env
  return {
    port: Number(env.PORT ?? 3000),
    host: env.HOST ?? '::',
    databaseUrl: env.DATABASE_URL ?? 'postgres://home:home@localhost:5433/home',
    logLevel: env.LOG_LEVEL ?? 'info',
    isProduction: env.NODE_ENV === 'production',
    sseHeartbeatMs: Number(env.SSE_HEARTBEAT_MS ?? 15_000),
    ...overrides,
  }
}
