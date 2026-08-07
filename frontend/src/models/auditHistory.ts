export interface FieldChange {
  field: string
  oldValue: unknown
  newValue: unknown
}

export type RecordHistoryAction = 'CREATED' | 'UPDATED' | 'NO_CHANGE' | 'VIEWED' | 'FAILED'

export interface RecordHistoryEntry {
  timestamp: string
  service: string
  principal: string
  type: string
  outcome: string
  action: RecordHistoryAction
  changes: FieldChange[]
}
