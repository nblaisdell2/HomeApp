import { randomUUID } from 'node:crypto'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { App } from '../src/app.js'
import { buildTestApp, issueTestKey, resetData } from './helpers.js'

// SSE needs a real socket (inject() waits for the response to end), so listen on a free port.
let app: App
let baseUrl: string
let headers: Record<string, string>

beforeAll(async () => {
  app = await buildTestApp()
  await resetData(app)
  headers = (await issueTestKey(app, 'tablet')).headers
  baseUrl = await app.listen({ port: 0, host: '127.0.0.1' })
})
afterAll(() => app.close())

/** Reads SSE frames from a fetch response until `predicate` matches one. */
async function readUntil(res: Response, predicate: (frame: string) => boolean, timeoutMs = 3000) {
  const reader = res.body!.pipeThrough(new TextDecoderStream()).getReader()
  const frames: string[] = []
  let buffer = ''
  const deadline = Date.now() + timeoutMs
  try {
    while (Date.now() < deadline) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += value
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        frames.push(frame)
        if (predicate(frame)) return frames
      }
    }
    throw new Error(`no matching frame; got: ${JSON.stringify(frames)}`)
  } finally {
    await reader.cancel()
  }
}

describe('GET /v1/events', () => {
  it('requires a key', async () => {
    const res = await fetch(`${baseUrl}/v1/events`)
    expect(res.status).toBe(401)
  })

  it('sends ready, then a change event when another device writes', async () => {
    const res = await fetch(`${baseUrl}/v1/events`, { headers })
    expect(res.status).toBe(200)
    expect(res.headers.get('content-type')).toContain('text/event-stream')

    const id = randomUUID()
    setTimeout(() => {
      void fetch(`${baseUrl}/v1/shopping-items/${id}`, {
        method: 'PUT',
        headers: { ...headers, 'content-type': 'application/json' },
        body: JSON.stringify({ name: 'Milk', modifiedAt: new Date().toISOString() }),
      })
    }, 100)

    const frames = await readUntil(res, (f) => f.startsWith('event: change'))
    expect(frames.some((f) => f.includes('event: ready'))).toBe(true)
    const change = frames.find((f) => f.startsWith('event: change'))!
    const data = JSON.parse(change.split('\n').find((l) => l.startsWith('data: '))!.slice(6))
    expect(data).toMatchObject({ entity: 'shopping_item', id })
  })

  it('sends heartbeat comments', async () => {
    const res = await fetch(`${baseUrl}/v1/events`, { headers })
    const frames = await readUntil(res, (f) => f.startsWith(': ping'))
    expect(frames.at(-1)).toBe(': ping')
  })
})
