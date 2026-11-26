import type { FastifyPluginAsync } from 'fastify'
import health from './health.js'
import shoppingItems from './shopping-items/index.js'
import events from './events/index.js'

/**
 * Everything under /v1 requires a device API key. The hook is added inside this
 * (encapsulated) plugin, so it applies to these routes only, not to the public /health.
 */
const v1: FastifyPluginAsync = async (app) => {
  app.addHook('onRequest', app.authenticate)

  await app.register(health)
  await app.register(shoppingItems, { prefix: '/shopping-items' })
  await app.register(events, { prefix: '/events' })
}

export default v1
