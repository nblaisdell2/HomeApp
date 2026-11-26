import closeWithGrace from 'close-with-grace'
import { buildApp } from './app.js'

const app = await buildApp({
  fastify: process.stdout.isTTY
    ? { logger: { level: process.env.LOG_LEVEL ?? 'info', transport: { target: 'pino-pretty' } } }
    : {},
})

closeWithGrace({ delay: 10_000 }, async ({ err }) => {
  if (err) app.log.error({ err }, 'shutting down after error')
  await app.close()
})

await app.listen({ port: app.config.port, host: app.config.host })
