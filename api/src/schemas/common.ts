import { Type, type Static, type TSchema } from 'typebox'

/**
 * Reference a shared schema (registered with app.addSchema) by its $id while keeping its
 * static type. The OpenAPI spec then emits a named component, which gives the generated
 * Kotlin client clean model names (ShoppingItem instead of an inline response class).
 */
export function ref<T extends TSchema>(schema: T) {
  const id = (schema as { $id?: unknown }).$id
  if (typeof id !== 'string') throw new Error('ref() needs a schema with an $id')
  return Type.Unsafe<Static<T>>({ $ref: `${id}#` })
}

/** A string enum that also declares `type: string`, which OpenAPI client generators need. */
export function StringEnum<const T extends readonly string[]>(values: T, options: { description?: string } = {}) {
  return Type.Unsafe<T[number]>({ type: 'string', enum: [...values], ...options })
}

export const ErrorResponse = Type.Object(
  {
    statusCode: Type.Integer(),
    error: Type.String(),
    message: Type.String(),
  },
  { $id: 'ErrorResponse' },
)

export const HealthStatus = Type.Object(
  {
    status: StringEnum(['ok', 'degraded']),
    database: StringEnum(['connected', 'unreachable']),
    time: Type.String({ format: 'date-time' }),
  },
  { $id: 'HealthStatus' },
)

export const DeviceHealthStatus = Type.Object(
  {
    status: StringEnum(['ok']),
    device: Type.String({ description: 'Name of the device the API key was issued to' }),
    time: Type.String({ format: 'date-time' }),
  },
  { $id: 'DeviceHealthStatus' },
)

/** Applied to every /v1 route so the spec marks it as requiring the device API key. */
export const bearerSecurity = [{ bearerAuth: [] }]

export const authErrorResponses = {
  401: ref(ErrorResponse),
}
