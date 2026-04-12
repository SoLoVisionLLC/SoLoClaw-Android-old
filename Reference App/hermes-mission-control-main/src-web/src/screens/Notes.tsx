import { useState } from 'react'
import { Plus, Pin, Trash2, Edit2, Check, X, Eye } from 'lucide-react'
import { useAppStore } from '../hooks/use-store'
import { useIsMobile } from '../hooks/use-is-mobile'
import type { Note } from '../types'

export function Notes() {
  const { notes, createNote, updateNote, deleteNote, currentAgent } = useAppStore()
  const [showNewNote, setShowNewNote] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [newContent, setNewContent] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [editContent, setEditContent] = useState('')
  
  const isMobile = useIsMobile()

  const handleCreate = async () => {
    if (!newTitle.trim()) return
    await createNote(newTitle, newContent)
    setNewTitle('')
    setNewContent('')
    setShowNewNote(false)
  }

  const handleEdit = (note: Note) => {
    setEditingId(note.id)
    setEditTitle(note.title)
    setEditContent(note.content)
  }

  const handleSaveEdit = async () => {
    if (!editingId) return
    await updateNote(editingId, { title: editTitle, content: editContent })
    setEditingId(null)
  }

  const handleDelete = async (id: string) => {
    if (confirm('Delete this note?')) {
      await deleteNote(id)
    }
  }

  const handleTogglePin = async (note: Note) => {
    await updateNote(note.id, { pinned: !note.pinned })
  }

  const handleMarkSeen = async (note: Note) => {
    await updateNote(note.id, { seen_by_agent: true })
  }

  return (
    <div style={{ animation: 'fadeIn 0.4s ease-out', padding: isMobile ? 12 : 0 }}>
      {/* Header */}
      <div style={{ 
        display: 'flex', 
        flexDirection: isMobile ? 'column' : 'row',
        alignItems: isMobile ? 'flex-start' : 'center',
        justifyContent: 'space-between',
        gap: isMobile ? 12 : 0,
        marginBottom: isMobile ? 24 : 32 
      }}>
        <div>
          <h1 style={{ fontSize: isMobile ? 24 : 28, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 6 }}>Notes</h1>
          <p style={{ fontSize: isMobile ? 14 : 15, color: 'var(--text-secondary)' }}>
            {notes.length} notes • {notes.filter(n => n.pinned).length} pinned
          </p>
        </div>
        <button
          onClick={() => setShowNewNote(true)}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: isMobile ? '10px 16px' : '12px 20px',
            borderRadius: 12,
            background: 'var(--accent)',
            color: 'white',
            fontSize: 14,
            fontWeight: 600,
            border: 'none',
            cursor: 'pointer',
            width: isMobile ? '100%' : 'auto',
            justifyContent: 'center',
          }}
        >
          <Plus size={18} />
          New Note
        </button>
      </div>

      {/* New Note Form */}
      {showNewNote && (
        <div style={{
          borderRadius: 20,
          border: '1px solid var(--border)',
          background: 'var(--bg-secondary)',
          padding: 20,
          marginBottom: 24,
        }}>
          <input
            type="text"
            value={newTitle}
            onChange={(e) => setNewTitle(e.target.value)}
            placeholder="Note title..."
            autoFocus
            style={{
              width: '100%',
              background: 'transparent',
              border: 'none',
              fontSize: 18,
              fontWeight: 600,
              color: 'var(--text-primary)',
              outline: 'none',
            }}
          />
          <textarea
            value={newContent}
            onChange={(e) => setNewContent(e.target.value)}
            placeholder="Write your note here..."
            rows={4}
            style={{
              marginTop: 12,
              width: '100%',
              resize: 'none',
              background: 'transparent',
              border: 'none',
              fontSize: 14,
              color: 'var(--text-primary)',
              outline: 'none',
            }}
          />
          <div style={{ marginTop: 12, display: 'flex', gap: 10 }}>
            <button
              onClick={handleCreate}
              disabled={!newTitle.trim()}
              style={{
                borderRadius: 10,
                padding: '10px 18px',
                fontSize: 14,
                fontWeight: 600,
                border: 'none',
                cursor: !newTitle.trim() ? 'not-allowed' : 'pointer',
                background: 'var(--accent)',
                color: 'white',
                opacity: !newTitle.trim() ? 0.5 : 1,
              }}
            >
              Create Note
            </button>
            <button
              onClick={() => setShowNewNote(false)}
              style={{
                borderRadius: 10,
                padding: '10px 18px',
                fontSize: 14,
                fontWeight: 600,
                border: '1px solid var(--border)',
                cursor: 'pointer',
                background: 'transparent',
                color: 'var(--text-secondary)',
              }}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Notes Grid */}
      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: isMobile ? '1fr' : 'repeat(auto-fill, minmax(320px, 1fr))', 
        gap: 16 
      }}>
        {notes.map(note => (
          <div
            key={note.id}
            style={{
              borderRadius: 16,
              border: note.pinned ? '1px solid rgba(124, 106, 237, 0.3)' : '1px solid var(--border)',
              background: note.pinned ? 'rgba(124, 106, 237, 0.05)' : 'var(--bg-secondary)',
              padding: 16,
              transition: 'all 0.2s ease',
            }}
          >
            {editingId === note.id ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <input
                  type="text"
                  value={editTitle}
                  onChange={(e) => setEditTitle(e.target.value)}
                  style={{
                    width: '100%',
                    background: 'transparent',
                    border: 'none',
                    fontSize: 18,
                    fontWeight: 600,
                    color: 'var(--text-primary)',
                    outline: 'none',
                  }}
                />
                <textarea
                  value={editContent}
                  onChange={(e) => setEditContent(e.target.value)}
                  rows={4}
                  style={{
                    width: '100%',
                    resize: 'none',
                    background: 'transparent',
                    border: 'none',
                    fontSize: 14,
                    color: 'var(--text-primary)',
                    outline: 'none',
                  }}
                />
                <div style={{ display: 'flex', gap: 8 }}>
                  <button onClick={handleSaveEdit} style={{ padding: 4, color: 'var(--accent)', background: 'transparent', border: 'none', cursor: 'pointer' }}>
                    <Check size={16} />
                  </button>
                  <button onClick={() => setEditingId(null)} style={{ padding: 4, color: 'var(--text-muted)', background: 'transparent', border: 'none', cursor: 'pointer' }}>
                    <X size={16} />
                  </button>
                </div>
              </div>
            ) : (
              <>
                <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
                  <h3 style={{ fontWeight: 600, color: 'var(--text-primary)', flex: 1 }}>{note.title}</h3>
                  <div style={{ display: 'flex', gap: 4 }}>
                    <button
                      onClick={() => handleTogglePin(note)}
                      style={{
                        padding: 4,
                        borderRadius: 6,
                        color: note.pinned ? 'var(--accent)' : 'var(--text-muted)',
                        background: 'transparent',
                        border: 'none',
                        cursor: 'pointer',
                      }}
                    >
                      <Pin size={16} />
                    </button>
                    <button
                      onClick={() => handleEdit(note)}
                      style={{ padding: 4, color: 'var(--text-muted)', background: 'transparent', border: 'none', cursor: 'pointer' }}
                    >
                      <Edit2 size={16} />
                    </button>
                    <button
                      onClick={() => handleDelete(note.id)}
                      style={{ padding: 4, color: 'var(--text-muted)', background: 'transparent', border: 'none', cursor: 'pointer' }}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
                
                <p style={{ marginTop: 8, fontSize: 14, color: 'var(--text-secondary)', whiteSpace: 'pre-wrap' }}>
                  {note.content}
                </p>
                
                <div style={{ marginTop: 16, display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 12 }}>
                  <span style={{ color: 'var(--text-muted)' }}>
                    {new Date(note.updated_at).toLocaleDateString()}
                  </span>
                  
                  {currentAgent && !note.seen_by_agent && (
                    <button
                      onClick={() => handleMarkSeen(note)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 4,
                        padding: '4px 10px',
                        borderRadius: 8,
                        background: 'rgba(124, 106, 237, 0.1)',
                        color: 'var(--accent)',
                        border: 'none',
                        cursor: 'pointer',
                        fontSize: 12,
                        fontWeight: 500,
                      }}
                    >
                      <Eye size={12} />
                      Mark seen
                    </button>
                  )}
                  
                  {note.seen_by_agent && (
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#22c55e' }}>
                      <Check size={12} />
                      Seen
                    </span>
                  )}
                </div>
              </>
            )}
          </div>
        ))}
        
        {notes.length === 0 && (
          <div style={{
            gridColumn: '1 / -1',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: 20,
            border: '1px solid var(--border)',
            background: 'var(--bg-secondary)',
            padding: 48,
            textAlign: 'center',
          }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 64,
              height: 64,
              borderRadius: '50%',
              background: 'var(--bg-tertiary)',
            }}>
              <Plus size={32} style={{ color: 'var(--text-muted)' }} />
            </div>
            <p style={{ marginTop: 16, fontSize: 18, fontWeight: 600, color: 'var(--text-primary)' }}>No notes yet</p>
            <p style={{ fontSize: 14, color: 'var(--text-muted)' }}>Create your first note to get started</p>
          </div>
        )}
      </div>
    </div>
  )
}
