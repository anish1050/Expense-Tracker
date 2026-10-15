import { getClientPromise } from './mongodb.js'
import type {
  AnyBulkWriteOperation,
  BulkWriteResult,
  Db,
  DeleteResult,
  Document,
  Filter,
  FindOneAndUpdateOptions,
  FindOptions,
  InsertOneResult,
  OptionalId,
  OptionalUnlessRequiredId,
  UpdateFilter,
  UpdateOptions,
  UpdateResult,
} from 'mongodb'

async function getDatabase(): Promise<Db> {
  const dbName = process.env.MONGODB_DBNAME
  if (!dbName) throw new Error('MONGODB_DBNAME environment variable is required but was not set.')
  const client = await getClientPromise()
  return client.db(dbName)
}

// `status` means "did the operation find or change something". Driver and
// network errors are thrown, never folded into an empty result, so a route
// cannot mistake an outage for "no records" (handleError turns them into 500).
export interface MongoOperationResult<T> {
  status: boolean
  mode: string
  data: T
  id: string
  message: string
}

export const MongoClientFindOne = async <T extends Document>(
  collection: string,
  query: Filter<T>,
  options: FindOptions = {},
): Promise<MongoOperationResult<T | null>> => {
  const db = await getDatabase()
  const data = await db.collection<T>(collection).findOne(query, options)
  return { status: !!data, mode: 'find', data: (data as T | null) ?? null, id: data?._id?.toString() ?? '', message: data ? 'Record found.' : 'No record found.' }
}

export const MongoClientFind = async <T extends Document>(
  collection: string,
  query: Filter<T>,
  options: FindOptions = {},
): Promise<MongoOperationResult<T[]>> => {
  const db = await getDatabase()
  const data = (await db.collection<T>(collection).find(query, options).toArray()) as unknown as T[]
  return { status: data.length > 0, mode: 'find', data, id: '', message: data.length > 0 ? 'Records found.' : 'No records found.' }
}

export const MongoClientInsertOne = async <T extends Document>(
  collection: string,
  document: OptionalId<T>,
): Promise<MongoOperationResult<InsertOneResult<T>>> => {
  const db = await getDatabase()
  const result = await db.collection<T>(collection).insertOne(document as OptionalUnlessRequiredId<T>)
  return { status: result.acknowledged, mode: 'insert', data: result, id: result.insertedId?.toString() ?? '', message: 'Insert successful.' }
}

export const MongoClientUpdateOne = async <T extends Document>(
  collection: string,
  query: Filter<T>,
  update: UpdateFilter<T>,
  options: UpdateOptions = {},
): Promise<MongoOperationResult<UpdateResult>> => {
  if (Object.keys(query).length === 0) throw new Error('Query cannot be empty')
  const db = await getDatabase()
  const result = await db.collection<T>(collection).updateOne(query, update, options)
  return { status: result.acknowledged, mode: 'update', data: result, id: result.upsertedId?.toString() ?? '', message: result.modifiedCount > 0 ? 'Update successful.' : 'No document updated.' }
}

export const MongoClientFindOneAndUpdate = async <T extends Document>(
  collection: string,
  query: Filter<T>,
  update: UpdateFilter<T>,
  options: FindOneAndUpdateOptions = {},
): Promise<MongoOperationResult<T | null>> => {
  if (Object.keys(query).length === 0) throw new Error('Query cannot be empty')
  const db = await getDatabase()
  const data = await db.collection<T>(collection).findOneAndUpdate(query, update, options)
  return { status: !!data, mode: 'findOneAndUpdate', data: (data as T | null) ?? null, id: data?._id?.toString() ?? '', message: data ? 'Document updated.' : 'No document matched.' }
}

export const MongoClientDeleteMany = async <T extends Document>(
  collection: string,
  query: Filter<T>,
): Promise<MongoOperationResult<DeleteResult>> => {
  if (Object.keys(query).length === 0) throw new Error('Query cannot be empty')
  const db = await getDatabase()
  const result = await db.collection<T>(collection).deleteMany(query)
  if (!result.acknowledged) throw new Error(`deleteMany on ${collection} was not acknowledged`)
  return { status: result.deletedCount > 0, mode: 'deleteMany', data: result, id: '', message: `${result.deletedCount} deleted.` }
}

export interface BulkWriteOutcome {
  result: BulkWriteResult | null
  /** Indexes into the operations array that failed with a duplicate key (11000). */
  duplicateKeyIndexes: number[]
}

// Unordered bulk write where duplicate keys are an expected outcome (version-
// guarded upserts). Any other error is thrown.
export const MongoClientBulkWrite = async <T extends Document>(
  collection: string,
  operations: AnyBulkWriteOperation<T>[],
): Promise<BulkWriteOutcome> => {
  if (operations.length === 0) throw new Error('Operations cannot be empty')
  const db = await getDatabase()
  try {
    const result = await db.collection<T>(collection).bulkWrite(operations, { ordered: false })
    return { result, duplicateKeyIndexes: [] }
  } catch (e) {
    const err = e as { code?: number; result?: BulkWriteResult; writeErrors?: Array<{ code: number; index: number }> }
    const writeErrors = err.writeErrors ?? []
    if (writeErrors.length > 0 && writeErrors.every((w) => w.code === 11000)) {
      return { result: err.result ?? null, duplicateKeyIndexes: writeErrors.map((w) => w.index) }
    }
    throw e
  }
}

export const MongoClientCreateIndex = async (
  collection: string,
  keys: Document,
  options: Document = {},
): Promise<void> => {
  const db = await getDatabase()
  await db.collection(collection).createIndex(keys, options)
}
