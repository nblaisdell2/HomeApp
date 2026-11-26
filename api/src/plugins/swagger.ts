import { readFileSync } from 'node:fs'
import fp from 'fastify-plugin'
import swagger from '@fastify/swagger'
import swaggerUi from '@fastify/swagger-ui'

const pkg = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf8')) as {
  version: string
}

/**
 * Publishes the OpenAPI spec built from the route schemas. `npm run openapi:export` writes it
 * to ../openapi/openapi.json, which the Android build turns into the Kotlin API client.
 */
export default fp(
  async (app) => {
    await app.register(swagger, {
      openapi: {
        openapi: '3.0.3',
        info: {
          title: 'Home API',
          description: 'Backend for the Home app (phone + wall tablet).',
          version: pkg.version,
        },
        tags: [
          { name: 'health', description: 'Service status' },
          { name: 'shopping', description: 'Shopping list items' },
          { name: 'events', description: 'Live change notifications' },
        ],
        components: {
          securitySchemes: {
            bearerAuth: {
              type: 'http',
              scheme: 'bearer',
              description: 'Per-device API key issued with `npm run issue-key`',
            },
          },
        },
      },
      // Name shared schemas after their $id instead of def-0, def-1, ...
      refResolver: {
        buildLocalReference: (json, _baseUri, _fragment, i) =>
          typeof json.$id === 'string' ? json.$id : `def-${i}`,
      },
    })

    if (!app.config.isProduction) {
      await app.register(swaggerUi, { routePrefix: '/docs' })
    }
  },
  { name: 'swagger' },
)
