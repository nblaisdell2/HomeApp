import type { FastifyPluginAsyncTypebox } from '@fastify/type-provider-typebox'
import { HealthStatus, ref } from '../../schemas/common.js'

/** Public liveness check used by Railway and for quick manual checks. */
const health: FastifyPluginAsyncTypebox = async (app) => {
  app.get(
    '',
    {
      schema: {
        operationId: 'getHealth',
        tags: ['health'],
        summary: 'Service and database status (no auth)',
        response: { 200: ref(HealthStatus), 503: ref(HealthStatus) },
      },
    },
    async (_request, reply) => {
      let database: 'connected' | 'unreachable' = 'connected'
      try {
        await app.pg.query('SELECT 1')
      } catch (err) {
        app.log.warn({ err }, 'health check: database unreachable')
        database = 'unreachable'
      }
      const ok = database === 'connected'
      return reply.code(ok ? 200 : 503).send({
        status: ok ? 'ok' : 'degraded',
        database,
        time: new Date().toISOString(),
      })
    },
  )
}

export default health
