import { create } from 'zustand'
import * as api from '../lib/api-client'
import type {
  AgentStatus,
  AgentGroup,
  Task,
  TaskStatus,
  TaskPriority,
  Activity as ActivityType,
  Note,
  Session,
  AppConfig,
} from '../types'

interface AppState {
  // Config
  config: AppConfig | null
  
  // Agents
  agents: AgentStatus[]
  currentAgent: AgentStatus | null
  
  // Tasks
  tasks: Task[]
  
  // Activity
  activities: ActivityType[]
  
  // Notes
  notes: Note[]
  
  // Sessions
  sessions: Session[]
  currentSession: Session | null
  
  // Sessions helper actions
  loadRecentSessions: (limit?: number, agentId?: string) => Promise<Session[]>
  updateSessionName: (id: string, name: string, agentId?: string) => Promise<void>
  
  // Groups
  groups: AgentGroup[]
  currentGroup: AgentGroup | null
  
  // User profile
  userAvatar: string | null
  userName: string | null

  // UI State
  sidebarOpen: boolean
  isLoading: boolean
  error: string | null
  showDetails: boolean  // Show tool calls and debug info in chat
  
  // Actions
  init: () => Promise<void>
  toggleSidebar: () => void
  toggleShowDetails: () => void
  
  // Agent actions
  loadAgents: () => Promise<void>
  setCurrentAgent: (agent: AgentStatus | null) => void
  updateAgentProfile: (id: string, updates: Partial<AgentStatus>) => Promise<void>
  setAgentGroup: (agentId: string, groupId: string | null) => Promise<void>
  
  // Group actions
  loadGroups: () => Promise<void>
  createGroup: (name: string, description?: string, color?: string) => Promise<void>
  updateGroup: (id: string, updates: Partial<AgentGroup>) => Promise<void>
  deleteGroup: (id: string) => Promise<void>
  setCurrentGroup: (group: AgentGroup | null) => void
  addAgentToGroup: (agentId: string, groupId: string) => Promise<void>
  removeAgentFromGroup: (agentId: string, groupId: string) => Promise<void>
  
  // Task actions
  loadTasks: (status?: TaskStatus) => Promise<void>
  createTask: (title: string, description?: string, priority?: TaskPriority) => Promise<void>
  updateTask: (id: string, updates: Partial<Task>) => Promise<void>
  deleteTask: (id: string) => Promise<void>
  moveTask: (id: string, status: TaskStatus) => Promise<void>
  
  // Activity actions
  loadActivities: (limit?: number) => Promise<void>

  loadNotes: () => Promise<void>
  createNote: (title: string, content: string) => Promise<void>
  updateNote: (id: string, updates: Partial<Note>) => Promise<void>
  deleteNote: (id: string) => Promise<void>
  
  // Session actions
  loadSessions: (agentId?: string) => Promise<Session[]>
  setCurrentSession: (session: Session | null) => void
  createSession: (name: string, agentId?: string) => Promise<any>
  deleteSession: (sessionId: string, agentId?: string) => Promise<void>

  // User profile actions
  setUserAvatar: (avatar: string | null) => void
  setUserName: (name: string | null) => void
}

export const useAppStore = create<AppState>((set, get) => ({
  // ── Initial State ──────────────────────────────────────────────────────────
  config: null,
  agents: [],
  currentAgent: null,
  tasks: [],
  activities: [],
  notes: [],
  sessions: [],
  currentSession: null,
  groups: [],
  currentGroup: null,
  // User profile — loaded from localStorage so it persists across sessions
  userAvatar: (typeof localStorage !== 'undefined' ? localStorage.getItem('mc_user_avatar') : null),
  userName: (typeof localStorage !== 'undefined' ? localStorage.getItem('mc_user_name') : null),
  sidebarOpen: true,
  isLoading: false,
  error: null,
  showDetails: (typeof localStorage !== 'undefined' ? localStorage.getItem('mc_show_details') === 'true' : false),

  // ── UI Actions ──────────────────────────────────────────────────────────────
  init: async () => {
    try {
      set({ isLoading: true, error: null })

      const config = await api.getConfig()
      set({ config, sidebarOpen: !config.sidebar_collapsed })

      await Promise.all([
        get().loadGroups(),
        get().loadAgents(),
        get().loadTasks(),
        get().loadActivities(50),
        get().loadNotes(),
      ])

      // Load sessions for whichever agent is currently selected
      const currentAgent = get().currentAgent
      await get().loadSessions(currentAgent?.id)
    } catch (error) {
      console.error('Init error:', error)
      set({ error: String(error) })
    } finally {
      set({ isLoading: false })
    }
  },

  toggleSidebar: () => {
    set(state => ({ sidebarOpen: !state.sidebarOpen }))
  },

  toggleShowDetails: () => {
    set(state => {
      const newValue = !state.showDetails
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem('mc_show_details', String(newValue))
      }
      return { showDetails: newValue }
    })
  },

  // ── Agent Actions ──────────────────────────────────────────────────────────
  loadAgents: async () => {
    try {
      const agents = await api.getAgents()
      const prev = get().currentAgent
      // Always update currentAgent if it's set, so avatar/name changes propagate
      const updatedCurrent = prev
        ? agents.find(a => a.id === prev.id) ?? prev
        : (agents.find(a => a.id === 'halo') ?? (agents.length > 0 ? agents[0] : null))
      set({ agents, currentAgent: updatedCurrent })
    } catch (error) {
      set({ error: String(error) })
    }
  },

  setCurrentAgent: (agent) => {
    set({ currentAgent: agent })
  },

  updateAgentProfile: async (id, updates) => {
    try {
      await api.updateAgentProfile(id, updates)
      await get().loadAgents()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  setAgentGroup: async (agentId, groupId) => {
    try {
      await api.setAgentGroup(agentId, groupId)
      await get().loadAgents()
      await get().loadGroups()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  // ── Group Actions ───────────────────────────────────────────────────────────
  loadGroups: async () => {
    try {
      const groups = await api.getGroups()
      set({ groups })
    } catch (error) {
      set({ error: String(error) })
    }
  },

  createGroup: async (name, description, color) => {
    try {
      await api.createGroup(name, description, color)
      await get().loadGroups()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  updateGroup: async (id, updates) => {
    try {
      await api.updateGroup(id, updates)
      await get().loadGroups()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  deleteGroup: async (id) => {
    try {
      await api.deleteGroup(id)
      await get().loadGroups()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  setCurrentGroup: (group) => {
    set({ currentGroup: group })
  },

  addAgentToGroup: async (agentId, groupId) => {
    try {
      await api.setAgentGroup(agentId, groupId)
      await get().loadAgents()
      await get().loadGroups()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  removeAgentFromGroup: async (agentId, groupId) => {
    try {
      await api.setAgentGroup(agentId, null)
      await get().loadAgents()
      await get().loadGroups()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  // ── Task Actions ───────────────────────────────────────────────────────────
  loadTasks: async (status) => {
    try {
      const tasks = await api.getTasks()
      set({ tasks })
    } catch (error) {
      set({ error: String(error) })
    }
  },

  createTask: async (title, description, priority) => {
    try {
      await api.createTask({ title, description, priority })
      await get().loadTasks()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  updateTask: async (id, updates) => {
    try {
      await api.updateTask(id, updates)
      await get().loadTasks()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  deleteTask: async (id) => {
    try {
      await api.deleteTask(id)
      await get().loadTasks()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  moveTask: async (id, status) => {
    await get().updateTask(id, { status })
  },

  // ── Activity Actions ───────────────────────────────────────────────────────
  loadActivities: async (limit = 50) => {
    try {
      const activities = await api.getActivity(limit)
      set({ activities })
    } catch (error) {
      set({ error: String(error) })
    }
  },

  // ── Note Actions ───────────────────────────────────────────────────────────
  loadNotes: async () => {
    try {
      const notes = await api.getNotes()
      set({ notes })
    } catch (error) {
      set({ error: String(error) })
    }
  },

  createNote: async (title, content) => {
    try {
      await api.createNote(title, content)
      await get().loadNotes()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  updateNote: async (id, updates) => {
    try {
      await api.updateNote(id, updates)
      await get().loadNotes()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  deleteNote: async (id) => {
    try {
      await api.deleteNote(id)
      await get().loadNotes()
    } catch (error) {
      set({ error: String(error) })
    }
  },

  // ── Session Actions ────────────────────────────────────────────────────────
  loadRecentSessions: async (limit = 10, agentId?: string) => {
    try {
      const sessions = await api.getSessions(agentId)
      const sorted = [...sessions].sort((a: any, b: any) =>
        new Date(b.updated_at).getTime() - new Date(a.updated_at).getTime()
      )
      set({ sessions: sorted })
      return sorted.slice(0, limit)
    } catch (error) {
      set({ error: String(error) })
      return []
    }
  },

  updateSessionName: async (id, name, agentId) => {
    try {
      await api.updateSessionName(id, name)
      await get().loadSessions(agentId)
    } catch (error) {
      set({ error: String(error) })
    }
  },

  loadSessions: async (agentId?: string) => {
    try {
      const sessions = await api.getSessions(agentId)
      set({ sessions })
      return sessions
    } catch (error) {
      set({ error: String(error) })
      return []
    }
  },

  deleteSession: async (sessionId: string, agentId?: string) => {
    try {
      await api.deleteSession(sessionId)
      // Remove from local store immediately
      const sessions = get().sessions.filter(s => s.id !== sessionId)
      set({ sessions, currentSession: get().currentSession?.id === sessionId ? null : get().currentSession })
      // Reload from server to sync
      await get().loadSessions(agentId)
    } catch (error) {
      set({ error: String(error) })
    }
  },

  setCurrentSession: (session) => {
    set({ currentSession: session })
  },

  createSession: async (name, agentId?: string) => {
    try {
      const session = await api.createSession(name, agentId)
      await get().loadSessions(agentId)
      set({ currentSession: session })
      return session
    } catch (error) {
      set({ error: String(error) })
      return null
    }
  },

  // ── User Profile Actions ────────────────────────────────────────────────────
  setUserAvatar: (avatar) => {
    if (avatar) {
      localStorage.setItem('mc_user_avatar', avatar)
    } else {
      localStorage.removeItem('mc_user_avatar')
    }
    set({ userAvatar: avatar })
  },

  setUserName: (name) => {
    if (name) {
      localStorage.setItem('mc_user_name', name)
    } else {
      localStorage.removeItem('mc_user_name')
    }
    set({ userName: name })
  },
}))
