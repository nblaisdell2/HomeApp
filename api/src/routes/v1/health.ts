import type { FastifyPluginAsyncTypebox } from '@fastify/type-provider-typebox'
import { authErrorResponses, bearerSecurity, DeviceHealthStatus, ref } from '../../schemas/common.js'

const v1: FastifyPluginAsyncTypebox = async (app) => {
  app.get(
    '/health',
    {
      schema: {
        operationId: 'getAuthenticatedHealth',
        tags: ['health'],
        summary: 'Confirms the caller has a valid device API key',
        security: bearerSecurity,
        response: { 200: ref(DeviceHealthStatus), ...authErrorResponses },
      },
    },
    async (request) => ({
      status: 'ok' as const,
      device: request.device!.name,
      time: new Date().toISOString(),
    }),
  )
}

export default v1
