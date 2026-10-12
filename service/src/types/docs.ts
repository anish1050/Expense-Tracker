import type { ObjectId } from 'mongodb'

export interface CoreUserDoc {
  _id: ObjectId
  google_sub: string
  email: string
  name: string
  created_at: Date
  last_login_at: Date
}

export interface DeviceDoc {
  _id: string
  user_id: string
  platform: 'android' | 'ios'
  last_seen_at: Date
}

export type SyncTable = 'accounts' | 'categories' | 'budgets' | 'transactions'

export interface SyncRowDoc {
  _id: string
  user_id: string
  table: SyncTable
  row_id: string
  version: number
  updated_at: Date
  ciphertext: string
  nonce: string
  deleted: boolean
}

export interface ParserRulesDoc {
  _id: ObjectId
  version: number
  rules: Record<string, unknown>
  published_at: Date
}

export interface RequestLogDoc {
  _id: ObjectId
  method: string
  path: string
  status: number
  duration_ms: number
  user_id: string
  created_at: Date
}
