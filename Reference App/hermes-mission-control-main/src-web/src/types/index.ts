// Shared types between Rust backend and TypeScript frontend
// These mirror the Rust model types

export interface AgentGroup {
  id: string
  name: string
  description?: string
  icon?: string
  color?: string
  agent_ids: string[]
  created_at: string
  updated_at: string
}

export type AgentState = 'online' | 'working' | 'idle' | 'offline' | 'error'

export interface AgentStatus {
  id: string
  name: string
  state: AgentState
  model?: string
  provider?: string
  current_task?: string
  sub_agents_active: number
  last_seen: string
  uptime_seconds: number
  message_count: number
  tool_calls_count: number
  // Profile fields
  avatar?: string
  bio?: string
  role?: string
  capabilities: string[]
  group_id?: string
  metadata?: Record<string, unknown>
}

export type MessageRole = 'user' | 'assistant' | 'system' | 'tool'

export interface ToolCall {
  id: string
  name: string
  arguments: Record<string, unknown>
  result?: string
}

export interface Message {
  id: string
  session_id: string
  role: MessageRole
  content: string
  created_at: string
  tool_calls?: ToolCall[]
}

export interface Session {
  id: string
  name: string
  agent_id: string
  created_at: string
  updated_at: string
  message_count: number
  archived: boolean
}

export type TaskStatus = 'todo' | 'in_progress' | 'done' | 'cancelled' | 'blocked'
export type TaskPriority = 'low' | 'medium' | 'high' | 'critical'

export interface Task {
  id: string
  title: string
  description?: string
  status: TaskStatus
  priority: TaskPriority
  tags: string[]
  agent_id?: string
  session_id?: string
  source?: string
  department?: string
  blockers?: string
  notion_url?: string
  created_at: string
  updated_at: string
  completed_at?: string
  due_at?: string
}

export type ActivityType = 
  | 'message'
  | 'tool_call'
  | 'file_operation'
  | 'task_created'
  | 'task_updated'
  | 'task_completed'
  | 'agent_connected'
  | 'agent_disconnected'
  | 'error'
  | 'warning'
  | 'info'

export interface Activity {
  id: string
  agent_id: string
  activity_type: ActivityType
  message: string
  details?: Record<string, unknown>
  timestamp: string
  session_id?: string
  task_id?: string
}

export interface Note {
  id: string
  title: string
  content: string
  agent_id?: string
  created_at: string
  updated_at: string
  seen_by_agent: boolean
  pinned: boolean
  tags: string[]
}

export interface Rule {
  id: string
  title: string
  content: string
  category: string
  agent_id?: string | null
  created_at: string
  updated_at: string
  pinned: boolean
  tags: string[]
}

export interface AppConfig {
  theme: string
  hermes_url?: string
  auto_connect: boolean
  notifications_enabled: boolean
  activity_log_max_entries: number
  default_agent_id?: string
  sidebar_collapsed: boolean
}

export interface CronJob {
  id: string
  name: string
  schedule: string
  command: string
  agent_id?: string
  session_target: string
  enabled: boolean
  delivery_mode?: string
  delivery_target?: string
  provider?: string
  model?: string
  last_run?: string
  next_run?: string
  failure_count: number
  consecutive_errors: number
  last_error?: string
  created_at: string
  updated_at: string
}

export interface CronJobRun {
  id: string
  job_id: string
  started_at: string
  completed_at?: string
  success: boolean
  status: string
  output?: string
  error?: string
  provider?: string
  model?: string
  session_id?: string
  session_key?: string
  duration_ms?: number
  delivery_status?: string
}

export interface SkillSummary {
  name: string
  description: string
  category: string
  path: string
  source: string
  bundled: boolean
  can_uninstall: boolean
}

export interface SkillFileEntry {
  name: string
  relative_path: string
  size: number
  mtime: string
}
