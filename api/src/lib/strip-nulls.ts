import type { FastifyRequest } from 'fastify'

/**
 * preValidation hook for upsert bodies: treats `"field": null` as "field not set".
 *
 * Fastify's Ajv coerces null to "" for string fields, so a client that spells an unset
 * optional field as null (kotlinx.serialization does by default) would otherwise fail
 * format checks like uuid/date-time, or store an empty string.
 */
export async function stripNulls(request: FastifyRequest) {
  const body = request.body
  if (body && typeof body === 'object' && !Array.isArray(body)) {
    for (const [key, value] of Object.entries(body)) {
      if (value === null) delete (body as Record<string, unknown>)[key]
    }
  }
}
