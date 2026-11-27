import type { FastifyPluginAsyncTypebox } from '@fastify/type-provider-typebox'
import { Type } from 'typebox'
import { authErrorResponses, bearerSecurity, ErrorResponse, ref } from '../../../schemas/common.js'
import {
  ShoppingItemChanges,
  ShoppingItemUpsert,
  ShoppingItemUpsertResult,
} from '../../../schemas/shopping.js'
import { listChangesSince, upsertItem } from '../../../repositories/shopping-items.repository.js'
import { stripNulls } from '../../../lib/strip-nulls.js'

const shoppingItems: FastifyPluginAsyncTypebox = async (app) => {
  app.get(
    '',
    {
      schema: {
        operationId: 'listShoppingItemChanges',
        tags: ['shopping'],
        summary: 'Items changed since a cursor (use since=0 for everything)',
        description:
          'Returns items, including deleted tombstones, whose version is greater than `since`. ' +
          'Keep calling with the returned cursor while hasMore is true.',
        security: bearerSecurity,
        querystring: Type.Object({
          since: Type.Integer({ format: 'int64', minimum: 0, default: 0 }),
          limit: Type.Integer({ minimum: 1, maximum: 1000, default: 500 }),
        }),
        response: { 200: ref(ShoppingItemChanges), ...authErrorResponses },
      },
    },
    async (request) => listChangesSince(app.pg.pool, request.query.since, request.query.limit),
  )

  app.put(
    '/:id',
    {
      schema: {
        operationId: 'upsertShoppingItem',
        tags: ['shopping'],
        summary: 'Create, update or delete (deleted=true) an item; last write wins',
        security: bearerSecurity,
        params: Type.Object({ id: Type.String({ format: 'uuid' }) }),
        body: ref(ShoppingItemUpsert),
        response: {
          200: ref(ShoppingItemUpsertResult),
          400: ref(ErrorResponse),
          ...authErrorResponses,
        },
      },
      preValidation: stripNulls,
    },
    async (request) => {
      const result = await app.pg.transact((client) =>
        upsertItem(client, request.params.id, request.body),
      )
      if (result.applied) {
        app.changes.publish({
          entity: 'shopping_item',
          id: result.item.id,
          version: result.item.version,
        })
      }
      return result
    },
  )
}

export default shoppingItems
