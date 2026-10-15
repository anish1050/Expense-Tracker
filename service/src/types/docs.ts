import type { ObjectId } from 'mongodb'

export interface CoreUserDoc {
  _id: ObjectId
  google_sub: string
  email: string
  name: string
  created_at: Date
  last_login_at: Date
  /** Last allocated sync sequence number; see routes/sync allocateSeq. */
  sync_seq: number
}

export interface DeviceDoc {
  /** `${user_id}:${device_id}` so one user can never overwrite another's record. */
  _id: string
  user_id: string
  device_id: string
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
  seq: number
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
