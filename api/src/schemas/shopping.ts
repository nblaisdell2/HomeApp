import { Type, type Static } from 'typebox'
import { ref } from './common.js'

const modifiedAt = (description?: string) =>
  Type.String({ format: 'date-time', ...(description ? { description } : {}) })

const version = () =>
  Type.Integer({
    format: 'int64',
    description: 'Server change sequence number; increases on every accepted write',
  })

const cursor = () =>
  Type.Integer({
    format: 'int64',
    description: 'Pass as `since` on the next call to get only newer changes',
  })

const hasMore = () => Type.Boolean({ description: 'True when more changes are available past cursor' })

const applied = () =>
  Type.Boolean({
    description: 'False when the server already had a newer edit; the payload is then the winning version',
  })

/** Writable item fields, shared by the item and its upsert body. Every write is a full replacement. */
const itemFields = {
  name: Type.String({ minLength: 1, maxLength: 200 }),
  storeId: Type.Optional(
    Type.String({ format: 'uuid', description: 'Store the item is bought at; omitted means "Anywhere"' }),
  ),
  aisle: Type.Optional(Type.String({ maxLength: 50, description: 'Aisle or section, used to sort within a store' })),
  note: Type.Optional(Type.String({ maxLength: 200, description: 'Quantity or notes, e.g. "2 gallons"' })),
  purchasedAt: Type.Optional(
    Type.String({
      format: 'date-time',
      description: 'Set when checked off in shopping mode; the item then lives in purchase history',
    }),
  ),
}

export const ShoppingItem = Type.Object(
  {
    id: Type.String({ format: 'uuid', description: 'Client-generated UUID' }),
    ...itemFields,
    modifiedAt: modifiedAt('When the item was last edited on a device; used for last-write-wins'),
    deleted: Type.Boolean({ description: 'Tombstone: true once the item has been removed' }),
    version: version(),
  },
  { $id: 'ShoppingItem' },
)
export type ShoppingItem = Static<typeof ShoppingItem>

export const ShoppingItemUpsert = Type.Object(
  {
    ...itemFields,
    modifiedAt: modifiedAt(),
    deleted: Type.Optional(Type.Boolean({ default: false })),
  },
  { $id: 'ShoppingItemUpsert' },
)
export type ShoppingItemUpsert = Static<typeof ShoppingItemUpsert>

export const ShoppingItemUpsertResult = Type.Object(
  { applied: applied(), item: ref(ShoppingItem) },
  { $id: 'ShoppingItemUpsertResult' },
)

export const ShoppingItemChanges = Type.Object(
  { items: Type.Array(ref(ShoppingItem)), cursor: cursor(), hasMore: hasMore() },
  { $id: 'ShoppingItemChanges' },
)

/** Writable store fields, shared by the store and its upsert body. */
const storeFields = {
  name: Type.String({ minLength: 1, maxLength: 100 }),
  sortOrder: Type.Integer({ description: 'Position in the store list and shopping groups (ascending)' }),
}

export const Store = Type.Object(
  {
    id: Type.String({ format: 'uuid', description: 'Client-generated UUID' }),
    ...storeFields,
    modifiedAt: modifiedAt('When the store was last edited on a device; used for last-write-wins'),
    deleted: Type.Boolean({ description: 'Tombstone: true once the store has been removed' }),
    version: version(),
  },
  { $id: 'Store' },
)
export type Store = Static<typeof Store>

export const StoreUpsert = Type.Object(
  {
    ...storeFields,
    modifiedAt: modifiedAt(),
    deleted: Type.Optional(Type.Boolean({ default: false })),
  },
  { $id: 'StoreUpsert' },
)
export type StoreUpsert = Static<typeof StoreUpsert>

export const StoreUpsertResult = Type.Object({ applied: applied(), store: ref(Store) }, { $id: 'StoreUpsertResult' })

export const StoreChanges = Type.Object(
  { stores: Type.Array(ref(Store)), cursor: cursor(), hasMore: hasMore() },
  { $id: 'StoreChanges' },
)
