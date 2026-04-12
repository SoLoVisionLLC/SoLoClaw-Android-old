import { useState, useEffect, useCallback } from 'react'
import {
  Plus, Search, Pin, PinOff, Trash2, Edit3, X, Check,
  ChevronDown, Tag, FileText, Bot, Shield, FolderOpen,
  BookOpen, Lightbulb, Filter,
} from 'lucide-react'
import { useAppStore } from '../hooks/use-store'
import { useIsMobile } from '../hooks/use-is-mobile'
import * as api from '../lib/api-client'
import type { Rule } from '../types'

const CATEGORIES = [
  { id: 'file-naming', label: 'File Naming', icon: FileText, color: '#3B82F6' },
  { id: 'agent-behavior', label: 'Agent Behavior', icon: Bot, color: '#8B5CF6' },
  { id: 'operational', label: 'Operational Policy', icon: Shield, color: '#EF4444' },
  { id: 'drive-org', label: 'Drive Organization', icon: FolderOpen, color: '#F59E0B' },
  { id: 'custom', label: 'Custom', icon: Lightbulb, color: '#10B981' },
]

const CATEGORY_MAP = Object.fromEntries(CATEGORIES.map(c => [c.id, c]))

function CategoryBadge({ categoryId }: { categoryId: string }) {
  const cat = CATEGORY_MAP[categoryId]
  if (!cat) return <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{categoryId}</span>
  const Icon = cat.icon
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 5, padding: '3px 10px',
      borderRadius: 20, fontSize: 11, fontWeight: 600,
      background: `${cat.color}20`, color: cat.color,
    }}>
      <Icon size={11} />
      {cat.label}
    </span>
  )
}

function RuleRow({ rule, onEdit, onDelete, onPin }: {
  rule: Rule
  onEdit: (r: Rule) => void
  onDelete: (id: string) => void
  onPin: (r: Rule) => void
}) {
  return (
    <div style={{
      padding: '14px 16px', borderRadius: 14, background: 'var(--bg-secondary)',
      border: '1px solid var(--border)', display: 'flex', flexDirection: 'column', gap: 8,
    }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span style={{
              fontSize: 15, fontWeight: 700, color: 'var(--text-primary)',
              textDecoration: rule.pinned ? 'none' : 'none',
            }}>
              {rule.pinned && <Pin size={12} style={{ marginRight: 4, color: 'var(--accent)' }} />}
              {rule.title}
            </span>
            <CategoryBadge categoryId={rule.category} />
          </div>
          <p style={{
            fontSize: 13, color: 'var(--text-muted)', marginTop: 6,
            lineHeight: 1.5, display: '-webkit-box',
            WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden',
          }}>
            {rule.content}
          </p>
          {rule.tags.length > 0 && (
            <div style={{ display: 'flex', gap: 6, marginTop: 6, flexWrap: 'wrap' }}>
              {rule.tags.map(tag => (
                <span key={tag} style={{
                  padding: '2px 8px', borderRadius: 10, fontSize: 11,
                  background: 'var(--bg-tertiary)', color: 'var(--text-muted)',
                }}>
                  #{tag}
                </span>
              ))}
            </div>
          )}
        </div>
        <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
          <button onClick={() => onPin(rule)} title={rule.pinned ? 'Unpin' : 'Pin'} style={{
            width: 32, height: 32, borderRadius: 8, border: 'none',
            background: rule.pinned ? 'var(--accent-soft)' : 'var(--bg-tertiary)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            cursor: 'pointer', color: rule.pinned ? 'var(--accent)' : 'var(--text-muted)',
          }}>
            {rule.pinned ? <PinOff size={14} /> : <Pin size={14} />}
          </button>
          <button onClick={() => onEdit(rule)} title="Edit" style={{
            width: 32, height: 32, borderRadius: 8, border: 'none',
            background: 'var(--bg-tertiary)', display: 'flex', alignItems: 'center',
            justifyContent: 'center', cursor: 'pointer', color: 'var(--text-muted)',
          }}>
            <Edit3 size={14} />
          </button>
          <button onClick={() => onDelete(rule.id)} title="Delete" style={{
            width: 32, height: 32, borderRadius: 8, border: 'none',
            background: 'var(--bg-tertiary)', display: 'flex', alignItems: 'center',
            justifyContent: 'center', cursor: 'pointer', color: 'var(--error)',
          }}>
            <Trash2 size={14} />
          </button>
        </div>
      </div>
    </div>
  )
}

function RuleEditorModal({ rule, categories, onSave, onClose }: {
  rule: Rule | null
  categories: typeof CATEGORIES
  onSave: (data: { title: string; content: string; category: string; tags: string[] }) => void
  onClose: () => void
}) {
  const [title, setTitle] = useState(rule?.title ?? '')
  const [content, setContent] = useState(rule?.content ?? '')
  const [category, setCategory] = useState(rule?.category ?? 'custom')
  const [tagInput, setTagInput] = useState(rule?.tags.join(', ') ?? '')
  const [expanded, setExpanded] = useState(false)

  const tags = tagInput.split(',').map(t => t.trim()).filter(Boolean)

  const handleSave = () => {
    if (!title.trim()) return
    onSave({ title: title.trim(), content: content.trim(), category, tags })
    onClose()
  }

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', zIndex: 200,
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
    }}>
      <div style={{
        background: 'var(--bg-secondary)', borderRadius: 20,
        border: '1px solid var(--border)', width: '100%', maxWidth: 640,
        maxHeight: '90vh', overflow: 'hidden', display: 'flex', flexDirection: 'column',
        boxShadow: '0 24px 60px rgba(0,0,0,0.3)',
      }}>
        {/* Header */}
        <div style={{
          padding: '20px 24px', borderBottom: '1px solid var(--border)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        }}>
          <span style={{ fontSize: 17, fontWeight: 700, color: 'var(--text-primary)' }}>
            {rule ? 'Edit Rule' : 'New Rule'}
          </span>
          <button onClick={onClose} style={{
            width: 32, height: 32, borderRadius: 8, border: 'none',
            background: 'var(--bg-tertiary)', display: 'flex', alignItems: 'center',
            justifyContent: 'center', cursor: 'pointer', color: 'var(--text-muted)',
          }}>
            <X size={16} />
          </button>
        </div>

        {/* Body */}
        <div style={{ padding: 24, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Title */}
          <div>
            <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
              Title *
            </label>
            <input
              value={title}
              onChange={e => setTitle(e.target.value)}
              placeholder="e.g. kebab-case for all files"
              style={{
                width: '100%', padding: '10px 14px', borderRadius: 12,
                border: '1px solid var(--border)', background: 'var(--bg-tertiary)',
                fontSize: 14, color: 'var(--text-primary)', outline: 'none',
                boxSizing: 'border-box',
              }}
            />
          </div>

          {/* Category */}
          <div>
            <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
              Category
            </label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {categories.map(cat => {
                const Icon = cat.icon
                return (
                  <button
                    key={cat.id}
                    onClick={() => setCategory(cat.id)}
                    style={{
                      padding: '6px 14px', borderRadius: 20,
                      background: category === cat.id ? `${cat.color}25` : 'var(--bg-tertiary)',
                      color: category === cat.id ? cat.color : 'var(--text-muted)',
                      fontSize: 12, fontWeight: 600, cursor: 'pointer',
                      display: 'flex', alignItems: 'center', gap: 6,
                      border: category === cat.id ? `1.5px solid ${cat.color}` : '1.5px solid transparent',
                    }}
                  >
                    <Icon size={12} />
                    {cat.label}
                  </button>
                )
              })}
            </div>
          </div>

          {/* Content */}
          <div>
            <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
              Rule Content
            </label>
            <textarea
              value={content}
              onChange={e => setContent(e.target.value)}
              placeholder="Describe the rule..."
              rows={expanded ? 12 : 5}
              style={{
                width: '100%', padding: '10px 14px', borderRadius: 12,
                border: '1px solid var(--border)', background: 'var(--bg-tertiary)',
                fontSize: 14, color: 'var(--text-primary)', outline: 'none',
                resize: 'vertical', lineHeight: 1.6, boxSizing: 'border-box',
                fontFamily: 'inherit',
              }}
            />
            <button
              onClick={() => setExpanded(!expanded)}
              style={{
                marginTop: 6, background: 'none', border: 'none', cursor: 'pointer',
                fontSize: 12, color: 'var(--text-muted)', padding: 0,
              }}
            >
              {expanded ? 'Show less' : 'Expand editor'}
            </button>
          </div>

          {/* Tags */}
          <div>
            <label style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
              Tags <span style={{ fontWeight: 400 }}>(comma-separated)</span>
            </label>
            <input
              value={tagInput}
              onChange={e => setTagInput(e.target.value)}
              placeholder="drive, naming, agents"
              style={{
                width: '100%', padding: '10px 14px', borderRadius: 12,
                border: '1px solid var(--border)', background: 'var(--bg-tertiary)',
                fontSize: 14, color: 'var(--text-primary)', outline: 'none',
                boxSizing: 'border-box',
              }}
            />
          </div>
        </div>

        {/* Footer */}
        <div style={{
          padding: '16px 24px', borderTop: '1px solid var(--border)',
          display: 'flex', gap: 10, justifyContent: 'flex-end',
        }}>
          <button onClick={onClose} style={{
            padding: '10px 20px', borderRadius: 12, border: '1px solid var(--border)',
            background: 'var(--bg-tertiary)', cursor: 'pointer',
            fontSize: 14, fontWeight: 600, color: 'var(--text-primary)',
          }}>
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={!title.trim()}
            style={{
              padding: '10px 20px', borderRadius: 12, border: 'none',
              background: title.trim() ? 'var(--accent)' : 'var(--bg-tertiary)',
              color: title.trim() ? 'white' : 'var(--text-muted)',
              fontSize: 14, fontWeight: 600, cursor: title.trim() ? 'pointer' : 'not-allowed',
              display: 'flex', alignItems: 'center', gap: 6,
            }}
          >
            <Check size={14} />
            {rule ? 'Save Changes' : 'Create Rule'}
          </button>
        </div>
      </div>
    </div>
  )
}

function DeleteConfirmModal({ ruleTitle, onConfirm, onClose }: {
  ruleTitle: string
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', zIndex: 200,
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
    }}>
      <div style={{
        background: 'var(--bg-secondary)', borderRadius: 20,
        border: '1px solid var(--border)', padding: 28,
        maxWidth: 420, width: '100%',
        boxShadow: '0 24px 60px rgba(0,0,0,0.3)',
      }}>
        <div style={{ fontSize: 20, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 10 }}>
          Delete Rule?
        </div>
        <p style={{ fontSize: 14, color: 'var(--text-muted)', marginBottom: 24, lineHeight: 1.5 }}>
          "<strong style={{ color: 'var(--text-primary)' }}>{ruleTitle}</strong>" will be permanently deleted. This cannot be undone.
        </p>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <button onClick={onClose} style={{
            padding: '10px 20px', borderRadius: 12, border: '1px solid var(--border)',
            background: 'var(--bg-tertiary)', cursor: 'pointer',
            fontSize: 14, fontWeight: 600, color: 'var(--text-primary)',
          }}>
            Cancel
          </button>
          <button onClick={onConfirm} style={{
            padding: '10px 20px', borderRadius: 12, border: 'none',
            background: 'var(--error)', color: 'white',
            fontSize: 14, fontWeight: 600, cursor: 'pointer',
          }}>
            Delete
          </button>
        </div>
      </div>
    </div>
  )
}

export function Rules() {
  const [rules, setRules] = useState<Rule[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [filterCategory, setFilterCategory] = useState<string>('all')
  const [showEditor, setShowEditor] = useState(false)
  const [editingRule, setEditingRule] = useState<Rule | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [deleteTitle, setDeleteTitle] = useState('')
  const isMobile = useIsMobile()

  const fetchRules = useCallback(async () => {
    setLoading(true)
    try {
      const data = await api.getRules()
      setRules(data)
    } catch {
      // silent
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchRules() }, [fetchRules])

  const handleSave = async (data: { title: string; content: string; category: string; tags: string[] }) => {
    if (editingRule) {
      const updated = await api.updateRule(editingRule.id, data)
      setRules(rules.map(r => r.id === editingRule.id ? updated : r))
      setEditingRule(null)
    } else {
      const rule = await api.createRule(data)
      setRules([rule, ...rules])
    }
    setShowEditor(false)
  }

  const handleDelete = async (id: string) => {
    await api.deleteRule(id)
    setRules(rules.filter(r => r.id !== id))
    setDeleteTarget(null)
  }

  const handlePin = async (rule: Rule) => {
    const updated = await api.updateRule(rule.id, { pinned: !rule.pinned })
    setRules(rules.map(r => r.id === rule.id ? updated : r))
  }

  const filtered = rules.filter(r => {
    const matchSearch = !search ||
      r.title.toLowerCase().includes(search.toLowerCase()) ||
      r.content.toLowerCase().includes(search.toLowerCase()) ||
      r.tags.some(t => t.toLowerCase().includes(search.toLowerCase()))
    const matchCat = filterCategory === 'all' || r.category === filterCategory
    return matchSearch && matchCat
  })

  const pinnedRules = filtered.filter(r => r.pinned)
  const unpinnedRules = filtered.filter(r => !r.pinned)

  return (
    <div style={{ maxWidth: 900, padding: isMobile ? '0 12px' : '0 32px', paddingBottom: 80 }}>
      {/* Header */}
      <div style={{ marginBottom: 24, marginTop: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
          <h1 style={{ fontSize: isMobile ? 24 : 28, fontWeight: 800, color: 'var(--text-primary)' }}>
            Rules
          </h1>
          <button
            onClick={() => { setEditingRule(null); setShowEditor(true) }}
            style={{
              padding: '10px 20px', borderRadius: 14, border: 'none',
              background: 'var(--accent)', color: 'white',
              fontSize: 14, fontWeight: 600, cursor: 'pointer',
              display: 'flex', alignItems: 'center', gap: 8,
              boxShadow: '0 4px 14px rgba(124, 106, 237, 0.3)',
            }}
          >
            <Plus size={16} />
            New Rule
          </button>
        </div>
        <p style={{ fontSize: 14, color: 'var(--text-secondary)' }}>
          Operating policies and conventions for all agents
        </p>
      </div>

      {/* Search + Filter Bar */}
      <div style={{
        display: 'flex', gap: 10, marginBottom: 20, flexWrap: 'wrap',
        padding: '12px 14px', background: 'var(--bg-secondary)',
        borderRadius: 16, border: '1px solid var(--border)',
      }}>
        <div style={{ flex: 1, minWidth: 180, position: 'relative' }}>
          <Search size={16} style={{
            position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)',
            color: 'var(--text-muted)',
          }} />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Search rules..."
            style={{
              width: '100%', padding: '8px 12px 8px 38px', borderRadius: 10,
              border: 'none', background: 'var(--bg-tertiary)',
              fontSize: 13, color: 'var(--text-primary)', outline: 'none', boxSizing: 'border-box',
            }}
          />
        </div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
          {[{ id: 'all', label: 'All' }, ...CATEGORIES].map(cat => {
            const isActive = filterCategory === cat.id
            return (
              <button
                key={cat.id}
                onClick={() => setFilterCategory(cat.id)}
                style={{
                  padding: '6px 14px', borderRadius: 20, border: 'none',
                  background: isActive ? 'var(--accent)' : 'var(--bg-tertiary)',
                  color: isActive ? 'white' : 'var(--text-muted)',
                  fontSize: 12, fontWeight: 600, cursor: 'pointer',
                }}
              >
                {cat.label}
              </button>
            )
          })}
        </div>
      </div>

      {/* Rules list */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)', fontSize: 14 }}>
          Loading rules...
        </div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 60, color: 'var(--text-muted)' }}>
          <BookOpen size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontSize: 16, fontWeight: 600, marginBottom: 6 }}>No rules yet</div>
          <div style={{ fontSize: 13 }}>Create your first rule to document operating policies</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {pinnedRules.length > 0 && (
            <div style={{ marginBottom: 4 }}>
              <div style={{
                fontSize: 11, fontWeight: 700, color: 'var(--accent)', textTransform: 'uppercase',
                letterSpacing: 1, marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6,
              }}>
                <Pin size={11} /> Pinned
              </div>
              {pinnedRules.map(rule => (
                <div key={rule.id} style={{ marginBottom: 8 }}>
                  <RuleRow rule={rule} onEdit={r => { setEditingRule(r); setShowEditor(true) }}
                    onDelete={id => { setDeleteTarget(id); setDeleteTitle(rules.find(r => r.id === id)?.title ?? '') }}
                    onPin={handlePin} />
                </div>
              ))}
            </div>
          )}

          {unpinnedRules.length > 0 && (
            <div>
              {pinnedRules.length > 0 && (
                <div style={{
                  fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase',
                  letterSpacing: 1, marginBottom: 8,
                }}>
                  Rules
                </div>
              )}
              {unpinnedRules.map(rule => (
                <div key={rule.id} style={{ marginBottom: 8 }}>
                  <RuleRow rule={rule} onEdit={r => { setEditingRule(r); setShowEditor(true) }}
                    onDelete={id => { setDeleteTarget(id); setDeleteTitle(rules.find(r => r.id === id)?.title ?? '') }}
                    onPin={handlePin} />
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Modals */}
      {showEditor && (
        <RuleEditorModal
          rule={editingRule}
          categories={CATEGORIES}
          onSave={handleSave}
          onClose={() => { setShowEditor(false); setEditingRule(null) }}
        />
      )}

      {deleteTarget && (
        <DeleteConfirmModal
          ruleTitle={deleteTitle}
          onConfirm={() => handleDelete(deleteTarget)}
          onClose={() => setDeleteTarget(null)}
        />
      )}
    </div>
  )
}
