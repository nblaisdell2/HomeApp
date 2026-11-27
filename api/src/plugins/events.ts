import { EventEmitter } from 'node:events'
import type { ServerResponse } from 'node:http'
import fp from 'fastify-plugin'

export interface ChangeEvent {
  entity: 'shopping_item' | 'store'
  id: string
  version: number
}

/**
 * In-process change bus feeding the SSE endpoint. The API runs as a single Railway instance,
 * so no cross-process fan-out (e.g. Postgres LISTEN/NOTIFY) is needed yet.
 */
export class ChangeBus extends EventEmitter<{ change: [ChangeEvent] }> {
  /** Open SSE responses, closed when the server shuts down. */
  readonly streams = new Set<ServerResponse>()

  publish(event: ChangeEvent) {
    this.emit('change', event)
  }
}

export default fp(
  async (app) => {
    const bus = new ChangeBus()
    bus.setMaxListeners(0)
    app.decorate('changes', bus)

    // Hijacked SSE responses would otherwise keep the server from closing.
    app.addHook('onClose', async () => {
      for (const stream of bus.streams) stream.end()
      bus.streams.clear()
    })
  },
  { name: 'events' },
)

declare module 'fastify' {
  interface FastifyInstance {
    changes: ChangeBus
  }
}
