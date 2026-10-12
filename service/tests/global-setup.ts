import { MongoMemoryServer } from 'mongodb-memory-server'

let mongod: MongoMemoryServer

export async function setup() {
  mongod = await MongoMemoryServer.create()
  process.env.MONGODB_URI = mongod.getUri()
  process.env.MONGODB_DBNAME = 'spent_test'
  process.env.JWT_SECRET = 'test-secret-test-secret-test-secret-test-secret'
  process.env.GOOGLE_CLIENT_ID = 'test-client-id.apps.googleusercontent.com'
  process.env.NODE_ENV = 'test'
}

export async function teardown() {
  await mongod.stop()
}
