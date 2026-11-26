import type { FastifyPluginAsyncTypebox } from '@fastify/type-provider-typebox'
import type { ChangeEvent } from '../../../plugins/events.js'
import { authErrorResponses, bearerSecurity } from '../../../schemas/common.js'

/**
 * Server-Sent Events stream of data changes. The app keeps this open while it is on screen
 * and pulls fresh data whenever a `change` event arrives (HOME-64).
 *
 * Events:
 *   event: ready   data: {}                                   (sent once on connect)
 *   event: change  data: {"entity":"shopping_item","id":"…","version":42}
 *   ": ping" comments every SSE_HEARTBEAT_MS keep proxies from closing an idle stream.
 */
const events: FastifyPluginAsyncTypebox = async (app) => {
  app.get(
    '',
    {
      schema: {
        operationId: 'streamEvents',
        tags: ['events'],
        summary: 'Server-Sent Events stream of data changes',
        security: bearerSecurity,
        response: {
          200: {
            description: 'text/event-stream with `ready` and `change` events',
            content: { 'text/event-stream': { schema: { type: 'string' } } },
          },
          ...authErrorResponses,
        },
      },
    },
    (request, reply) => {
      reply.hijack()
      const res = reply.raw
      res.writeHead(200, {
        'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache, no-transform',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no',
      })
      res.write('retry: 3000\n\nevent: ready\ndata: {}\n\n')

      const onChange = (event: ChangeEvent) => {
        res.write(`event: change\ndata: ${JSON.stringify(event)}\n\n`)
      }
      const heartbeat = setInterval(() => res.write(': ping\n\n'), app.config.sseHeartbeatMs)

      app.changes.on('change', onChange)
      app.changes.streams.add(res)
      request.log.info({ device: request.device?.name }, 'event stream opened')

      res.on('close', () => {
        clearInterval(heartbeat)
        app.changes.off('change', onChange)
        app.changes.streams.delete(res)
        request.log.info({ device: request.device?.name }, 'event stream closed')
      })
    },
  )
}

export default events
