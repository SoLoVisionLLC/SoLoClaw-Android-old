import type {
  Activity,
  AgentGroup,
  AgentStatus,
  AppConfig,
  CronJob,
  CronJobRun,
  Message,
  Note,
  Session,
  SkillFileEntry,
  SkillSummary,
  Task,
  TaskPriority,
  Rule,
} from '../types'

const isAndroid = typeof navigator !== 'undefined' && /Android/i.test(navigator.userAgent)
const isTauriShell = typeof globalThis !== 'undefined' && (
  '__TAURI_INTERNALS__' in globalThis || '__TAURI__' in globalThis
)

const API_BASE = (
  (globalThis as any).__MISSION_CONTROL_API_BASE__
  ?? (isAndroid && isTauriShell ? 'https://hermes.solobot.cloud' : '')
).replace(/\/$/, '')

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const url = `${API_BASE}${path}`
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(`API error ${response.status}${text ? `: ${text}` : ''}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}

export const getAgents = () => apiFetch<AgentStatus[]>('/api/agents')
export const discoverAgents = () => apiFetch<AgentStatus[]>('/api/agents/discover', { method: 'POST' })
export const updateAgentProfile = (id: string, updates: Partial<AgentStatus>) =>
  apiFetch<AgentStatus>(`/api/agents/${id}`, { method: 'PATCH', body: JSON.stringify(updates) })
export const setAgentGroup = (agentId: string, groupId: string | null) =>
  apiFetch<AgentStatus>('/api/agents/group', {
    method: 'POST',
    body: JSON.stringify({ agent_id: agentId, group_id: groupId }),
  })

export const getGroups = () => apiFetch<AgentGroup[]>('/api/groups')
export const createGroup = (name: string, description?: string, color?: string) =>
  apiFetch<AgentGroup>('/api/groups', {
    method: 'POST',
    body: JSON.stringify({ name, description, color }),
  })
export const updateGroup = (id: string, updates: Partial<AgentGroup>) =>
  apiFetch<AgentGroup>(`/api/groups/${id}`, { method: 'PATCH', body: JSON.stringify(updates) })
export const deleteGroup = (id: string) => apiFetch<void>(`/api/groups/${id}`, { method: 'DELETE' })

export const getTasks = () => apiFetch<Task[]>('/api/tasks')
export const createTask = (payload: { title: string; description?: string; priority?: TaskPriority; agent_id?: string; source?: string; department?: string; blockers?: string; due_at?: string }) =>
  apiFetch<Task>('/api/tasks', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
export const updateTask = (id: string, updates: Partial<Task>) =>
  apiFetch<Task>(`/api/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(updates) })
export const deleteTask = (id: string) => apiFetch<void>(`/api/tasks/${id}`, { method: 'DELETE' })

export const getNotes = () => apiFetch<Note[]>('/api/notes')
export const getRules = () => apiFetch<Rule[]>('/api/rules')
export const createRule = (payload: { title: string; content: string; category: string; tags?: string[]; agent_id?: string | null; pinned?: boolean }) =>
  apiFetch<Rule>('/api/rules', { method: 'POST', body: JSON.stringify(payload) })
export const updateRule = (id: string, payload: Partial<Rule>) =>
  apiFetch<Rule>(`/api/rules/${id}`, { method: 'PATCH', body: JSON.stringify(payload) })
export const deleteRule = (id: string) => apiFetch<void>(`/api/rules/${id}`, { method: 'DELETE' })
export const createNote = (title: string, content: string, agentId?: string) =>
  apiFetch<Note>('/api/notes', {
    method: 'POST',
    body: JSON.stringify({ title, content, agent_id: agentId }),
  })
export const updateNote = (id: string, updates: Partial<Note>) =>
  apiFetch<Note>(`/api/notes/${id}`, { method: 'PATCH', body: JSON.stringify(updates) })
export const deleteNote = (id: string) => apiFetch<void>(`/api/notes/${id}`, { method: 'DELETE' })

export const getActivity = (limit?: number) => apiFetch<Activity[]>(`/api/activity${limit ? `?limit=${limit}` : ''}`)

export const getSessions = (agentId?: string) =>
  apiFetch<Session[]>(agentId ? `/api/sessions?agent_id=${encodeURIComponent(agentId)}` : '/api/sessions')
export const createSession = (name: string, agentId?: string) =>
  apiFetch<Session>('/api/sessions', {
    method: 'POST',
    body: JSON.stringify({ name, agent_id: agentId }),
  })
export const updateSessionName = (id: string, name: string) =>
  apiFetch<Session>(`/api/sessions/${id}`, { method: 'PATCH', body: JSON.stringify({ name }) })
export const deleteSession = (id: string) =>
  apiFetch<void>(`/api/sessions/${id}`, { method: 'DELETE' })
export const generateSessionName = (agentId: string, sessionId: string, firstMessage: string) =>
  apiFetch<{ name: string }>('/api/sessions/generate-name', {
    method: 'POST',
    body: JSON.stringify({ agent_id: agentId, session_id: sessionId, first_message: firstMessage }),
  })
export const getMessages = (sessionId: string) => apiFetch<Message[]>(`/api/sessions/${sessionId}/messages`)
export const sendMessage = (sessionId: string, content: string, agentId: string) =>
  apiFetch<{ user_message: Message; agent_message: Message }>('/api/messages', {
    method: 'POST',
    body: JSON.stringify({ session_id: sessionId, content, agent_id: agentId }),
  })

// Streaming message endpoint for real-time tool call display
export interface StreamEvent {
  event: string
  data: string
}

export const sendMessageStream = (
  sessionId: string,
  content: string,
  agentId: string,
  onEvent: (event: StreamEvent) => void,
  onError?: (error: Error) => void
) => {
  const url = `${API_BASE}/api/messages/stream`
  
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', url, true)
    xhr.setRequestHeader('Content-Type', 'application/json')
    
    let buffer = ''
    
    xhr.onprogress = () => {
      const newData = xhr.responseText.substring(buffer.length)
      buffer = xhr.responseText
      
      // Parse SSE events
      const lines = newData.split('\n')
      let currentEvent: string | null = null
      let currentData: string | null = null
      
      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          currentData = line.substring(5).trim()
        } else if (line === '' && currentEvent && currentData) {
          onEvent({ event: currentEvent, data: currentData })
          currentEvent = null
          currentData = null
        }
      }
    }
    
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve()
      } else {
        reject(new Error(`HTTP ${xhr.status}: ${xhr.statusText}`))
      }
    }
    
    xhr.onerror = () => {
      const error = new Error('Network error')
      onError?.(error)
      reject(error)
    }
    
    xhr.send(JSON.stringify({ session_id: sessionId, content, agent_id: agentId }))
  })
}

export const getCronJobs = () => apiFetch<CronJob[]>('/api/cron-jobs')
export const getCronJobRuns = (jobId: string, limit = 24) => apiFetch<CronJobRun[]>(`/api/cron-jobs/${jobId}/runs?limit=${limit}`)
export const runCronJobNow = (jobId: string) => apiFetch<void>(`/api/cron-jobs/${jobId}/run`, { method: 'POST' })
export const createCronJob = (data: Partial<CronJob>) =>
  apiFetch<CronJob>('/api/cron-jobs', { method: 'POST', body: JSON.stringify(data) })
export const updateCronJob = (id: string, updates: Partial<CronJob>) =>
  apiFetch<CronJob>(`/api/cron-jobs/${id}`, { method: 'PATCH', body: JSON.stringify(updates) })
export const deleteCronJob = (id: string) => apiFetch<void>(`/api/cron-jobs/${id}`, { method: 'DELETE' })

export const getConfig = () => apiFetch<AppConfig>('/api/config')
export const getSkills = () => apiFetch<SkillSummary[]>('/api/skills')
export const checkSkill = (skillName: string) => apiFetch<{ ok: boolean; command: string; stdout: string; stderr: string }>(`/api/skills/${encodeURIComponent(skillName)}/check`, { method: 'POST' })
export const updateSkill = (skillName: string) => apiFetch<{ ok: boolean; command: string; stdout: string; stderr: string }>(`/api/skills/${encodeURIComponent(skillName)}/update`, { method: 'POST' })
export const searchSkillsHub = (query: string) => apiFetch<{ ok: boolean; command: string; stdout: string; stderr: string }>(`/api/skills/hub/search`, { method: 'POST', body: JSON.stringify({ query }) })
export const searchSkillsHubStructured = (query: string) => apiFetch<Array<{ name: string; description: string; source: string; identifier: string; trust_level: string; repo?: string; path?: string; tags?: string[]; extra?: Record<string, unknown> }>>(`/api/skills/hub/search-structured`, { method: 'POST', body: JSON.stringify({ query }) })
export const browseSkillsHubStructured = (page = 1, size = 20, source = 'all') => apiFetch<{ page: number; size: number; total: number; total_pages: number; items: Array<{ name: string; description: string; source: string; identifier: string; trust_level: string; repo?: string; path?: string; tags?: string[]; extra?: Record<string, unknown> }> }>(`/api/skills/hub/browse-structured`, { method: 'POST', body: JSON.stringify({ page, size, source }) })
export const inspectSkillHub = (identifier: string) => apiFetch<{ ok: boolean; command: string; stdout: string; stderr: string }>(`/api/skills/hub/inspect`, { method: 'POST', body: JSON.stringify({ identifier }) })
export const installSkillHub = (identifier: string) => apiFetch<{ ok: boolean; command: string; stdout: string; stderr: string }>(`/api/skills/hub/install`, { method: 'POST', body: JSON.stringify({ identifier }) })
export const deleteSkill = (skillName: string) => apiFetch<{ ok: boolean; removed: string }>(`/api/skills/${encodeURIComponent(skillName)}`, { method: 'DELETE' })
export const getSkillFiles = (skillName: string) => apiFetch<{ path: string; files: SkillFileEntry[] }>(`/api/skills/${encodeURIComponent(skillName)}/files`)
export const getSkillFile = (skillName: string, filePath: string) => apiFetch<{ path: string; content: string }>(`/api/skills/${encodeURIComponent(skillName)}/files/${filePath.split('/').map(encodeURIComponent).join('/')}`)
export const updateSkillFile = (skillName: string, filePath: string, content: string) => apiFetch<{ ok: boolean; path: string }>(`/api/skills/${encodeURIComponent(skillName)}/files/${filePath.split('/').map(encodeURIComponent).join('/')}`, { method: 'PUT', body: JSON.stringify({ content }) })
