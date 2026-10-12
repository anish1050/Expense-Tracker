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

export interface MongoOperationResult<T> {
  status: boolean
  mode: string
  data: T
  id: string
  message: string
  error_code?: number | string
}

function failure<T>(op: string, empty: T, e: unknown): MongoOperationResult<T> {
  const err = e as { message?: string; code?: number | string }
  console.error(`[mongoclient:${op}] ${err.message ?? String(e)}`)
  return { status: false, mode: 'error', data: empty, id: '', message: err.message ?? 'error', error_code: err.code }
}

export const MongoClientFindOne = async <T extends Document>(
  collection: string,
  query: Filter<T>,
  options: FindOptions = {},
): Promise<MongoOperationResult<T | null>> => {
  try {
    const db = await getDatabase()
    const data = await db.collection<T>(collection).findOne(query, options)
    return { status: !!data, mode: 'find', data: (data as T | null) ?? null, id: data?._id?.toString() ?? '', message: data ? 'Record found.' : 'No record found.' }
  } catch (e) {
    return failure('findOne', null, e)
  }
}

export const MongoClientFind = async <T extends Document>(
  collection: string,
  query: Filter<T>,
  options: FindOptions = {},
): Promise<MongoOperationResult<T[]>> => {
  try {
    const db = await getDatabase()
    const data = (await db.collection<T>(collection).find(query, options).toArray()) as unknown as T[]
    return { status: data.length > 0, mode: 'find', data, id: '', message: data.length > 0 ? 'Records found.' : 'No records found.' }
  } catch (e) {
    return failure('find', [], e)
  }
}

export const MongoClientInsertOne = async <T extends Document>(
  collection: string,
  document: OptionalId<T>,
): Promise<MongoOperationResult<InsertOneResult<T> | null>> => {
  try {
    const db = await getDatabase()
    const result = await db.collection<T>(collection).insertOne(document as OptionalUnlessRequiredId<T>)
    return { status: result.acknowledged, mode: 'insert', data: result, id: result.insertedId?.toString() ?? '', message: 'Insert successful.' }
  } catch (e) {
    return failure('insertOne', null, e)
  }
}

export const MongoClientUpdateOne = async <T extends Document>(
  collection: string,
  query: Filter<T>,
  update: UpdateFilter<T>,
  options: UpdateOptions = {},
): Promise<MongoOperationResult<UpdateResult | null>> => {
  if (Object.keys(query).length === 0) throw new Error('Query cannot be empty')
  try {
    const db = await getDatabase()
    const result = await db.collection<T>(collection).updateOne(query, update, options)
    return { status: result.acknowledged, mode: 'update', data: result, id: result.upsertedId?.toString() ?? '', message: result.modifiedCount > 0 ? 'Update successful.' : 'No document updated.' }
  } catch (e) {
    return failure('updateOne', null, e)
  }
}

export const MongoClientFindOneAndUpdate = async <T extends Document>(
  collection: string,
  query: Filter<T>,
  update: UpdateFilter<T>,
  options: FindOneAndUpdateOptions = {},
): Promise<MongoOperationResult<T | null>> => {
  if (Object.keys(query).length === 0) throw new Error('Query cannot be empty')
  try {
    const db = await getDatabase()
    const data = await db.collection<T>(collection).findOneAndUpdate(query, update, options)
    return { status: !!data, mode: 'findOneAndUpdate', data: (data as T | null) ?? null, id: data?._id?.toString() ?? '', message: data ? 'Document updated.' : 'No document matched.' }
  } catch (e) {
    return failure('findOneAndUpdate', null, e)
  }
}

export const MongoClientDeleteMany = async <T extends Document>(
  collection: string,
  query: Filter<T>,
): Promise<MongoOperationResult<DeleteResult | null>> => {
  if (Object.keys(query).length === 0) throw new Error('Query cannot be empty')
  try {
    const db = await getDatabase()
    const result = await db.collection<T>(collection).deleteMany(query)
    return { status: result.acknowledged, mode: 'deleteMany', data: result, id: '', message: `${result.deletedCount} deleted.` }
  } catch (e) {
    return failure('deleteMany', null, e)
  }
}

export const MongoClientBulkWrite = async <T extends Document>(
  collection: string,
  operations: AnyBulkWriteOperation<T>[],
): Promise<MongoOperationResult<BulkWriteResult | null>> => {
  if (operations.length === 0) throw new Error('Operations cannot be empty')
  try {
    const db = await getDatabase()
    const result = await db.collection<T>(collection).bulkWrite(operations, { ordered: false })
    return { status: result.ok === 1, mode: 'bulkWrite', data: result, id: '', message: 'Bulk write done.' }
  } catch (e) {
    // Duplicate keys are an expected outcome for version-guarded upserts, so
    // return them without the error log; callers inspect error_code.
    const err = e as { code?: number; message?: string; result?: BulkWriteResult }
    if (err.code === 11000) return { status: false, mode: 'bulkWrite', data: err.result ?? null, id: '', message: err.message ?? 'duplicate', error_code: 11000 }
    return failure('bulkWrite', null, e)
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
