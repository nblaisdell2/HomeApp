import { Type, type Static } from 'typebox'
import { ref } from './common.js'

export const ShoppingItem = Type.Object(
  {
    id: Type.String({ format: 'uuid', description: 'Client-generated UUID' }),
    name: Type.String({ minLength: 1, maxLength: 200 }),
    store: Type.Optional(Type.String({ maxLength: 100 })),
    modifiedAt: Type.String({
      format: 'date-time',
      description: 'When the item was last edited on a device; used for last-write-wins',
    }),
    deleted: Type.Boolean({ description: 'Tombstone: true once the item has been removed' }),
    version: Type.Integer({
      format: 'int64',
      description: 'Server change sequence number; increases on every accepted write',
    }),
  },
  { $id: 'ShoppingItem' },
)
export type ShoppingItem = Static<typeof ShoppingItem>

export const ShoppingItemUpsert = Type.Object(
  {
    name: Type.String({ minLength: 1, maxLength: 200 }),
    store: Type.Optional(Type.String({ maxLength: 100 })),
    modifiedAt: Type.String({ format: 'date-time' }),
    deleted: Type.Optional(Type.Boolean({ default: false })),
  },
  { $id: 'ShoppingItemUpsert' },
)
export type ShoppingItemUpsert = Static<typeof ShoppingItemUpsert>

export const ShoppingItemUpsertResult = Type.Object(
  {
    applied: Type.Boolean({
      description: 'False when the server already had a newer edit; item is then the winning version',
    }),
    item: ref(ShoppingItem),
  },
  { $id: 'ShoppingItemUpsertResult' },
)

export const ShoppingItemChanges = Type.Object(
  {
    items: Type.Array(ref(ShoppingItem)),
    cursor: Type.Integer({
      format: 'int64',
      description: 'Pass as `since` on the next call to get only newer changes',
    }),
    hasMore: Type.Boolean({ description: 'True when more changes are available past cursor' }),
  },
  { $id: 'ShoppingItemChanges' },
)
