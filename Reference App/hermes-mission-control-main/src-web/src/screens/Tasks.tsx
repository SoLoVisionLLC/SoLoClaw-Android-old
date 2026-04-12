import { useEffect, useMemo, useRef, useState } from 'react'
import { Plus, MoreHorizontal, Tag, CheckCircle2, Clock, ArrowRightCircle, ChevronLeft, ChevronRight, ExternalLink, RefreshCw, CalendarDays, AlertTriangle, GripVertical, CheckSquare } from 'lucide-react'

import { useAppStore } from '../hooks/use-store'
import { useIsMobile } from '../hooks/use-is-mobile'
import * as api from '../lib/api-client'
import type { Task, TaskStatus, TaskPriority } from '../types'

const TASK_BOARD_URL = 'https://www.notion.so/solovisionllc/8e85701f81a6490fa8595c0bc9e52827?v=320c7b069816800eb5e4000c83f5efd8&source=copy_link'
const MANUAL_ORDER_KEY = 'mission-control.task-manual-order.v1'
const TASK_VIEW_PRESETS_KEY = 'mission-control.task-view-presets.v1'

const COLUMNS: { id: TaskStatus; label: string; icon: any }[] = [
  { id: 'todo', label: 'To Do', icon: Clock },
  { id: 'in_progress', label: 'In Progress', icon: ArrowRightCircle },
  { id: 'done', label: 'Done', icon: CheckCircle2 },
]

const PRIORITIES: { id: TaskPriority; label: string; color: string; bgColor: string }[] = [
  { id: 'low', label: 'Low', color: '#22c55e', bgColor: 'rgba(34, 197, 94, 0.1)' },
  { id: 'medium', label: 'Medium', color: '#3b82f6', bgColor: 'rgba(59, 130, 246, 0.1)' },
  { id: 'high', label: 'High', color: '#f59e0b', bgColor: 'rgba(245, 158, 11, 0.1)' },
  { id: 'critical', label: 'Critical', color: '#ef4444', bgColor: 'rgba(239, 68, 68, 0.1)' },
]

const DEPARTMENTS = ['Engineering', 'Marketing', 'Operations', 'Social Media', 'Finance', 'Security', 'General']
const SORT_OPTIONS = ['manual', 'updated_desc', 'priority_desc', 'priority_asc', 'due_asc', 'title_asc'] as const
type ManualOrderMap = Record<string, string[]>
type TaskViewPreset = {
  id: string
  name: string
  filterAgent: string
  filterDepartment: string
  filterPriority: string
  filterQuery: string
  sortBy: SortOption
}

type SortOption = typeof SORT_OPTIONS[number]

function readManualOrder(): ManualOrderMap {
  try {
    return JSON.parse(localStorage.getItem(MANUAL_ORDER_KEY) || '{}')
  } catch {
    return {}
  }
}

function writeManualOrder(value: ManualOrderMap) {
  localStorage.setItem(MANUAL_ORDER_KEY, JSON.stringify(value))
}

function readTaskViewPresets(): TaskViewPreset[] {
  try {
    return JSON.parse(localStorage.getItem(TASK_VIEW_PRESETS_KEY) || '[]')
  } catch {
    return []
  }
}

function writeTaskViewPresets(value: TaskViewPreset[]) {
  localStorage.setItem(TASK_VIEW_PRESETS_KEY, JSON.stringify(value))
}

function formatDate(value?: string) {
  if (!value) return ''
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString()
}

function priorityRank(value: TaskPriority) {
  return { low: 0, medium: 1, high: 2, critical: 3 }[value] ?? 0
}

function sortTasks(tasks: Task[], sort: SortOption, manualOrder: string[]) {
  const copy = [...tasks]
  if (sort === 'manual') {
    const positions = new Map(manualOrder.map((id, index) => [id, index]))
    return copy.sort((a, b) => {
      const ai = positions.has(a.id) ? positions.get(a.id)! : Number.MAX_SAFE_INTEGER
      const bi = positions.has(b.id) ? positions.get(b.id)! : Number.MAX_SAFE_INTEGER
      if (ai !== bi) return ai - bi
      return new Date(b.updated_at).getTime() - new Date(a.updated_at).getTime()
    })
  }
  switch (sort) {
    case 'priority_desc':
      return copy.sort((a, b) => priorityRank(b.priority) - priorityRank(a.priority))
    case 'priority_asc':
      return copy.sort((a, b) => priorityRank(a.priority) - priorityRank(b.priority))
    case 'due_asc':
      return copy.sort((a, b) => {
        const ad = a.due_at ? new Date(a.due_at).getTime() : Number.MAX_SAFE_INTEGER
        const bd = b.due_at ? new Date(b.due_at).getTime() : Number.MAX_SAFE_INTEGER
        return ad - bd
      })
    case 'title_asc':
      return copy.sort((a, b) => a.title.localeCompare(b.title))
    case 'updated_desc':
    default:
      return copy.sort((a, b) => new Date(b.updated_at).getTime() - new Date(a.updated_at).getTime())
  }
}

function TaskCard({
  task,
  onEdit,
  onDelete,
  onDragStart,
  onMove,
  selected,
  onToggleSelect,
  onQuickUpdate,
  canManualReorder,
  onMoveUp,
  onMoveDown,
  onCardDrop,
  agentOptions,
}: {
  task: Task
  onEdit: (task: Task) => void
  onDelete: (task: Task) => void
  onDragStart?: (taskId: string) => void
  onMove: (id: string, status: TaskStatus) => void
  selected: boolean
  onToggleSelect: (id: string) => void
  onQuickUpdate: (id: string, updates: Partial<Task>) => Promise<void>
  canManualReorder: boolean
  onMoveUp: (id: string) => void
  onMoveDown: (id: string) => void
  onCardDrop: (targetId: string) => void
  agentOptions: string[]
}) {
  const priority = PRIORITIES.find(p => p.id === task.priority) || PRIORITIES[1]
  const cardPanelRef = useRef<HTMLDivElement | null>(null)
  const [quickEditOpen, setQuickEditOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [quickSaving, setQuickSaving] = useState(false)
  const [quickPriority, setQuickPriority] = useState<TaskPriority>(task.priority)
  const [quickAgent, setQuickAgent] = useState(task.agent_id || '')
  const [quickDepartment, setQuickDepartment] = useState(task.department || '')
  const [quickDueAt, setQuickDueAt] = useState(task.due_at ? new Date(task.due_at).toISOString().slice(0, 10) : '')

  useEffect(() => {
    setQuickPriority(task.priority)
    setQuickAgent(task.agent_id || '')
    setQuickDepartment(task.department || '')
    setQuickDueAt(task.due_at ? new Date(task.due_at).toISOString().slice(0, 10) : '')
  }, [task.id, task.priority, task.agent_id, task.department, task.due_at])

  const saveQuick = async () => {
    setQuickSaving(true)
    try {
      await onQuickUpdate(task.id, {
        priority: quickPriority,
        agent_id: quickAgent || undefined,
        department: quickDepartment || undefined,
        due_at: quickDueAt ? new Date(`${quickDueAt}T00:00:00Z`).toISOString() : undefined,
      })
      setQuickEditOpen(false)
    } finally {
      setQuickSaving(false)
    }
  }

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent) => {
      if (!cardPanelRef.current) return
      if (!cardPanelRef.current.contains(event.target as Node)) {
        setMenuOpen(false)
        setQuickEditOpen(false)
      }
    }
    if (menuOpen || quickEditOpen) {
      document.addEventListener('mousedown', handlePointerDown)
      return () => document.removeEventListener('mousedown', handlePointerDown)
    }
  }, [menuOpen, quickEditOpen])

  return (
    <div ref={cardPanelRef}
      draggable={!!onDragStart}
      onDragStart={() => onDragStart?.(task.id)}
      onDragOver={(e) => { if (canManualReorder) e.preventDefault() }}
      onDrop={(e) => {
        if (!canManualReorder) return
        e.preventDefault()
        onCardDrop(task.id)
      }}
      style={{
        borderRadius: 14,
        border: selected ? '1px solid var(--accent)' : '1px solid var(--border)',
        background: selected ? 'var(--accent-soft)' : 'var(--bg-tertiary)',
        padding: 10,
        boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
        transition: 'box-shadow 0.2s ease',
        cursor: onDragStart ? 'grab' : 'default',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
        <div style={{ display: 'flex', gap: 8, flex: 1, minWidth: 0 }}>
          <button onClick={() => onToggleSelect(task.id)} style={{ marginTop: 2, padding: 0, border: 'none', background: 'transparent', cursor: 'pointer', color: selected ? 'var(--accent)' : 'var(--text-muted)' }}>
            <CheckSquare size={16} />
          </button>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
              {canManualReorder ? <GripVertical size={14} style={{ color: 'var(--text-muted)' }} /> : null}
              <h4 style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)', margin: 0 }}>{task.title}</h4>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 5, flexWrap: 'wrap' }}>
              <span style={{ borderRadius: 6, padding: '2px 8px', fontSize: 11, fontWeight: 700, color: priority.color, background: priority.bgColor }}>{priority.label}</span>
              {task.department ? <span style={{ borderRadius: 6, padding: '2px 8px', fontSize: 11, color: 'var(--text-secondary)', background: 'white', border: '1px solid var(--border)' }}>{task.department}</span> : null}
              {task.agent_id ? <span style={{ borderRadius: 6, padding: '2px 8px', fontSize: 11, color: 'var(--text-secondary)', background: 'white', border: '1px solid var(--border)' }}>{task.agent_id}</span> : null}
            </div>
          </div>
        </div>
        <div style={{ position: 'relative' }}>
          <button onClick={() => setMenuOpen(v => !v)} style={{ padding: 4, color: 'var(--text-muted)', background: 'transparent', border: 'none', cursor: 'pointer' }}>
            <MoreHorizontal size={16} />
          </button>
          {menuOpen ? (
            <div style={{ position: 'absolute', top: 24, right: 0, zIndex: 20, minWidth: 180, background: 'white', border: '1px solid var(--border)', borderRadius: 12, boxShadow: '0 10px 24px rgba(0,0,0,0.08)', overflow: 'hidden' }}>
              <button onClick={() => { setMenuOpen(false); onEdit(task) }} style={{ width: '100%', textAlign: 'left', padding: '10px 12px', background: 'white', border: 'none', cursor: 'pointer', color: 'var(--text-primary)' }}>Open full edit</button>
              <button onClick={() => { setMenuOpen(false); setQuickEditOpen(v => !v) }} style={{ width: '100%', textAlign: 'left', padding: '10px 12px', background: 'white', border: 'none', borderTop: '1px solid var(--border-light)', cursor: 'pointer', color: 'var(--text-primary)' }}>{quickEditOpen ? 'Hide quick edit' : 'Quick edit'}</button>
              {COLUMNS.filter(col => col.id !== task.status).map(col => (
                <button key={col.id} onClick={() => { setMenuOpen(false); onMove(task.id, col.id) }} style={{ width: '100%', textAlign: 'left', padding: '10px 12px', background: 'white', border: 'none', borderTop: '1px solid var(--border-light)', cursor: 'pointer', color: 'var(--text-primary)' }}>Move to {col.label}</button>
              ))}
              <button onClick={() => { setMenuOpen(false); onDelete(task) }} style={{ width: '100%', textAlign: 'left', padding: '10px 12px', background: '#fff1f2', border: 'none', borderTop: '1px solid var(--border-light)', cursor: 'pointer', color: '#b91c1c' }}>Archive</button>
            </div>
          ) : null}
        </div>
      </div>

      {task.description && (
        <p style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical' }}>{task.description}</p>
      )}

      <div style={{ marginTop: 10, display: 'grid', gap: 6 }}>
        {task.blockers ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: '#9a3412', background: '#fff7ed', border: '1px solid #fed7aa', borderRadius: 8, padding: '5px 8px' }}>
            <AlertTriangle size={12} /> {task.blockers}
          </div>
        ) : null}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', fontSize: 11, color: 'var(--text-muted)' }}>
          {task.due_at ? <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><CalendarDays size={12} /> {formatDate(task.due_at)}</span> : null}
          {task.notion_url ? <a href={task.notion_url} target='_blank' rel='noreferrer' style={{ color: 'var(--accent)', textDecoration: 'none' }}>Open in Notion</a> : null}
          {task.source ? <span>{task.source}</span> : null}
          {task.tags.length > 0 ? <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><Tag size={12} /> {task.tags.length}</span> : null}
        </div>
      </div>

      {quickEditOpen ? (
        <div style={{ marginTop: 12, display: 'grid', gap: 8, borderTop: '1px solid var(--border)', paddingTop: 12 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0,1fr))', gap: 8 }}>
            <select value={quickPriority} onChange={(e) => setQuickPriority(e.target.value as TaskPriority)} style={{ borderRadius: 8, border: '1px solid var(--border)', padding: '8px 10px', background: 'white' }}>
              {PRIORITIES.map(p => <option key={p.id} value={p.id}>{p.label}</option>)}
            </select>
            <select value={quickAgent} onChange={(e) => setQuickAgent(e.target.value)} style={{ borderRadius: 8, border: '1px solid var(--border)', padding: '8px 10px', background: 'white' }}>
              <option value=''>Unassigned</option>
              {agentOptions.map(a => <option key={a} value={a}>{a}</option>)}
            </select>
            <select value={quickDepartment} onChange={(e) => setQuickDepartment(e.target.value)} style={{ borderRadius: 8, border: '1px solid var(--border)', padding: '8px 10px', background: 'white' }}>
              <option value=''>No department</option>
              {DEPARTMENTS.map(d => <option key={d} value={d}>{d}</option>)}
            </select>
            <input type='date' value={quickDueAt} onChange={(e) => setQuickDueAt(e.target.value)} style={{ borderRadius: 8, border: '1px solid var(--border)', padding: '8px 10px', background: 'white' }} />
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button onClick={saveQuick} disabled={quickSaving} style={{ borderRadius: 8, padding: '6px 10px', border: 'none', background: 'var(--accent)', color: 'white', cursor: 'pointer' }}>{quickSaving ? 'Saving…' : 'Save quick edit'}</button>
            <button onClick={() => setQuickEditOpen(false)} style={{ borderRadius: 8, padding: '6px 10px', border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>Cancel</button>
          </div>
        </div>
      ) : null}

      {canManualReorder ? (
        <div style={{ marginTop: 12, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <button onClick={() => onMoveUp(task.id)} style={{ borderRadius: 8, padding: '4px 10px', fontSize: 11, background: 'white', color: 'var(--text-secondary)', border: '1px solid var(--border)', cursor: 'pointer' }}>Move up</button>
          <button onClick={() => onMoveDown(task.id)} style={{ borderRadius: 8, padding: '4px 10px', fontSize: 11, background: 'white', color: 'var(--text-secondary)', border: '1px solid var(--border)', cursor: 'pointer' }}>Move down</button>
        </div>
      ) : null}
    </div>
  )
}

function TaskModal({ task, agents, onClose, onSaved }: { task?: Task | null; agents: string[]; onClose: () => void; onSaved: () => Promise<void> }) {
  const [title, setTitle] = useState(task?.title || '')
  const [description, setDescription] = useState(task?.description || '')
  const [priority, setPriority] = useState<TaskPriority>(task?.priority || 'medium')
  const [status, setStatus] = useState<TaskStatus>(task?.status || 'todo')
  const [agentId, setAgentId] = useState(task?.agent_id || '')
  const [department, setDepartment] = useState(task?.department || '')
  const [source, setSource] = useState(task?.source || 'Mission Control')
  const [blockers, setBlockers] = useState(task?.blockers || '')
  const [dueAt, setDueAt] = useState(task?.due_at ? new Date(task.due_at).toISOString().slice(0, 10) : '')
  const [saving, setSaving] = useState(false)
  const [showBlockers, setShowBlockers] = useState(!!task?.blockers)

  const handleSave = async () => {
    setSaving(true)
    try {
      const payload = {
        title,
        description,
        priority,
        status,
        agent_id: agentId || undefined,
        department: department || undefined,
        source: source || undefined,
        blockers: blockers || undefined,
        due_at: dueAt ? new Date(`${dueAt}T00:00:00Z`).toISOString() : undefined,
      }
      if (task?.id) await api.updateTask(task.id, payload as Partial<Task>)
      else await api.createTask(payload)
      await onSaved()
      onClose()
    } finally {
      setSaving(false)
    }
  }

  const fieldStyle: React.CSSProperties = {
    width: '100%',
    borderRadius: 10,
    border: '1px solid var(--border)',
    padding: '9px 12px',
    fontSize: 13,
    background: 'white',
    color: 'var(--text-primary)',
    outline: 'none',
  }

  const labelStyle: React.CSSProperties = {
    fontSize: 11,
    fontWeight: 700,
    color: 'var(--text-muted)',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 5,
  }

  return (
    <div
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.45)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16, zIndex: 1000 }}
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}
    >
      <div
        style={{
          width: 'min(920px, 100%)',
          height: 'min(700px, 90vh)',
          borderRadius: 20,
          background: 'white',
          border: '1px solid var(--border-light)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          boxShadow: '0 24px 64px rgba(0,0,0,0.18)',
        }}
      >
        {/* ── Header ── */}
        <div style={{ padding: '18px 24px 14px', borderBottom: '1px solid var(--border-light)', flexShrink: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.6 }}>{task ? 'Edit Task' : 'New Task'}</span>
            <button onClick={onClose} style={{ padding: '4px 8px', borderRadius: 8, border: 'none', background: 'transparent', cursor: 'pointer', color: 'var(--text-muted)', fontSize: 18, lineHeight: 1 }}>×</button>
          </div>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Task title"
            autoFocus
            style={{ width: '100%', borderRadius: 12, border: '1px solid var(--border)', padding: '11px 14px', fontSize: 17, fontWeight: 600, color: 'var(--text-primary)', outline: 'none', boxSizing: 'border-box' }}
          />
        </div>

        {/* ── Body: description (left) + metadata sidebar (right) ── */}
        <div style={{ display: 'flex', flex: 1, minHeight: 0, gap: 0 }}>
          {/* Left: description + blockers */}
          <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', padding: '18px 20px', gap: 12, overflow: 'hidden' }}>
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
              <span style={labelStyle}>Description / Notes</span>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Add notes, context, links, anything you need to remember…"
                style={{
                  flex: 1,
                  ...fieldStyle,
                  minHeight: 0,
                  resize: 'none',
                  padding: '12px 14px',
                  fontSize: 14,
                  lineHeight: 1.6,
                  fontFamily: 'inherit',
                }}
              />
            </div>

            {/* Blockers — collapsible, only visible when open or has content */}
            {showBlockers || blockers ? (
              <div style={{ flexShrink: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 5 }}>
                  <span style={labelStyle}>Blockers</span>
                  <button
                    onClick={() => { setShowBlockers(v => !v); if (showBlockers && !blockers) setShowBlockers(false) }}
                    style={{ fontSize: 11, color: 'var(--text-muted)', background: 'none', border: 'none', cursor: 'pointer', padding: '2px 4px' }}
                  >
                    {showBlockers ? 'hide' : 'show'}
                  </button>
                </div>
                <textarea
                  value={blockers}
                  onChange={(e) => setBlockers(e.target.value)}
                  placeholder="What's blocking progress?"
                  style={{ ...fieldStyle, minHeight: 60, resize: 'none', padding: '9px 12px', fontSize: 13 }}
                />
              </div>
            ) : (
              <button
                onClick={() => setShowBlockers(true)}
                style={{ alignSelf: 'flex-start', fontSize: 12, color: 'var(--text-muted)', background: 'none', border: 'none', cursor: 'pointer', padding: '4px 0' }}
              >
                + Add blockers
              </button>
            )}
          </div>

          {/* Divider */}
          <div style={{ width: 1, background: 'var(--border-light)', margin: '12px 0' }} />

          {/* Right: compact metadata sidebar */}
          <div style={{ width: 240, flexShrink: 0, padding: '18px 20px', display: 'flex', flexDirection: 'column', gap: 14, overflow: 'auto' }}>
            <div>
              <span style={labelStyle}>Status</span>
              <select value={status} onChange={(e) => setStatus(e.target.value as TaskStatus)} style={fieldStyle}>
                <option value='todo'>To Do</option>
                <option value='in_progress'>In Progress</option>
                <option value='done'>Done</option>
              </select>
            </div>

            <div>
              <span style={labelStyle}>Priority</span>
              <select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} style={fieldStyle}>
                {PRIORITIES.map(p => <option key={p.id} value={p.id}>{p.label}</option>)}
              </select>
            </div>

            <div>
              <span style={labelStyle}>Agent</span>
              <select value={agentId} onChange={(e) => setAgentId(e.target.value)} style={fieldStyle}>
                <option value=''>Unassigned</option>
                {agents.map(a => <option key={a} value={a}>{a}</option>)}
              </select>
            </div>

            <div>
              <span style={labelStyle}>Department</span>
              <select value={department} onChange={(e) => setDepartment(e.target.value)} style={fieldStyle}>
                <option value=''>None</option>
                {DEPARTMENTS.map(d => <option key={d} value={d}>{d}</option>)}
              </select>
            </div>

            <div>
              <span style={labelStyle}>Source</span>
              <input value={source} onChange={(e) => setSource(e.target.value)} placeholder='Source' style={fieldStyle} />
            </div>

            <div>
              <span style={labelStyle}>Due Date</span>
              <input type='date' value={dueAt} onChange={(e) => setDueAt(e.target.value)} style={fieldStyle} />
            </div>
          </div>
        </div>

        {/* ── Footer ── */}
        <div style={{ padding: '12px 24px', borderTop: '1px solid var(--border-light)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0, background: 'var(--bg-secondary)' }}>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {task ? `ID: ${task.id.slice(0, 8)}…` : ''}
          </span>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              onClick={onClose}
              style={{ borderRadius: 20, padding: '7px 18px', fontSize: 13, fontWeight: 500, border: '1px solid var(--border)', cursor: 'pointer', background: 'white', color: 'var(--text-secondary)' }}
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={!title.trim() || saving}
              style={{
                borderRadius: 20,
                padding: '7px 20px',
                fontSize: 13,
                fontWeight: 600,
                border: 'none',
                cursor: !title.trim() ? 'not-allowed' : 'pointer',
                background: 'var(--accent)',
                color: 'white',
                opacity: !title.trim() ? 0.45 : 1,
              }}
            >
              {saving ? 'Saving…' : 'Save Task'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export function Tasks() {
  const { tasks, loadTasks, moveTask, deleteTask, agents, updateTask } = useAppStore()
  const [showNewTask, setShowNewTask] = useState(false)
  const [editingTask, setEditingTask] = useState<Task | null>(null)
  const [mobileActiveColumn, setMobileActiveColumn] = useState(0)
  const [draggedTaskId, setDraggedTaskId] = useState<string | null>(null)
  const [sortBy, setSortBy] = useState<SortOption>('manual')
  const [groupBy, setGroupBy] = useState<'none' | 'agent' | 'department'>('none')
  const isMobile = useIsMobile()
  const [selectedTaskIds, setSelectedTaskIds] = useState<string[]>([])
  const [manualOrder, setManualOrder] = useState<ManualOrderMap>(() => readManualOrder())
  const [viewPresets, setViewPresets] = useState<TaskViewPreset[]>(() => readTaskViewPresets())
  const [filterAgent, setFilterAgent] = useState('')
  const [filterDepartment, setFilterDepartment] = useState('')
  const [filterPriority, setFilterPriority] = useState('')
  const [filterQuery, setFilterQuery] = useState('')



  useEffect(() => {
    loadTasks()
    const interval = window.setInterval(() => loadTasks(), 60_000)
    return () => window.clearInterval(interval)
  }, [loadTasks])

  useEffect(() => {
    writeManualOrder(manualOrder)
  }, [manualOrder])

  useEffect(() => {
    writeTaskViewPresets(viewPresets)
  }, [viewPresets])

  useEffect(() => {
    setManualOrder((prev) => {
      const next: ManualOrderMap = { ...prev }
      for (const col of COLUMNS) {
        const ids = tasks.filter(t => t.status === col.id).map(t => t.id)
        const current = next[col.id] || []
        next[col.id] = [...current.filter(id => ids.includes(id)), ...ids.filter(id => !current.includes(id))]
      }
      return next
    })
  }, [tasks])

  const agentOptions = useMemo(() => Array.from(new Set((agents || []).map(a => a.name).filter(Boolean))).sort(), [agents])
  const departmentOptions = useMemo(() => Array.from(new Set(tasks.map(t => t.department).filter(Boolean) as string[])).sort(), [tasks])

  const groupTaskBuckets = (status: TaskStatus) => {
    const statusTasks = getTasksByStatus(status)
    if (groupBy === 'none') return [{ label: '', tasks: statusTasks }]
    const map = new Map<string, Task[]>()
    for (const task of statusTasks) {
      const key = groupBy === 'agent' ? (task.agent_id || 'unassigned') : (task.department || 'No department')
      if (!map.has(key)) map.set(key, [])
      map.get(key)!.push(task)
    }
    return Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b)).map(([label, tasks]) => ({ label, tasks }))
  }

  const filteredTasks = useMemo(() => tasks.filter((t) => {
    if (filterAgent && t.agent_id !== filterAgent.toLowerCase()) return false
    if (filterDepartment && t.department !== filterDepartment) return false
    if (filterPriority && t.priority !== filterPriority) return false
    if (filterQuery) {
      const hay = `${t.title} ${t.description || ''} ${t.blockers || ''} ${t.source || ''}`.toLowerCase()
      if (!hay.includes(filterQuery.toLowerCase())) return false
    }
    return true
  }), [tasks, filterAgent, filterDepartment, filterPriority, filterQuery])

  const saveCurrentView = () => {
    const name = prompt('Name this task view')?.trim()
    if (!name) return
    const preset: TaskViewPreset = {
      id: crypto.randomUUID(),
      name,
      filterAgent,
      filterDepartment,
      filterPriority,
      filterQuery,
      sortBy,
    }
    setViewPresets((prev) => [preset, ...prev.filter((p) => p.name !== name)].slice(0, 12))
  }

  const applyPreset = (preset: TaskViewPreset) => {
    setFilterAgent(preset.filterAgent)
    setFilterDepartment(preset.filterDepartment)
    setFilterPriority(preset.filterPriority)
    setFilterQuery(preset.filterQuery)
    setSortBy(preset.sortBy)
  }

  const deletePreset = (id: string) => setViewPresets((prev) => prev.filter((p) => p.id !== id))

  const clearFilters = () => {
    setFilterAgent('')
    setFilterDepartment('')
    setFilterPriority('')
    setFilterQuery('')
  }

  const getTasksByStatus = (status: TaskStatus) => sortTasks(filteredTasks.filter(t => t.status === status), sortBy, manualOrder[status] || [])
  const activeColumn = COLUMNS[mobileActiveColumn]

  const handleMove = async (id: string, status: TaskStatus) => {
    await moveTask(id, status)
    await loadTasks()
  }

  const handleDelete = async (task: Task) => {
    if (!confirm(`Archive “${task.title}”?`)) return
    await deleteTask(task.id)
    await loadTasks()
  }

  const handleDrop = async (status: TaskStatus) => {
    if (!draggedTaskId) return
    await handleMove(draggedTaskId, status)
    setDraggedTaskId(null)
  }

  const handleCardDrop = (targetId: string) => {
    if (!draggedTaskId || draggedTaskId === targetId || sortBy !== 'manual') return
    const draggedTask = tasks.find(t => t.id === draggedTaskId)
    const targetTask = tasks.find(t => t.id === targetId)
    if (!draggedTask || !targetTask || draggedTask.status !== targetTask.status) return
    const col = draggedTask.status
    const order = [...(manualOrder[col] || [])]
    const withoutDragged = order.filter(id => id !== draggedTaskId)
    const targetIndex = withoutDragged.indexOf(targetId)
    if (targetIndex < 0) return
    withoutDragged.splice(targetIndex, 0, draggedTaskId)
    setManualOrder(prev => ({ ...prev, [col]: withoutDragged }))
    setDraggedTaskId(null)
  }

  const toggleSelect = (id: string) => {
    setSelectedTaskIds((prev) => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id])
  }

  const runBulkMove = async (status: TaskStatus) => {
    for (const id of selectedTaskIds) await api.updateTask(id, { status })
    setSelectedTaskIds([])
    await loadTasks()
  }

  const runBulkArchive = async () => {
    if (!confirm(`Archive ${selectedTaskIds.length} selected tasks?`)) return
    for (const id of selectedTaskIds) await api.deleteTask(id)
    setSelectedTaskIds([])
    await loadTasks()
  }

  const quickUpdate = async (id: string, updates: Partial<Task>) => {
    await updateTask(id, updates)
  }

  const moveWithinColumn = (taskId: string, direction: -1 | 1) => {
    const task = tasks.find(t => t.id === taskId)
    if (!task) return
    const col = task.status
    const order = [...(manualOrder[col] || [])]
    const index = order.indexOf(taskId)
    if (index < 0) return
    const nextIndex = index + direction
    if (nextIndex < 0 || nextIndex >= order.length) return
    ;[order[index], order[nextIndex]] = [order[nextIndex], order[index]]
    setManualOrder(prev => ({ ...prev, [col]: order }))
  }

  return (
    <div style={{ animation: 'fadeIn 0.4s ease-out', padding: isMobile ? 12 : 0 }}>
      <div style={{ display: 'flex', flexDirection: isMobile ? 'column' : 'row', alignItems: isMobile ? 'flex-start' : 'center', justifyContent: 'space-between', gap: 12, marginBottom: isMobile ? 24 : 24 }}>
        <div>
          <h1 style={{ fontSize: isMobile ? 24 : 28, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 6 }}>Tasks</h1>
          <p style={{ fontSize: isMobile ? 14 : 15, color: 'var(--text-secondary)', marginBottom: 8 }}>
            {filteredTasks.length} visible tasks • {filteredTasks.filter(t => t.status === 'todo').length} todo • {filteredTasks.filter(t => t.status === 'in_progress').length} in progress • {filteredTasks.filter(t => t.status === 'done').length} done
          </p>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span style={{ borderRadius: 999, padding: '4px 10px', fontSize: 12, fontWeight: 600, color: '#166534', background: '#dcfce7' }}>Synced with Notion Task Board</span>
            <a href={TASK_BOARD_URL} target='_blank' rel='noreferrer' style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 600, color: 'var(--accent)', textDecoration: 'none' }}>
              <ExternalLink size={14} /> Open in Notion
            </a>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', width: isMobile ? '100%' : 'auto' }}>
          <select value={sortBy} onChange={(e) => setSortBy(e.target.value as SortOption)} style={{ borderRadius: 12, border: '1px solid var(--border)', background: 'white', padding: '12px 14px', fontSize: 14 }}>
            <option value='manual'>Manual board order</option>
            <option value='updated_desc'>Recently Updated</option>
            <option value='priority_desc'>Priority (High → Low)</option>
            <option value='priority_asc'>Priority (Low → High)</option>
            <option value='due_asc'>Due Date</option>
            <option value='title_asc'>Title</option>
          </select>
          <select value={groupBy} onChange={(e) => setGroupBy(e.target.value as 'none' | 'agent' | 'department')} style={{ borderRadius: 12, border: '1px solid var(--border)', background: 'white', padding: '12px 14px', fontSize: 14 }}>
            <option value='none'>No grouping</option>
            <option value='agent'>Group by agent</option>
            <option value='department'>Group by department</option>
          </select>
          <button onClick={() => loadTasks()} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 16px', borderRadius: 12, background: 'white', color: 'var(--text-primary)', fontSize: 14, fontWeight: 600, border: '1px solid var(--border)', cursor: 'pointer', justifyContent: 'center' }}><RefreshCw size={16} /> Refresh</button>
          <button onClick={() => setShowNewTask(true)} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 20px', borderRadius: 12, background: 'var(--accent)', color: 'white', fontSize: 14, fontWeight: 600, border: 'none', cursor: 'pointer', justifyContent: 'center' }}><Plus size={18} /> New Task</button>
        </div>
      </div>

      <div style={{ display: 'grid', gap: 12, marginBottom: 16 }}>
        <div style={{ display: 'grid', gridTemplateColumns: isMobile ? '1fr' : '2fr repeat(3, minmax(160px, 1fr))', gap: 10 }}>
          <input value={filterQuery} onChange={(e) => setFilterQuery(e.target.value)} placeholder='Search title, notes, blockers, source…' style={{ borderRadius: 12, border: '1px solid var(--border)', background: 'white', padding: '12px 14px', fontSize: 14 }} />
          <select value={filterAgent} onChange={(e) => setFilterAgent(e.target.value)} style={{ borderRadius: 12, border: '1px solid var(--border)', background: 'white', padding: '12px 14px', fontSize: 14 }}>
            <option value=''>All agents</option>
            {agentOptions.map(a => <option key={a} value={a}>{a}</option>)}
          </select>
          <select value={filterDepartment} onChange={(e) => setFilterDepartment(e.target.value)} style={{ borderRadius: 12, border: '1px solid var(--border)', background: 'white', padding: '12px 14px', fontSize: 14 }}>
            <option value=''>All departments</option>
            {departmentOptions.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
          <select value={filterPriority} onChange={(e) => setFilterPriority(e.target.value)} style={{ borderRadius: 12, border: '1px solid var(--border)', background: 'white', padding: '12px 14px', fontSize: 14 }}>
            <option value=''>All priorities</option>
            {PRIORITIES.map(p => <option key={p.id} value={p.id}>{p.label}</option>)}
          </select>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <button onClick={saveCurrentView} style={{ borderRadius: 10, padding: '8px 12px', border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>Save current view</button>
          <button onClick={clearFilters} style={{ borderRadius: 10, padding: '8px 12px', border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>Clear filters</button>
          {viewPresets.map((preset) => (
            <div key={preset.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 0 }}>
              <button onClick={() => applyPreset(preset)} style={{ borderRadius: '10px 0 0 10px', padding: '8px 12px', border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>{preset.name}</button>
              <button onClick={() => deletePreset(preset.id)} style={{ borderRadius: '0 10px 10px 0', padding: '8px 10px', border: '1px solid var(--border)', borderLeft: 'none', background: 'white', cursor: 'pointer', color: 'var(--text-muted)' }}>×</button>
            </div>
          ))}
        </div>
        {selectedTaskIds.length > 0 ? (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', padding: 12, borderRadius: 12, background: 'white', border: '1px solid var(--border)' }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' }}>{selectedTaskIds.length} selected</span>
            <button onClick={() => runBulkMove('todo')} style={{ borderRadius: 8, padding: '6px 10px', border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>Move to To Do</button>
            <button onClick={() => runBulkMove('in_progress')} style={{ borderRadius: 8, padding: '6px 10px', border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>Move to In Progress</button>
            <button onClick={() => runBulkMove('done')} style={{ borderRadius: 8, padding: '6px 10px', border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>Move to Done</button>
            <button onClick={runBulkArchive} style={{ borderRadius: 8, padding: '6px 10px', border: '1px solid #fecdd3', background: '#fff1f2', color: '#b91c1c', cursor: 'pointer' }}>Archive selected</button>
            <button onClick={() => setSelectedTaskIds([])} style={{ borderRadius: 8, padding: '6px 10px', border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>Clear</button>
          </div>
        ) : null}
      </div>

      {showNewTask && <TaskModal agents={agentOptions} onClose={() => setShowNewTask(false)} onSaved={loadTasks} />}
      {editingTask && <TaskModal task={editingTask} agents={agentOptions} onClose={() => setEditingTask(null)} onSaved={loadTasks} />}

      {isMobile ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: 'var(--bg-secondary)', borderRadius: 16, padding: '8px 12px', border: '1px solid var(--border)' }}>
            <button onClick={() => setMobileActiveColumn(Math.max(0, mobileActiveColumn - 1))} disabled={mobileActiveColumn === 0} style={{ width: 36, height: 36, borderRadius: 10, border: '1px solid var(--border)', background: 'var(--bg-tertiary)', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: mobileActiveColumn === 0 ? 0.5 : 1, cursor: mobileActiveColumn === 0 ? 'not-allowed' : 'pointer' }}><ChevronLeft size={20} /></button>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {(() => { const Icon = activeColumn.icon; return <Icon size={20} style={{ color: 'var(--accent)' }} /> })()}
              <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{activeColumn.label}</span>
              <span style={{ padding: '2px 8px', borderRadius: 10, background: 'var(--bg-tertiary)', fontSize: 12, color: 'var(--text-muted)' }}>{getTasksByStatus(activeColumn.id).length}</span>
            </div>
            <button onClick={() => setMobileActiveColumn(Math.min(2, mobileActiveColumn + 1))} disabled={mobileActiveColumn === 2} style={{ width: 36, height: 36, borderRadius: 10, border: '1px solid var(--border)', background: 'var(--bg-tertiary)', display: 'flex', alignItems: 'center', justifyContent: 'center', opacity: mobileActiveColumn === 2 ? 0.5 : 1, cursor: mobileActiveColumn === 2 ? 'not-allowed' : 'pointer' }}><ChevronRight size={20} /></button>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, minHeight: 400, borderRadius: 16, border: '1px solid var(--border)', background: 'var(--bg-secondary)', padding: 12 }}>
            {getTasksByStatus(activeColumn.id).map((task) => <TaskCard key={task.id} task={task} onEdit={setEditingTask} onDelete={handleDelete} onMove={handleMove} selected={selectedTaskIds.includes(task.id)} onToggleSelect={toggleSelect} onQuickUpdate={quickUpdate} canManualReorder={sortBy === 'manual'} onMoveUp={(id) => moveWithinColumn(id, -1)} onMoveDown={(id) => moveWithinColumn(id, 1)} onCardDrop={handleCardDrop} agentOptions={agentOptions} />)}
            {getTasksByStatus(activeColumn.id).length === 0 && <div style={{ display: 'flex', height: 128, alignItems: 'center', justifyContent: 'center', fontSize: 14, color: 'var(--text-muted)' }}>No tasks</div>}
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 20 }}>
          {COLUMNS.map(column => (
            <div key={column.id} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderRadius: 14, background: 'var(--bg-secondary)', padding: '12px 16px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  {(() => { const Icon = column.icon; return <Icon size={16} style={{ color: 'var(--text-muted)' }} /> })()}
                  <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{column.label}</span>
                </div>
                <span style={{ borderRadius: 8, background: 'var(--bg-tertiary)', padding: '2px 10px', fontSize: 12, color: 'var(--text-muted)' }}>{getTasksByStatus(column.id).length}</span>
              </div>
              <div onDragOver={(e) => e.preventDefault()} onDrop={() => handleDrop(column.id)} style={{ display: 'flex', flexDirection: 'column', gap: 8, minHeight: 240, borderRadius: 16, border: '1px solid var(--border)', background: draggedTaskId ? 'rgba(249,115,22,0.04)' : 'var(--bg-secondary)', padding: 12 }}>
                {groupTaskBuckets(column.id).map((bucket, idx) => (
                  <div key={`${column.id}-${bucket.label || idx}`} style={{ display: 'grid', gap: 8 }}>
                    {groupBy !== 'none' ? <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4, padding: '2px 2px 0' }}>{bucket.label} · {bucket.tasks.length}</div> : null}
                    {bucket.tasks.map((task) => <TaskCard key={task.id} task={task} onEdit={setEditingTask} onDelete={handleDelete} onDragStart={setDraggedTaskId} onMove={handleMove} selected={selectedTaskIds.includes(task.id)} onToggleSelect={toggleSelect} onQuickUpdate={quickUpdate} canManualReorder={sortBy === 'manual'} onMoveUp={(id) => moveWithinColumn(id, -1)} onMoveDown={(id) => moveWithinColumn(id, 1)} onCardDrop={handleCardDrop} agentOptions={agentOptions} />)}
                  </div>
                ))}
                {getTasksByStatus(column.id).length === 0 && <div style={{ display: 'flex', height: 128, alignItems: 'center', justifyContent: 'center', fontSize: 14, color: 'var(--text-muted)' }}>Drop tasks here</div>}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
