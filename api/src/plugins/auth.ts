import fp from 'fastify-plugin'
import type { FastifyReply, FastifyRequest } from 'fastify'
import { findActiveDeviceByKey, type Device } from '../repositories/devices.repository.js'

/**
 * Per-device API keys sent as `Authorization: Bearer <key>`. Each device (phone, tablet)
 * gets its own key from `npm run issue-key`, so one can be revoked without touching the other.
 */
export default fp(
  async (app) => {
    app.decorateRequest('device', null)

    app.decorate('authenticate', async (request: FastifyRequest, _reply: FastifyReply) => {
      const header = request.headers.authorization
      const key = header?.startsWith('Bearer ') ? header.slice('Bearer '.length).trim() : ''
      if (!key) throw app.httpErrors.unauthorized('Missing API key')

      const device = await findActiveDeviceByKey(app.pg.pool, key)
      if (!device) throw app.httpErrors.unauthorized('Invalid or revoked API key')

      request.device = device
    })
  },
  { name: 'auth' },
)

declare module 'fastify' {
  interface FastifyInstance {
    authenticate: (request: FastifyRequest, reply: FastifyReply) => Promise<void>
  }
  interface FastifyRequest {
    device: Device | null
  }
}
