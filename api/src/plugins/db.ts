import fp from 'fastify-plugin'
import postgres from '@fastify/postgres'
import pg from 'pg'

// Return BIGINT (int8) columns as JS numbers. Our sequence values stay far below 2^53.
pg.types.setTypeParser(pg.types.builtins.INT8, (value) => Number(value))

export default fp(
  async (app) => {
    await app.register(postgres, {
      pg,
      connectionString: app.config.databaseUrl,
      max: 10,
    })
  },
  { name: 'db' },
)
