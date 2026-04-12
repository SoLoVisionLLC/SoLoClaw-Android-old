import { useEffect } from 'react'
import { Bot, CheckCircle2, Clock, Zap, MessageSquare, Activity, Calendar, AlertTriangle, CircleDot, Wifi, WifiOff } from 'lucide-react'
import { useAppStore } from '../hooks/use-store'
import { useIsMobile } from '../hooks/use-is-mobile'
import { AgentAvatar } from '../components/AgentAvatar'
import type { AgentStatus, Task } from '../types'

// ─── Semantic color maps ────────────────────────────────────────────────────────
const STATE_COLORS: Record<string, { dot: string; bg: string; text: string }> = {
  online:  { dot: '#22c55e', bg: 'rgba(34,197,94,0.12)',  text: '#16a34a' },
  working: { dot: '#3b82f6', bg: 'rgba(59,130,246,0.12)', text: '#2563eb' },
  idle:    { dot: '#f59e0b', bg: 'rgba(245,158,11,0.12)', text: '#d97706' },
  offline: { dot: '#ef4444', bg: 'rgba(239,68,68,0.12)',  text: '#dc2626' },
  error:   { dot: '#ef4444', bg: 'rgba(239,68,68,0.12)',  text: '#dc2626' },
}
const DEFAULT_STATE = { dot: '#64748b', bg: 'rgba(100,116,139,0.12)', text: '#475569' }

const PRIORITY_COLORS: Record<string, { bg: string; text: string }> = {
  low:      { bg: 'rgba(34,197,94,0.12)',   text: '#16a34a' },
  medium:   { bg: 'rgba(245,158,11,0.12)',  text: '#d97706' },
  high:     { bg: 'rgba(249,115,22,0.12)',  text: '#ea580c' },
  critical: { bg: 'rgba(239,68,68,0.12)',   text: '#dc2626' },
}

const STATUS_BADGE: Record<string, { bg: string; text: string }> = {
  todo:        { bg: 'var(--bg-tertiary)',       text: 'var(--text-muted)' },
  in_progress: { bg: 'rgba(59,130,246,0.12)',    text: '#2563eb' },
  done:        { bg: 'rgba(34,197,94,0.12)',     text: '#16a34a' },
}

// ─── Compact KPI card ────────────────────────────────────────────────────────────
function StatCard({
  title, value, icon: Icon, accent,
}: {
  title: string, value: string | number, icon: any, accent?: string,
}) {
  const c = accent || 'var(--accent)'
  return (
    <div style={{
      background: 'var(--bg-secondary)', borderRadius: 14, padding: '14px 16px',
      border: '1px solid var(--border)',
      display: 'flex', alignItems: 'center', gap: 12,
    }}>
      <div style={{
        width: 36, height: 36, borderRadius: 10, background: `${c}18`,
        display: 'flex', alignItems: 'center', justifyContent: 'center', color: c, flexShrink: 0,
      }}>
        <Icon size={18} />
      </div>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 22, fontWeight: 800, color: 'var(--text-primary)', lineHeight: 1.1 }}>{value}</div>
        <div style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600, marginTop: 2 }}>{title}</div>
      </div>
    </div>
  )
}

// ─── Attention strip ─────────────────────────────────────────────────────────────
function AttentionChip({ label, count, color, icon: Icon }: {
  label: string, count: number, color: string, icon: any
}) {
  if (count === 0) return null
  return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '6px 12px', borderRadius: 10,
      background: `${color}18`, color,
      fontSize: 12, fontWeight: 600,
    }}>
      <Icon size={13} />
      {count} {label}
    </div>
  )
}

// ─── Agent row (dense table style) ───────────────────────────────────────────────
function AgentRow({ agent }: { agent: AgentStatus }) {
  const sc = STATE_COLORS[agent.state] || DEFAULT_STATE
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'minmax(0,2fr) 90px minmax(0,1.2fr) 72px 60px 60px',
      alignItems: 'center', gap: 8,
      padding: '10px 14px',
      borderBottom: '1px solid var(--border)',
      fontSize: 13, color: 'var(--text-primary)',
    }}>
      {/* Name + state */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
        <AgentAvatar size={28} name={agent.name} avatar={agent.avatar} />
        <span style={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {agent.name}
        </span>
      </div>

      {/* State badge */}
      <span style={{
        display: 'inline-flex', alignItems: 'center', gap: 4,
        padding: '3px 8px', borderRadius: 6,
        background: sc.bg, color: sc.text,
        fontSize: 11, fontWeight: 700, textTransform: 'capitalize',
      }}>
        <span style={{ width: 5, height: 5, borderRadius: '50%', background: sc.dot }} />
        {agent.state}
      </span>

      {/* Model */}
      <span style={{ fontSize: 12, color: 'var(--text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {agent.model || 'GPT-4'}
      </span>

      {/* Messages */}
      <span style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'right' }}>
        {agent.message_count} msg
      </span>

      {/* Tools */}
      <span style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'right' }}>
        {agent.tool_calls_count}
      </span>

      {/* Sub-agents */}
      <span style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'right' }}>
        {agent.sub_agents_active}
      </span>
    </div>
  )
}

// ─── Agent table header ──────────────────────────────────────────────────────────
function AgentTableHeader() {
  const cell: React.CSSProperties = {
    fontSize: 10, fontWeight: 700, color: 'var(--text-muted)',
    textTransform: 'uppercase', letterSpacing: '0.06em',
    padding: '8px 14px', borderBottom: '1px solid var(--border)',
  }
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'minmax(0,2fr) 90px minmax(0,1.2fr) 72px 60px 60px',
      gap: 8,
      background: 'var(--bg-tertiary)',
      borderRadius: '14px 14px 0 0',
    }}>
      <div style={cell}>Agent</div>
      <div style={cell}>State</div>
      <div style={cell}>Model</div>
      <div style={{ ...cell, textAlign: 'right' }}>Msgs</div>
      <div style={{ ...cell, textAlign: 'right' }}>Tools</div>
      <div style={{ ...cell, textAlign: 'right' }}>Sub</div>
    </div>
  )
}

// ─── Compact task row ────────────────────────────────────────────────────────────
function TaskRow({ task }: { task: Task }) {
  const pri = PRIORITY_COLORS[task.priority] || PRIORITY_COLORS.medium
  const st = STATUS_BADGE[task.status] || STATUS_BADGE.todo

  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px',
      background: 'var(--bg-secondary)', borderRadius: 12,
      border: '1px solid var(--border)',
    }}>
      {/* Status badge */}
      <span style={{
        padding: '3px 8px', borderRadius: 6, fontSize: 10, fontWeight: 700,
        background: st.bg, color: st.text, textTransform: 'capitalize', whiteSpace: 'nowrap',
      }}>
        {task.status.replace('_', ' ')}
      </span>

      {/* Title */}
      <span style={{
        flex: 1, fontSize: 13, color: 'var(--text-primary)', fontWeight: 500,
        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
      }}>
        {task.title}
      </span>

      {/* Priority badge */}
      <span style={{
        padding: '3px 8px', borderRadius: 6, fontSize: 10, fontWeight: 700,
        background: pri.bg, color: pri.text, textTransform: 'uppercase', letterSpacing: 0.4,
        whiteSpace: 'nowrap',
      }}>
        {task.priority}
      </span>

      {/* Blockers indicator */}
      {(task as any).blockers && (
        <AlertTriangle size={13} style={{ color: '#ea580c', flexShrink: 0 }} />
      )}
    </div>
  )
}

// ─── Compact activity row ────────────────────────────────────────────────────────
function ActivityRow({ type, message, time }: { type: string, message: string, time: string }) {
  const icons: Record<string, any> = {
    message: MessageSquare, tool_call: Zap, file_operation: Activity,
    task_created: CheckCircle2, agent_connected: Bot,
  }
  const Icon = icons[type] || Activity

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0' }}>
      <div style={{
        width: 26, height: 26, borderRadius: 7, background: 'var(--bg-tertiary)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', flexShrink: 0,
      }}>
        <Icon size={12} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, color: 'var(--text-primary)', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{message}</div>
        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 1 }}>{time}</div>
      </div>
    </div>
  )
}

// ─── Dashboard ───────────────────────────────────────────────────────────────────
export function Dashboard() {
  const { agents, currentAgent, tasks, activities, loadAgents, loadTasks, loadActivities } = useAppStore()

  useEffect(() => {
    const interval = setInterval(() => { loadAgents(); loadTasks(); loadActivities(20) }, 5000)
    return () => clearInterval(interval)
  }, [loadAgents, loadTasks, loadActivities])

  const todoCount = tasks.filter((t: Task) => t.status === 'todo').length
  const inProgressCount = tasks.filter((t: Task) => t.status === 'in_progress').length
  const doneCount = tasks.filter((t: Task) => t.status === 'done').length

  // Attention signals
  const idleAgents = agents.filter((a: AgentStatus) => a.state === 'idle').length
  const errorAgents = agents.filter((a: AgentStatus) => a.state === 'error' || a.state === 'offline').length
  const urgentTasks = tasks.filter((t: Task) => t.priority === 'high' || t.priority === 'critical').length
  const blockedTasks = tasks.filter((t: Task) => (t as any).blockers).length
  const hasAttention = idleAgents + errorAgents + urgentTasks + blockedTasks > 0

  const isMobile = useIsMobile()

  return (
    <div style={{ animation: 'fadeIn 0.4s ease-out', padding: isMobile ? 12 : 20 }}>
      {/* Header */}
      <div style={{
        display: 'flex',
        flexDirection: isMobile ? 'column' : 'row',
        justifyContent: 'space-between',
        alignItems: isMobile ? 'flex-start' : 'center',
        gap: isMobile ? 8 : 0,
        marginBottom: isMobile ? 16 : 20,
      }}>
        <div>
          <h1 style={{ fontSize: isMobile ? 22 : 26, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 4 }}>
            Dashboard
          </h1>
          <p style={{ fontSize: isMobile ? 13 : 14, color: 'var(--text-secondary)' }}>
            Hermes Mission Control
          </p>
        </div>
        {currentAgent && (
          <div style={{
            display: 'flex', alignItems: 'center', gap: 10, padding: isMobile ? '8px 14px' : '10px 16px',
            background: 'var(--bg-secondary)', borderRadius: 14, border: '1px solid var(--border)',
          }}>
            <div style={{
              width: 8, height: 8, borderRadius: '50%',
              background: currentAgent.state === 'online' ? '#22c55e' : currentAgent.state === 'working' ? '#3b82f6' : '#f59e0b',
            }} />
            <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)' }}>{currentAgent.name}</span>
            <span style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'capitalize' }}>({currentAgent.state})</span>
          </div>
        )}
      </div>

      {/* Attention Strip */}
      {hasAttention && (
        <div style={{
          display: 'flex', flexWrap: 'wrap', gap: 8,
          marginBottom: isMobile ? 16 : 20,
        }}>
          <AttentionChip label="idle" count={idleAgents} color="#d97706" icon={Clock} />
          <AttentionChip label="offline / error" count={errorAgents} color="#dc2626" icon={WifiOff} />
          <AttentionChip label="urgent tasks" count={urgentTasks} color="#ea580c" icon={AlertTriangle} />
          <AttentionChip label="blocked" count={blockedTasks} color="#dc2626" icon={AlertTriangle} />
        </div>
      )}

      {/* KPI Row */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: isMobile ? '1fr 1fr' : 'repeat(4, 1fr)',
        gap: isMobile ? 10 : 14,
        marginBottom: isMobile ? 20 : 24,
      }}>
        <StatCard
          title="Active Agents"
          value={agents.length}
          icon={Bot}
          accent="#3b82f6"
        />
        <StatCard
          title="In Progress"
          value={inProgressCount}
          icon={Zap}
          accent="#f59e0b"
        />
        <StatCard
          title="Completed"
          value={doneCount}
          icon={CheckCircle2}
          accent="#22c55e"
        />
        <StatCard
          title="Messages"
          value={agents.reduce((sum: number, a: AgentStatus) => sum + a.message_count, 0)}
          icon={MessageSquare}
          accent="#8b5cf6"
        />
      </div>

      {/* Main Content Grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: isMobile ? '1fr' : '1fr 1fr',
        gap: isMobile ? 14 : 20,
      }}>
        {/* Agent Status Table */}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
            <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)' }}>Agent Status</h2>
            <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
              {agents.filter((a: AgentStatus) => a.state === 'online' || a.state === 'working').length}/{agents.length} active
            </span>
          </div>

          {agents.length > 0 ? (
            <div style={{
              background: 'var(--bg-secondary)', borderRadius: 14,
              border: '1px solid var(--border)', overflow: 'hidden',
            }}>
              <AgentTableHeader />
              {agents.map((agent: AgentStatus) => (
                <AgentRow key={agent.id} agent={agent} />
              ))}
            </div>
          ) : (
            <div style={{
              textAlign: 'center', padding: 32, color: 'var(--text-muted)',
              background: 'var(--bg-secondary)', borderRadius: 14, border: '1px solid var(--border)',
            }}>
              <Bot size={36} style={{ margin: '0 auto 12px', opacity: 0.4 }} />
              <p style={{ fontSize: 13 }}>No agents connected</p>
            </div>
          )}
        </div>

        {/* Recent Tasks */}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
            <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)' }}>Recent Tasks</h2>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-muted)', fontSize: 12 }}>
              <Calendar size={13} />
              {tasks.length} total
            </div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {tasks.slice(0, 8).map((task: Task) => <TaskRow key={task.id} task={task} />)}
            {tasks.length === 0 && (
              <div style={{
                textAlign: 'center', padding: 32, color: 'var(--text-muted)',
                background: 'var(--bg-secondary)', borderRadius: 14, border: '1px solid var(--border)',
              }}>
                <CheckCircle2 size={36} style={{ margin: '0 auto 12px', opacity: 0.4 }} />
                <p style={{ fontSize: 13 }}>No tasks yet</p>
              </div>
            )}
          </div>
        </div>

        {/* Recent Activity — full width below */}
        <div style={{ gridColumn: isMobile ? '1' : '1 / -1' }}>
          <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', marginBottom: 14 }}>Recent Activity</h2>
          <div style={{
            background: 'var(--bg-secondary)', borderRadius: 14, padding: '12px 16px',
            border: '1px solid var(--border)',
          }}>
            {activities.slice(0, 8).map((activity, i) => (
              <ActivityRow
                key={activity.id}
                type={activity.activity_type}
                message={activity.message}
                time={new Date(activity.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              />
            ))}
            {activities.length === 0 && (
              <div style={{ textAlign: 'center', padding: 20, color: 'var(--text-muted)' }}>
                <Activity size={28} style={{ margin: '0 auto 10px', opacity: 0.4 }} />
                <p style={{ fontSize: 13 }}>No recent activity</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
