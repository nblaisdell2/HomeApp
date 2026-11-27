import fp from 'fastify-plugin'
import { DeviceHealthStatus, ErrorResponse, HealthStatus } from '../schemas/common.js'
import {
  ShoppingItem,
  ShoppingItemChanges,
  ShoppingItemUpsert,
  ShoppingItemUpsertResult,
  Store,
  StoreChanges,
  StoreUpsert,
  StoreUpsertResult,
} from '../schemas/shopping.js'

/** Registers shared schemas app-wide so routes can $ref them and the OpenAPI spec names them. */
export default fp(
  async (app) => {
    for (const schema of [
      ErrorResponse,
      HealthStatus,
      DeviceHealthStatus,
      ShoppingItem,
      ShoppingItemUpsert,
      ShoppingItemUpsertResult,
      ShoppingItemChanges,
      Store,
      StoreUpsert,
      StoreUpsertResult,
      StoreChanges,
    ]) {
      app.addSchema(schema)
    }
  },
  { name: 'schemas' },
)
