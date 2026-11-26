import Fastify, { type FastifyServerOptions } from 'fastify'
import type { TypeBoxTypeProvider } from '@fastify/type-provider-typebox'
import { loadConfig, type Config } from './config.js'
import sensiblePlugin from './plugins/sensible.js'
import dbPlugin from './plugins/db.js'
import schemasPlugin from './plugins/schemas.js'
import swaggerPlugin from './plugins/swagger.js'
import eventsPlugin from './plugins/events.js'
import authPlugin from './plugins/auth.js'
import healthRoutes from './routes/health/index.js'
import v1Routes from './routes/v1/index.js'

export interface BuildAppOptions {
  config?: Partial<Config>
  fastify?: FastifyServerOptions
}

/** Builds and configures the Fastify instance. Does not listen; see server.ts. */
export async function buildApp(opts: BuildAppOptions = {}) {
  const config = loadConfig(opts.config)

  const app = Fastify({
    logger: { level: config.logLevel },
    ...opts.fastify,
  }).withTypeProvider<TypeBoxTypeProvider>()

  app.decorate('config', config)

  // Shared plugins are fp()-wrapped, so their decorators (pg, changes, authenticate, ...) are
  // visible app-wide. Swagger must be registered before the routes it documents.
  await app.register(sensiblePlugin)
  await app.register(dbPlugin)
  await app.register(schemasPlugin)
  await app.register(swaggerPlugin)
  await app.register(eventsPlugin)
  await app.register(authPlugin)

  // Feature routes are encapsulated plugins.
  await app.register(healthRoutes, { prefix: '/health' })
  await app.register(v1Routes, { prefix: '/v1' })

  return app
}

export type App = Awaited<ReturnType<typeof buildApp>>

declare module 'fastify' {
  interface FastifyInstance {
    config: Config
  }
}
