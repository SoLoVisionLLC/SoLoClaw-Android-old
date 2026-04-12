import { useCallback, useEffect, useState } from 'react'
import {
  Clock,
  Plus,
  Play,
  Pause,
  Trash2,
  Edit2,
  X,
  Check,
  AlertCircle,
  Search,
  RefreshCw
} from 'lucide-react'
import { useAppStore } from '../hooks/use-store'
import { useIsMobile } from '../hooks/use-is-mobile'
import * as api from '../lib/api-client'
import type { CronJob, CronJobRun } from '../types'

// Schedule builder types
type ScheduleType = 'daily' | 'weekly' | 'monthly' | 'hourly' | 'minutes'

type ScheduleBuilder = {
  type: ScheduleType
  dailyTime: string
  weeklyDay: string
  weeklyTime: string
  monthlyDay: string
  monthlyTime: string
  minutesInterval: number
}

const PROVIDER_MODELS: Record<string, string[]> = {
  'openai-codex': ['gpt-5.4', 'gpt-5.4-mini'],
  minimax: ['minimax/MiniMax-M2.7', 'MiniMaxAI/MiniMax-M2.5'],
  openrouter: ['openai/gpt-4.1', 'anthropic/claude-3.7-sonnet', 'google/gemini-2.5-pro-preview'],
  anthropic: ['claude-3-7-sonnet-latest', 'claude-3-5-sonnet-latest'],
  google: ['gemini-2.5-pro-preview-03-25', 'gemini-2.0-flash'],
  copilot: ['gpt-4o', 'claude-3.5-sonnet'],
}

// Helper to build cron expression from schedule builder
function buildCronExpression(builder: ScheduleBuilder): string {
  switch (builder.type) {
    case 'daily':
      const [hours, minutes] = builder.dailyTime.split(':')
      return `${minutes} ${hours} * * *`
    case 'weekly':
      const [wHours, wMinutes] = builder.weeklyTime.split(':')
      return `${wMinutes} ${wHours} * * ${builder.weeklyDay}`
    case 'monthly':
      const [mHours, mMinutes] = builder.monthlyTime.split(':')
      return `${mMinutes} ${mHours} ${builder.monthlyDay} * *`
    case 'hourly':
      return `0 * * * *`
    case 'minutes':
      return `*/${builder.minutesInterval} * * * *`
    default:
      return '0 9 * * *'
  }
}

// Helper to get human-readable schedule description
function getScheduleDescription(schedule: string): string {
  // Simple parsing for common patterns
  if (schedule === '0 * * * *') return 'Every hour'
  if (schedule.startsWith('*/')) {
    const minutes = schedule.split(' ')[0].replace('*/', '')
    return `Every ${minutes} minutes`
  }
  const parts = schedule.split(' ')
  if (parts.length === 5) {
    const minute = parts[0]
    const hour = parts[1]
    const dayOfMonth = parts[2]
    const month = parts[3]
    const dayOfWeek = parts[4]
    
    if (dayOfMonth === '*' && month === '*' && dayOfWeek === '*') {
      return `Daily at ${hour}:${minute}`
    }
    if (dayOfMonth === '*' && month === '*') {
      const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
      return `Weekly on ${days[parseInt(dayOfWeek)]} at ${hour}:${minute}`
    }
    if (month === '*') {
      return `Monthly on day ${dayOfMonth} at ${hour}:${minute}`
    }
  }
  return schedule
}

// Status badge component
function StatusBadge({ enabled, consecutiveErrors }: { enabled: boolean; consecutiveErrors: number }) {
  if (!enabled) {
    return (
      <span style={{ 
        padding: '4px 8px', 
        borderRadius: 12, 
        background: 'var(--bg-tertiary)', 
        color: 'var(--text-muted)',
        fontSize: 12,
        fontWeight: 600
      }}>
        Disabled
      </span>
    )
  }
  if (consecutiveErrors > 0) {
    return (
      <span style={{ 
        padding: '4px 8px', 
        borderRadius: 12, 
        background: '#fee2e2', 
        color: '#ef4444',
        fontSize: 12,
        fontWeight: 600
      }}>
        <AlertCircle size={12} style={{ display: 'inline', marginRight: 4 }} />
        Failing
      </span>
    )
  }
  return (
    <span style={{ 
      padding: '4px 8px', 
      borderRadius: 12, 
      background: '#dcfce7', 
      color: '#22c55e',
      fontSize: 12,
      fontWeight: 600
    }}>
      <Check size={12} style={{ display: 'inline', marginRight: 4 }} />
      Healthy
    </span>
  )
}

// Job card component
function JobCard({
  job,
  isSelected,
  onClick,
  onEdit,
  onDelete,
  onToggle,
  onRun,
  onRefresh,
  onCopyJson,
  isRunning = false,
  isMobile = false
}: {
  job: CronJob
  isSelected: boolean
  onClick: () => void
  onEdit: () => void
  onDelete: () => void
  onToggle: () => void
  onRun: () => void
  onRefresh?: () => void
  onCopyJson?: () => void
  isRunning?: boolean
  isMobile?: boolean
}) {
  const agent = useAppStore(state => state.agents.find(a => a.id === job.agent_id))
  
  return (
    <div 
      onClick={onClick}
      style={{
        padding: 16,
        borderRadius: 12,
        background: isSelected ? 'var(--accent-soft)' : 'white',
        border: `1px solid ${isSelected ? 'var(--accent)' : 'var(--border-light)'}`,
        cursor: 'pointer',
        transition: 'all 0.2s ease',
        width: '100%',
        boxSizing: 'border-box',
        overflow: 'hidden',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 12 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {job.name}
          </div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {agent?.name || 'No agent'} • {job.session_target}
          </div>
        </div>
        <StatusBadge enabled={job.enabled} consecutiveErrors={job.consecutive_errors} />
      </div>
      
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
        <Clock size={14} color="var(--text-muted)" />
        <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
          {getScheduleDescription(job.schedule)}
        </span>
      </div>
      
      {job.last_run && (
        <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>
          Last run: {new Date(job.last_run).toLocaleString()}
        </div>
      )}
      
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
        <button
          onClick={(e) => { e.stopPropagation(); onToggle(); }}
          style={{
            padding: '5px 8px',
            borderRadius: 8,
            border: '1px solid var(--border)',
            background: 'transparent',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 3,
            fontSize: 11,
            color: 'var(--text-secondary)',
          }}
        >
          {job.enabled ? <Pause size={13} /> : <Play size={13} />}
          {job.enabled ? 'Pause' : 'Enable'}
        </button>
        <button
          onClick={(e) => { e.stopPropagation(); onRun(); }}
          disabled={isRunning}
          style={{
            padding: '5px 8px',
            borderRadius: 8,
            border: '1px solid var(--border)',
            background: isRunning ? 'var(--accent-soft)' : 'transparent',
            cursor: isRunning ? 'wait' : 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 3,
            fontSize: 11,
            color: isRunning ? 'var(--accent)' : 'var(--text-secondary)',
            opacity: isRunning ? 0.9 : 1,
          }}
        >
          <Play size={13} />
          {isRunning ? 'Running…' : 'Run'}
        </button>
        <button
          onClick={(e) => { e.stopPropagation(); onEdit(); }}
          style={{
            padding: '5px 8px',
            borderRadius: 8,
            border: '1px solid var(--border)',
            background: 'transparent',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 3,
            fontSize: 11,
            color: 'var(--text-secondary)',
          }}
        >
          <Edit2 size={13} />
          Edit
        </button>
        {onRefresh ? (
          <button
            onClick={(e) => { e.stopPropagation(); onRefresh(); }}
            style={{
              padding: '5px 8px',
              borderRadius: 8,
              border: '1px solid var(--border)',
              background: 'transparent',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 3,
              fontSize: 11,
              color: 'var(--text-secondary)',
            }}
          >
            <RefreshCw size={13} />
            Refresh
          </button>
        ) : null}
        {onCopyJson ? (
          <button
            onClick={(e) => { e.stopPropagation(); onCopyJson(); }}
            style={{
              padding: '5px 8px',
              borderRadius: 8,
              border: '1px solid var(--border)',
              background: 'transparent',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 3,
              fontSize: 11,
              color: 'var(--text-secondary)',
            }}
          >
            Copy JSON
          </button>
        ) : null}
        <button
          onClick={(e) => { e.stopPropagation(); onDelete(); }}
          style={{
            padding: '5px 8px',
            borderRadius: 8,
            border: '1px solid var(--border)',
            background: 'transparent',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 3,
            fontSize: 11,
            color: '#ef4444',
          }}
        >
          <Trash2 size={13} />
        </button>
      </div>
    </div>
  )
}

// Modal component for add/edit
function JobModal({ 
  isOpen, 
  onClose, 
  job, 
  onSave,
  agents
}: { 
  isOpen: boolean
  onClose: () => void
  job?: CronJob
  onSave: (job: Partial<CronJob>) => void
  agents: { id: string; name: string }[]
}) {
  const [name, setName] = useState('')
  const [command, setCommand] = useState('')
  const [agentId, setAgentId] = useState('')
  const [sessionTarget, setSessionTarget] = useState('main')
  const [provider, setProvider] = useState('')
  const [model, setModel] = useState('')
  const [enabled, setEnabled] = useState(true)
  const [scheduleBuilder, setScheduleBuilder] = useState<ScheduleBuilder>({
    type: 'daily',
    dailyTime: '09:00',
    weeklyDay: '1',
    weeklyTime: '09:00',
    monthlyDay: '1',
    monthlyTime: '09:00',
    minutesInterval: 15,
  })
  const [schedule, setSchedule] = useState('0 9 * * *')

  useEffect(() => {
    if (!isOpen) return

    setName(job?.name || '')
    setCommand(job?.command || '')
    setAgentId(job?.agent_id || '')
    setSessionTarget(job?.session_target || 'main')
    setProvider(job?.provider || '')
    setModel(job?.model || '')
    setEnabled(job?.enabled ?? true)
    setSchedule(job?.schedule || '0 9 * * *')
  }, [job, isOpen])

  const availableModels = provider ? (PROVIDER_MODELS[provider] || []) : []

  if (!isOpen) return null

  const handleSave = () => {
    onSave({
      id: job?.id,
      name,
      schedule,
      command,
      agent_id: agentId || undefined,
      session_target: sessionTarget,
      provider: provider || undefined,
      model: model || undefined,
      enabled,
    })
    onClose()
  }

  const updateScheduleBuilder = (updates: Partial<ScheduleBuilder>) => {
    const newBuilder = { ...scheduleBuilder, ...updates }
    setScheduleBuilder(newBuilder)
    setSchedule(buildCronExpression(newBuilder))
  }

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      background: 'rgba(0,0,0,0.5)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 1000,
    }}>
      <div style={{
        background: 'white',
        borderRadius: 16,
        width: '90%',
        maxWidth: 700,
        maxHeight: '90vh',
        overflow: 'auto',
        padding: 24,
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <h2 style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)' }}>
            {job ? 'Edit Cron Job' : 'Add Cron Job'}
          </h2>
          <button onClick={onClose} style={{ padding: 8, borderRadius: 8, border: 'none', background: 'transparent', cursor: 'pointer' }}>
            <X size={20} color="var(--text-muted)" />
          </button>
        </div>

        <div style={{ display: 'grid', gap: 16 }}>
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              Name
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="My cron job"
              style={{
                width: '100%',
                padding: '10px 14px',
                borderRadius: 10,
                border: '1px solid var(--border)',
                fontSize: 14,
                outline: 'none',
              }}
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              Schedule
            </label>
            <select
              value={scheduleBuilder.type}
              onChange={(e) => updateScheduleBuilder({ type: e.target.value as ScheduleType })}
              style={{
                width: '100%',
                padding: '10px 14px',
                borderRadius: 10,
                border: '1px solid var(--border)',
                fontSize: 14,
                marginBottom: 12,
              }}
            >
              <option value="daily">Daily at a specific time</option>
              <option value="weekly">Weekly on a specific day</option>
              <option value="monthly">Monthly on a specific date</option>
              <option value="hourly">Every hour</option>
              <option value="minutes">Every X minutes</option>
            </select>

            {scheduleBuilder.type === 'daily' && (
              <input
                type="time"
                value={scheduleBuilder.dailyTime}
                onChange={(e) => updateScheduleBuilder({ dailyTime: e.target.value })}
                style={{
                  padding: '10px 14px',
                  borderRadius: 10,
                  border: '1px solid var(--border)',
                  fontSize: 14,
                }}
              />
            )}

            {scheduleBuilder.type === 'weekly' && (
              <div style={{ display: 'flex', gap: 8 }}>
                <select
                  value={scheduleBuilder.weeklyDay}
                  onChange={(e) => updateScheduleBuilder({ weeklyDay: e.target.value })}
                  style={{
                    padding: '10px 14px',
                    borderRadius: 10,
                    border: '1px solid var(--border)',
                    fontSize: 14,
                  }}
                >
                  <option value="0">Sunday</option>
                  <option value="1">Monday</option>
                  <option value="2">Tuesday</option>
                  <option value="3">Wednesday</option>
                  <option value="4">Thursday</option>
                  <option value="5">Friday</option>
                  <option value="6">Saturday</option>
                </select>
                <input
                  type="time"
                  value={scheduleBuilder.weeklyTime}
                  onChange={(e) => updateScheduleBuilder({ weeklyTime: e.target.value })}
                  style={{
                    padding: '10px 14px',
                    borderRadius: 10,
                    border: '1px solid var(--border)',
                    fontSize: 14,
                  }}
                />
              </div>
            )}

            {scheduleBuilder.type === 'monthly' && (
              <div style={{ display: 'flex', gap: 8 }}>
                <input
                  type="number"
                  min="1"
                  max="31"
                  value={scheduleBuilder.monthlyDay}
                  onChange={(e) => updateScheduleBuilder({ monthlyDay: e.target.value })}
                  style={{
                    padding: '10px 14px',
                    borderRadius: 10,
                    border: '1px solid var(--border)',
                    fontSize: 14,
                    width: 80,
                  }}
                />
                <input
                  type="time"
                  value={scheduleBuilder.monthlyTime}
                  onChange={(e) => updateScheduleBuilder({ monthlyTime: e.target.value })}
                  style={{
                    padding: '10px 14px',
                    borderRadius: 10,
                    border: '1px solid var(--border)',
                    fontSize: 14,
                  }}
                />
              </div>
            )}

            {scheduleBuilder.type === 'minutes' && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: 14, color: 'var(--text-secondary)' }}>Every</span>
                <input
                  type="number"
                  min="1"
                  max="59"
                  value={scheduleBuilder.minutesInterval}
                  onChange={(e) => updateScheduleBuilder({ minutesInterval: parseInt(e.target.value) || 15 })}
                  style={{
                    padding: '10px 14px',
                    borderRadius: 10,
                    border: '1px solid var(--border)',
                    fontSize: 14,
                    width: 80,
                  }}
                />
                <span style={{ fontSize: 14, color: 'var(--text-secondary)' }}>minutes</span>
              </div>
            )}

            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
              Cron: {schedule} ({getScheduleDescription(schedule)})
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
                Agent
              </label>
              <select
                value={agentId}
                onChange={(e) => setAgentId(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  borderRadius: 10,
                  border: '1px solid var(--border)',
                  fontSize: 14,
                }}
              >
                <option value="">— No specific agent —</option>
                {agents.map(agent => (
                  <option key={agent.id} value={agent.id}>{agent.name}</option>
                ))}
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
                Session
              </label>
              <select
                value={sessionTarget}
                onChange={(e) => setSessionTarget(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  borderRadius: 10,
                  border: '1px solid var(--border)',
                  fontSize: 14,
                }}
              >
                <option value="main">Main session</option>
                <option value="isolated">Isolated session</option>
              </select>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
                Provider
              </label>
              <select
                value={provider}
                onChange={(e) => {
                  const nextProvider = e.target.value
                  setProvider(nextProvider)
                  const models = nextProvider ? (PROVIDER_MODELS[nextProvider] || []) : []
                  if (models.length > 0 && !models.includes(model)) {
                    setModel(models[0])
                  }
                  if (!nextProvider) {
                    setModel('')
                  }
                }}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  borderRadius: 10,
                  border: '1px solid var(--border)',
                  fontSize: 14,
                }}
              >
                <option value="">— Auto / default —</option>
                <option value="openai-codex">openai-codex</option>
                <option value="minimax">minimax</option>
                <option value="openrouter">openrouter</option>
                <option value="anthropic">anthropic</option>
                <option value="google">google</option>
                <option value="copilot">copilot</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
                Model
              </label>
              {provider ? (
                <select
                  value={model}
                  onChange={(e) => setModel(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    borderRadius: 10,
                    border: '1px solid var(--border)',
                    fontSize: 14,
                  }}
                >
                  {availableModels.length === 0 ? <option value="">No preset models</option> : null}
                  {availableModels.map((option) => (
                    <option key={option} value={option}>{option}</option>
                  ))}
                </select>
              ) : (
                <input
                  type="text"
                  value={model}
                  onChange={(e) => setModel(e.target.value)}
                  placeholder="Select a provider first, or leave both blank for auto"
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    borderRadius: 10,
                    border: '1px solid var(--border)',
                    fontSize: 14,
                    outline: 'none',
                  }}
                />
              )}
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              Command / Action
            </label>
            <textarea
              value={command}
              onChange={(e) => setCommand(e.target.value)}
              placeholder="What should this job do? Write your prompt here..."
              rows={6}
              style={{
                width: '100%',
                padding: '10px 14px',
                borderRadius: 10,
                border: '1px solid var(--border)',
                fontSize: 14,
                resize: 'vertical',
                fontFamily: 'inherit',
              }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <input
              type="checkbox"
              id="enabled"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
            />
            <label htmlFor="enabled" style={{ fontSize: 14, color: 'var(--text-primary)', cursor: 'pointer' }}>
              Enabled
            </label>
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 24 }}>
          <button
            onClick={onClose}
            style={{
              padding: '10px 20px',
              borderRadius: 10,
              border: '1px solid var(--border)',
              background: 'transparent',
              cursor: 'pointer',
              fontSize: 14,
              color: 'var(--text-secondary)',
            }}
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={!name || !command}
            style={{
              padding: '10px 20px',
              borderRadius: 10,
              border: 'none',
              background: 'var(--accent)',
              color: 'white',
              cursor: name && command ? 'pointer' : 'not-allowed',
              fontSize: 14,
              fontWeight: 600,
              opacity: name && command ? 1 : 0.5,
            }}
          >
            {job ? 'Update Job' : 'Add Job'}
          </button>
        </div>
      </div>
    </div>
  )
}

function formatDateTime(value?: string) {
  if (!value) return '--'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? '--' : d.toLocaleString()
}

function SummaryCard({ label, value, subtext, compact = false }: { label: string; value: string; subtext?: string; compact?: boolean }) {
  return (
    <div style={{ padding: compact ? '8px 10px' : 14, borderRadius: 12, background: 'var(--bg-secondary)', border: '1px solid var(--border-light)' }}>
      <div style={{ fontSize: compact ? 10 : 12, color: 'var(--text-muted)', marginBottom: compact ? 3 : 6 }}>{label}</div>
      <div style={{ fontSize: compact ? 13 : 16, fontWeight: 700, color: 'var(--text-primary)', lineHeight: 1.2 }}>{value || '--'}</div>
      {subtext ? <div style={{ fontSize: compact ? 10 : 12, color: 'var(--text-secondary)', marginTop: compact ? 2 : 6 }}>{subtext}</div> : null}
    </div>
  )
}

function AccordionSection({ title, count, defaultOpen, children }: { title: string; count?: number; defaultOpen?: boolean; children: React.ReactNode }) {
  const [open, setOpen] = useState(defaultOpen ?? true)
  return (
    <div style={{ marginBottom: 8 }}>
      <button
        onClick={() => setOpen(v => !v)}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 14px',
          borderRadius: 12,
          background: 'var(--bg-secondary)',
          border: '1px solid var(--border-light)',
          cursor: 'pointer',
          marginBottom: open ? 8 : 0,
        }}
      >
        <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' }}>
          {title}{count !== undefined ? ` (${count})` : ''}
        </span>
        <span style={{ fontSize: 16, color: 'var(--text-muted)', transition: 'transform 0.2s', transform: open ? 'rotate(0deg)' : 'rotate(-90deg)' }}>▾</span>
      </button>
      {open ? children : null}
    </div>
  )
}

function CronDetailPanel({
  job,
  runs,
  onClose,
  onRefresh,
  onRun,
  onEdit,
  onCopyJson,
  onExport,
  isMobile = false,
}: {
  job: CronJob
  runs: CronJobRun[]
  onClose: () => void
  onRefresh: () => void
  onRun: () => void
  onEdit: () => void
  onCopyJson?: () => void
  onExport?: () => void
  isMobile?: boolean
}) {
  const latest = runs[0]
  const latestFailure = runs.find(run => !run.success)
  const latestSuccess = runs.find(run => run.success)
  const successCount = runs.filter(run => run.success).length
  const failureCount = runs.filter(run => !run.success).length

  // Compact stats — 3 columns on mobile, 6 on desktop
  const statCols = isMobile ? 3 : 6
  const stats = [
    { label: 'Last run', value: formatDateTime(latest?.completed_at || latest?.started_at || job.last_run), subtext: latest?.status || '--' },
    { label: 'Last fail', value: formatDateTime(latestFailure?.completed_at || latestFailure?.started_at), subtext: latestFailure?.provider || '' },
    { label: 'Last OK', value: formatDateTime(latestSuccess?.completed_at || latestSuccess?.started_at), subtext: latestSuccess?.provider || '' },
    { label: 'Attempts', value: String(runs.length), subtext: `${failureCount} fail · ${successCount} ok` },
    { label: 'Errs', value: String(job.consecutive_errors || 0), subtext: job.enabled ? 'enabled' : 'paused' },
    { label: 'Next', value: formatDateTime(job.next_run), subtext: job.enabled ? 'scheduled' : 'disabled' },
  ]

  const actionBtn = (label: string, onClick: () => void, primary?: boolean) => (
    <button
      key={label}
      onClick={onClick}
      style={{
        flexShrink: 0,
        padding: '7px 12px',
        borderRadius: 20,
        border: primary ? 'none' : '1px solid var(--border)',
        background: primary ? 'var(--accent)' : 'white',
        color: primary ? 'white' : 'var(--text-secondary)',
        cursor: 'pointer',
        fontSize: 12,
        fontWeight: 600,
        whiteSpace: 'nowrap',
      }}
    >
      {label}
    </button>
  )

  const metaData: [string, string][] = [
    ['Job ID', job.id],
    ['Agent', job.agent_id || '—'],
    ['Session', job.session_target || '—'],
    ['Provider', job.provider || 'auto'],
    ['Model', job.model || 'auto'],
    ['Delivery', job.delivery_mode || '—'],
    ['Schedule', getScheduleDescription(job.schedule)],
    ['Enabled', job.enabled ? 'Yes' : 'No'],
  ]

  const metaStyle: React.CSSProperties = {
    padding: '8px 10px',
    borderRadius: 10,
    background: 'var(--bg-secondary)',
    border: '1px solid var(--border-light)',
  }
  const metaLabel: React.CSSProperties = { fontSize: 10, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 2 }
  const metaValue: React.CSSProperties = { fontSize: 12, color: 'var(--text-primary)', fontWeight: 500, wordBreak: 'break-all' }

  return (
    <div style={{
      padding: isMobile ? 14 : 20,
      borderRadius: isMobile ? 12 : 16,
      background: 'white',
      border: '1px solid var(--border-light)',
      width: '100%',
      maxWidth: '100%',
      boxSizing: 'border-box',
      minWidth: 0,
      overflow: 'hidden',
    }}>
      {/* ── Mobile: no header — card above already shows name/schedule/actions ── */}
      {/* ── Desktop: slim header + actions ── */}
      {!isMobile && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 14, gap: 12 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 15, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 3, wordBreak: 'break-word', lineHeight: 1.3 }}>{job.name}</div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', wordBreak: 'break-all' }}>{getScheduleDescription(job.schedule)}</div>
          </div>
          <button onClick={onClose} style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: 'var(--text-muted)', padding: '4px 6px', flexShrink: 0, fontSize: 18 }}>×</button>
        </div>
      )}

      {!isMobile && (
        <div style={{ display: 'flex', gap: 6, overflowX: 'auto', marginBottom: 14, paddingBottom: 2, scrollbarWidth: 'none' }}>
          {actionBtn('▶ Run now', onRun, true)}
          {actionBtn('Edit job', onEdit)}
          {actionBtn('Refresh', onRefresh)}
          {onCopyJson ? actionBtn('Copy JSON', onCopyJson) : null}
          {onExport ? actionBtn('Export JSON', onExport) : null}
        </div>
      )}

      {/* ── Stats grid ── */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${statCols}, minmax(0, 1fr))`,
        gap: 8,
        marginBottom: 14,
      }}>
        {stats.map(s => (
          <SummaryCard key={s.label} label={s.label} value={s.value} subtext={s.subtext} compact />
        ))}
      </div>

      {/* ── Latest failure alert ── */}
      {latestFailure?.error ? (
        <div style={{ marginBottom: 14, padding: '10px 12px', borderRadius: 12, background: '#fff1f2', border: '1px solid #fecdd3' }}>
          <div style={{ fontSize: 11, color: '#be123c', fontWeight: 700, marginBottom: 4 }}>Latest failure · {formatDateTime(latestFailure.completed_at || latestFailure.started_at)}</div>
          <div style={{ fontSize: 12, color: '#b91c1c', whiteSpace: 'pre-wrap', lineHeight: 1.4 }}>{latestFailure.error}</div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>{latestFailure.duration_ms ?? '--'}ms · {latestFailure.provider || '--'}</div>
        </div>
      ) : null}

      {/* ── Recent runs accordion ── */}
      <AccordionSection title="Recent attempts" count={runs.length} defaultOpen={!isMobile}>
        {runs.length === 0 ? (
          <div style={{ padding: 16, borderRadius: 12, background: 'var(--bg-secondary)', color: 'var(--text-muted)', fontSize: 13, textAlign: 'center' }}>No run history yet</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {runs.map((run) => {
              const isEmptyOutput = !run.error && (!run.output || run.output.trim() === '(No response generated)')
              return (
                <div key={run.id} style={{
                  padding: '10px 12px',
                  borderRadius: 12,
                  border: `1px solid ${run.success ? 'var(--border-light)' : '#fecdd3'}`,
                  background: 'white',
                }}>
                  {/* Row: status + time */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      <span style={{
                        padding: '2px 8px',
                        borderRadius: 999,
                        background: run.success ? '#dcfce7' : '#fee2e2',
                        color: run.success ? '#166534' : '#b91c1c',
                        fontSize: 11,
                        fontWeight: 700,
                      }}>
                        {run.status}
                      </span>
                      {isEmptyOutput ? (
                        <span style={{ padding: '2px 8px', borderRadius: 999, background: '#fef3c7', color: '#92400e', fontSize: 11, fontWeight: 700 }}>empty</span>
                      ) : null}
                      {run.delivery_status ? (
                        <span style={{ padding: '2px 8px', borderRadius: 999, background: 'var(--bg-secondary)', fontSize: 11 }}>{run.delivery_status}</span>
                      ) : null}
                    </div>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{run.duration_ms ?? '--'}ms</span>
                  </div>
                  {/* Row: timestamp + provider */}
                  <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: run.error || (run.output && !isEmptyOutput) ? 6 : 0 }}>
                    {formatDateTime(run.completed_at || run.started_at)} · {run.provider || '—'} · {run.model || '—'}
                  </div>
                  {/* Error */}
                  {run.error ? (
                    <div style={{ marginTop: 4, padding: '7px 9px', borderRadius: 8, background: '#fff1f2', fontSize: 11, color: '#b91c1c', whiteSpace: 'pre-wrap', lineHeight: 1.4 }}>{run.error}</div>
                  ) : null}
                  {/* Output */}
                  {run.output && !isEmptyOutput ? (
                    <div style={{ marginTop: 4, padding: '7px 9px', borderRadius: 8, background: 'var(--bg-secondary)', fontSize: 11, color: 'var(--text-primary)', whiteSpace: 'pre-wrap', lineHeight: 1.4 }}>
                      {run.output.length > 200 ? run.output.slice(0, 200) + '…' : run.output}
                    </div>
                  ) : null}
                  {/* Session key */}
                  {run.session_key ? (
                    <div style={{ marginTop: 4, fontSize: 10, color: 'var(--text-muted)', fontFamily: 'monospace', wordBreak: 'break-all' }}>{run.session_key.slice(0, 60)}{run.session_key.length > 60 ? '…' : ''}</div>
                  ) : null}
                </div>
              )
            })}
          </div>
        )}
      </AccordionSection>
    </div>
  )
}

export function Cron() {

  const isMobile = useIsMobile()
  const { agents } = useAppStore()
  const [jobs, setJobs] = useState<CronJob[]>([])
  const [selectedJob, setSelectedJob] = useState<CronJob | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingJob, setEditingJob] = useState<CronJob | undefined>(undefined)
  const [filter, setFilter] = useState('all')
  const [searchQuery, setSearchQuery] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isLoadingRuns, setIsLoadingRuns] = useState(false)
  const [selectedJobRuns, setSelectedJobRuns] = useState<CronJobRun[]>([])
  const [runningJobId, setRunningJobId] = useState<string | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)

  const loadJobs = useCallback(async () => {
    setIsLoading(true)
    try {
      const result = await api.getCronJobs()

      let filtered = result
      if (filter !== 'all') {
        filtered = result.filter((job) => {
          switch (filter) {
            case 'enabled': return job.enabled
            case 'disabled': return !job.enabled
            case 'failing': return job.consecutive_errors > 0
            case 'healthy': return job.consecutive_errors === 0 && job.enabled
            default: return true
          }
        })
      }

      setJobs(filtered)
      // Keep selected job in sync if it still exists in the refreshed list
      if (selectedJob) {
        const refreshed = result.find((job) => job.id === selectedJob.id) || null
        setSelectedJob(refreshed)
      }
    } catch (error) {
      console.error('Failed to load cron jobs:', error)
    } finally {
      setIsLoading(false)
    }
  }, [filter, selectedJob])

  useEffect(() => {
    loadJobs()
  }, [filter])

  useEffect(() => {
    if (!actionMessage) return

    const timeout = window.setTimeout(() => {
      setActionMessage(null)
    }, 4000)

    return () => window.clearTimeout(timeout)
  }, [actionMessage])

  useEffect(() => {
    const loadRuns = async () => {
      if (!selectedJob?.id) {
        setSelectedJobRuns([])
        return
      }
      setIsLoadingRuns(true)
      try {
        const runs = await api.getCronJobRuns(selectedJob.id, 24)
        setSelectedJobRuns(runs)
      } catch (error) {
        console.error('Failed to load cron job runs:', error)
        setSelectedJobRuns([])
      } finally {
        setIsLoadingRuns(false)
      }
    }
    loadRuns()
  }, [selectedJob?.id])

  const handleCreate = async (jobData: Partial<CronJob>) => {
    try {
      await api.createCronJob(jobData)
      loadJobs()
    } catch (error) {
      console.error('Failed to create cron job:', error)
    }
  }

  const handleUpdate = async (jobData: Partial<CronJob>) => {
    if (!jobData.id) return
    try {
      await api.updateCronJob(jobData.id, jobData)
      loadJobs()
      if (selectedJob?.id === jobData.id) {
        setSelectedJob(null)
      }
    } catch (error) {
      console.error('Failed to update cron job:', error)
    }
  }

  const handleDelete = async (jobId: string) => {
    if (!confirm('Are you sure you want to delete this cron job?')) return
    try {
      await api.deleteCronJob(jobId)
      loadJobs()
      if (selectedJob?.id === jobId) {
        setSelectedJob(null)
      }
    } catch (error) {
      console.error('Failed to delete cron job:', error)
    }
  }

  const handleToggle = async (job: CronJob) => {
    try {
      await api.updateCronJob(job.id, { enabled: !job.enabled })
      loadJobs()
    } catch (error) {
      console.error('Failed to toggle cron job:', error)
    }
  }

  const handleRunNow = async (job: CronJob) => {
    try {
      setRunningJobId(job.id)
      setActionMessage(`Triggering "${job.name}"...`)
      await api.runCronJobNow(job.id)
      setActionMessage(`Triggered "${job.name}". Refreshing timeline...`)
      await loadJobs()
      const runs = await api.getCronJobRuns(job.id, 24)
      setSelectedJobRuns(runs)
      setActionMessage(`Triggered "${job.name}". Timeline updated.`)
    } catch (error) {
      console.error('Failed to run cron job now:', error)
      setActionMessage(`Failed to run "${job.name}". Check console/logs.`)
    } finally {
      setRunningJobId(null)
    }
  }

  const handleCopyTimeline = async (job: CronJob) => {
    try {
      const runs = await api.getCronJobRuns(job.id, 24)
      await navigator.clipboard.writeText(JSON.stringify({ job, runs }, null, 2))
      setActionMessage(`Copied timeline for "${job.name}"`)
    } catch (error) {
      console.error('Failed to copy timeline:', error)
    }
  }

  const handleExportTimeline = (job: CronJob, runs: CronJobRun[]) => {
    const blob = new Blob([JSON.stringify({ job, runs }, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `cron-timeline-${job.id}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  const handleRefreshTimeline = async (job: CronJob) => {
    await loadJobs()
    const runs = await api.getCronJobRuns(job.id, 24)
    setSelectedJobRuns(runs)
  }

  const filteredJobs = jobs.filter(job => {
    if (searchQuery) {
      const query = searchQuery.toLowerCase()
      return job.name.toLowerCase().includes(query) || 
             job.command.toLowerCase().includes(query)
    }
    return true
  })

  const agentOptions = agents.map(a => ({ id: a.id, name: a.name }))

  return (
    <div style={{ padding: isMobile ? 16 : 24, width: '100%', maxWidth: '100%', boxSizing: 'border-box', overflowX: 'hidden' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: isMobile ? 'stretch' : 'center', flexDirection: isMobile ? 'column' : 'row', marginBottom: 24, gap: 12 }}>
        <div>
          <h1 style={{ fontSize: isMobile ? 24 : 28, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 6 }}>⏰ Cron Jobs</h1>
          <p style={{ fontSize: 15, color: 'var(--text-secondary)' }}>Manage scheduled tasks and automation</p>
        </div>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <button
            onClick={loadJobs}
            style={{
              padding: '10px 16px',
              borderRadius: 10,
              border: '1px solid var(--border)',
              background: 'transparent',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontSize: 14,
              color: 'var(--text-secondary)',
            }}
          >
            <RefreshCw size={16} />
            Refresh
          </button>
          <button
            onClick={() => {
              setEditingJob(undefined)
              setIsModalOpen(true)
            }}
            style={{
              padding: '10px 16px',
              borderRadius: 10,
              border: 'none',
              background: 'var(--accent)',
              color: 'white',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontSize: 14,
              fontWeight: 600,
            }}
          >
            <Plus size={16} />
            Add Job
          </button>
        </div>
      </div>

      {/* Filters */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap', flexDirection: isMobile ? 'column' : 'row' }}>
        <div style={{ position: 'relative', flex: 1, minWidth: isMobile ? '100%' : 200 }}>
          <Search size={16} style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
          <input
            type="text"
            placeholder="Search jobs..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              padding: '10px 14px 10px 38px',
              borderRadius: 10,
              border: '1px solid var(--border)',
              fontSize: 14,
              outline: 'none',
            }}
          />
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {['all', 'enabled', 'disabled', 'failing', 'healthy'].map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              style={{
                padding: '10px 16px',
                borderRadius: 10,
                border: '1px solid var(--border)',
                background: filter === f ? 'var(--accent-soft)' : 'transparent',
                color: filter === f ? 'var(--accent)' : 'var(--text-secondary)',
                cursor: 'pointer',
                fontSize: 14,
                fontWeight: filter === f ? 600 : 500,
                textTransform: 'capitalize',
              }}
            >
              {f}
            </button>
          ))}
        </div>
      </div>

      {actionMessage && (
        <div style={{
          marginBottom: 16,
          padding: '12px 14px',
          borderRadius: 10,
          background: 'var(--accent-soft)',
          color: 'var(--text-primary)',
          fontSize: 14,
          fontWeight: 500,
        }}>
          {actionMessage}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: selectedJob && !isMobile ? 'minmax(320px, 420px) minmax(0, 1fr)' : '1fr', gap: 20, alignItems: 'start' }}>
        <div>
          {isLoading ? (
            <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)' }}>
              <RefreshCw size={32} style={{ margin: '0 auto 16px', animation: 'spin 1s linear infinite' }} />
              <p>Loading cron jobs...</p>
            </div>
          ) : filteredJobs.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 60, color: 'var(--text-muted)' }}>
              <Clock size={48} style={{ margin: '0 auto 16px', opacity: 0.5 }} />
              <p style={{ fontSize: 16, marginBottom: 8 }}>No cron jobs found</p>
              <p style={{ fontSize: 14 }}>Create your first scheduled task</p>
            </div>
          ) : (
            <div style={{ display: 'grid', gap: 16 }}>
              {filteredJobs.map(job => {
                const isSelected = selectedJob?.id === job.id
                return (
                  <div key={job.id} style={{ display: 'grid', gap: 12 }}>
                    <JobCard
                      job={job}
                      isSelected={isSelected}
                      isRunning={runningJobId === job.id}
                      isMobile={isMobile}
                      onClick={() => setSelectedJob(isSelected ? null : job)}
                      onEdit={() => {
                        setEditingJob(job)
                        setIsModalOpen(true)
                      }}
                      onDelete={() => handleDelete(job.id)}
                      onToggle={() => handleToggle(job)}
                      onRun={() => handleRunNow(job)}
                      onRefresh={() => handleRefreshTimeline(job)}
                      onCopyJson={() => handleCopyTimeline(job)}
                    />

                    {isMobile && isSelected ? (
                      isLoadingRuns ? (
                        <div style={{ padding: 16, borderRadius: 16, background: 'white', border: '1px solid var(--border-light)', color: 'var(--text-muted)' }}>Loading cron timeline…</div>
                      ) : (
                        <CronDetailPanel
                          job={job}
                          runs={selectedJobRuns}
                          onClose={() => setSelectedJob(null)}
                          onRefresh={() => handleRefreshTimeline(job)}
                          onRun={() => handleRunNow(job)}
                          onEdit={() => {
                            setEditingJob(job)
                            setIsModalOpen(true)
                          }}
                          onCopyJson={() => handleCopyTimeline(job)}
                          onExport={() => handleExportTimeline(job, selectedJobRuns)}
                          isMobile={true}
                        />
                      )
                    ) : null}
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {!isMobile && selectedJob ? (
          <div>
            {isLoadingRuns ? (
              <div style={{ padding: 20, borderRadius: 16, background: 'white', border: '1px solid var(--border-light)', color: 'var(--text-muted)' }}>Loading cron timeline…</div>
            ) : (
              <CronDetailPanel
                job={selectedJob}
                runs={selectedJobRuns}
                onClose={() => setSelectedJob(null)}
                onRefresh={() => handleRefreshTimeline(selectedJob)}
                onRun={() => handleRunNow(selectedJob)}
                onEdit={() => {
                  setEditingJob(selectedJob)
                  setIsModalOpen(true)
                }}
                onCopyJson={() => handleCopyTimeline(selectedJob)}
                onExport={() => handleExportTimeline(selectedJob, selectedJobRuns)}
                isMobile={false}
              />
            )}
          </div>
        ) : null}
      </div>

      {/* Modal */}
      <JobModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        job={editingJob}
        onSave={editingJob ? handleUpdate : handleCreate}
        agents={agentOptions}
      />
    </div>
  )
}
