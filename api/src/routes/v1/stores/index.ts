import type { FastifyPluginAsyncTypebox } from '@fastify/type-provider-typebox'
import { Type } from 'typebox'
import { authErrorResponses, bearerSecurity, ErrorResponse, ref } from '../../../schemas/common.js'
import { StoreChanges, StoreUpsert, StoreUpsertResult } from '../../../schemas/shopping.js'
import { listStoreChangesSince, upsertStore } from '../../../repositories/stores.repository.js'
import { stripNulls } from '../../../lib/strip-nulls.js'

const stores: FastifyPluginAsyncTypebox = async (app) => {
  app.get(
    '',
    {
      schema: {
        operationId: 'listStoreChanges',
        tags: ['shopping'],
        summary: 'Stores changed since a cursor (use since=0 for everything)',
        description:
          'Returns stores, including deleted tombstones, whose version is greater than `since`. ' +
          'Keep calling with the returned cursor while hasMore is true.',
        security: bearerSecurity,
        querystring: Type.Object({
          since: Type.Integer({ format: 'int64', minimum: 0, default: 0 }),
          limit: Type.Integer({ minimum: 1, maximum: 1000, default: 500 }),
        }),
        response: { 200: ref(StoreChanges), ...authErrorResponses },
      },
    },
    async (request) => listStoreChangesSince(app.pg.pool, request.query.since, request.query.limit),
  )

  app.put(
    '/:id',
    {
      schema: {
        operationId: 'upsertStore',
        tags: ['shopping'],
        summary: 'Create, update or delete (deleted=true) a store; last write wins',
        security: bearerSecurity,
        params: Type.Object({ id: Type.String({ format: 'uuid' }) }),
        body: ref(StoreUpsert),
        response: {
          200: ref(StoreUpsertResult),
          400: ref(ErrorResponse),
          ...authErrorResponses,
        },
      },
      preValidation: stripNulls,
    },
    async (request) => {
      const result = await app.pg.transact((client) => upsertStore(client, request.params.id, request.body))
      if (result.applied) {
        app.changes.publish({ entity: 'store', id: result.store.id, version: result.store.version })
      }
      return result
    },
  )
}

export default stores
