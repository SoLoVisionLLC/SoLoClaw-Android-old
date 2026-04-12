import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Search, MoreVertical, Edit2, Trash2, Bot, ChevronDown, X, Check, RefreshCw, Users, Settings, MessageSquare, Sparkles, Folder, FolderOpen } from 'lucide-react'
import { useIsMobile } from '../hooks/use-is-mobile'
import { useAppStore } from '../hooks/use-store'
import type { AgentStatus, AgentGroup } from '../types'
import { ImageUpload } from '../components/ImageUpload'

// Provider-to-models mapping (shared with Cron.tsx)
const PROVIDER_MODELS: Record<string, string[]> = {
  'openai-codex': ['gpt-5.4', 'gpt-5.4-mini'],
  minimax: ['minimax/MiniMax-M2.7', 'MiniMaxAI/MiniMax-M2.5'],
  openrouter: ['openai/gpt-4.1', 'anthropic/claude-3.7-sonnet', 'google/gemini-2.5-pro-preview'],
  anthropic: ['claude-3-7-sonnet-latest', 'claude-3-5-sonnet-latest'],
  google: ['gemini-2.5-pro-preview-03-25', 'gemini-2.0-flash'],
}

const PROVIDER_LIST = ['openai-codex', 'minimax', 'openrouter', 'anthropic', 'google', 'copilot']

// Group Card Component
function GroupCard({ group, isActive, onClick, agentCount }: { group: AgentGroup, isActive: boolean, onClick: () => void, agentCount: number }) {
  return (
    <button onClick={onClick} style={{
      width: '100%', textAlign: 'left', padding: 16, borderRadius: 20,
      border: 'none', background: isActive ? 'var(--accent)' : 'var(--bg-secondary)',
      color: isActive ? 'white' : 'var(--text-primary)',
      cursor: 'pointer', transition: 'all 0.2s ease',
      boxShadow: isActive ? '0 4px 14px rgba(124, 106, 237, 0.4)' : '0 2px 8px rgba(0,0,0,0.06)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{
          width: 40, height: 40, borderRadius: 12,
          background: isActive ? 'rgba(255,255,255,0.2)' : (group.color || 'var(--accent-soft)'),
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: isActive ? 'white' : 'var(--accent)',
        }}>
          {isActive ? <FolderOpen size={20} /> : <Folder size={20} />}
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 15, fontWeight: 700 }}>{group.name}</div>
          <div style={{ fontSize: 12, opacity: 0.8, marginTop: 2 }}>
            {agentCount} agents • {group.description || 'No description'}
          </div>
        </div>
      </div>
    </button>
  )
}

// Agent Card Component
function AgentCard({ agent, onChat, onEdit, onDelete }: { agent: AgentStatus, onChat: () => void, onEdit: () => void, onDelete: () => void }) {
  const statusColors: Record<string, string> = {
    online: '#22c55e', working: '#3b82f6', idle: '#f59e0b', offline: '#ef4444', error: '#ef4444'
  }
  const statusColor = statusColors[agent.state] || '#64748b'
  
  return (
    <div style={{
      background: 'var(--bg-secondary)', borderRadius: 24, padding: 20,
      border: '1px solid var(--border)', boxShadow: '0 2px 12px rgba(0,0,0,0.04)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 16 }}>
        <div style={{ position: 'relative' }}>
          {agent.avatar ? (
            <img src={agent.avatar} alt={agent.name} style={{ width: 56, height: 56, borderRadius: 16, objectFit: 'cover' }} />
          ) : (
            <div style={{
              width: 56, height: 56, borderRadius: 16, background: 'var(--accent-soft)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent)',
            }}>
              <Bot size={28} />
            </div>
          )}
          <div style={{
            position: 'absolute', bottom: -2, right: -2, width: 18, height: 18, borderRadius: '50%',
            background: statusColor, border: '3px solid var(--bg-secondary)',
          }} />
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--text-primary)' }}>{agent.name}</div>
          <div style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 2 }}>
            {agent.role || 'Assistant'} • {agent.model || 'Unknown model'}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={onChat} style={{
            width: 36, height: 36, borderRadius: 10, border: 'none',
            background: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'white', cursor: 'pointer',
          }}>
            <MessageSquare size={18} />
          </button>
          <button onClick={onEdit} style={{
            width: 36, height: 36, borderRadius: 10, border: '1px solid var(--border)',
            background: 'var(--bg-tertiary)', display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'var(--text-secondary)', cursor: 'pointer',
          }}>
            <Edit2 size={18} />
          </button>
          <button onClick={onDelete} style={{
            width: 36, height: 36, borderRadius: 10, border: '1px solid var(--border)',
            background: 'var(--bg-tertiary)', display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: '#ef4444', cursor: 'pointer',
          }}>
            <Trash2 size={18} />
          </button>
        </div>
      </div>
      
      {agent.bio && (
        <div style={{ fontSize: 14, color: 'var(--text-secondary)', marginBottom: 12, lineHeight: 1.5 }}>
          {agent.bio}
        </div>
      )}
      
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
        {agent.capabilities?.map((cap) => (
          <span key={cap} style={{
            padding: '6px 12px', borderRadius: 20, fontSize: 12, fontWeight: 600,
            background: 'var(--accent-soft)', color: 'var(--accent)', textTransform: 'capitalize',
          }}>
            {cap}
          </span>
        ))}
      </div>
      
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, paddingTop: 12, borderTop: '1px solid var(--border)' }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)' }}>{agent.message_count}</div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Messages</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)' }}>{agent.tool_calls_count}</div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Tools</div>
        </div>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)' }}>{agent.sub_agents_active}</div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Sub-agents</div>
        </div>
      </div>
    </div>
  )
}

// Edit Agent Modal
function EditAgentModal({ agent, onSave, onClose }: { agent: AgentStatus, onSave: (updates: Partial<AgentStatus>) => void, onClose: () => void }) {
  const [name, setName] = useState(agent.name)
  const [bio, setBio] = useState(agent.bio || '')
  const [role, setRole] = useState(agent.role || '')
  const [capabilities, setCapabilities] = useState(agent.capabilities?.join(', ') || '')
  const [avatar, setAvatar] = useState(agent.avatar || '')
  const [provider, setProvider] = useState(agent.provider || '')
  const [model, setModel] = useState(agent.model || '')

  const availableModels = provider ? (PROVIDER_MODELS[provider] || []) : []

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center',
      zIndex: 100,
    }}>
      <div style={{
        background: 'var(--bg-secondary)', borderRadius: 24, padding: 28, width: '100%', maxWidth: 520,
        boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <h2 style={{ fontSize: 22, fontWeight: 700, color: 'var(--text-primary)' }}>Edit Agent</h2>
          <button onClick={onClose} style={{ width: 32, height: 32, borderRadius: 8, border: 'none', background: 'var(--bg-tertiary)', cursor: 'pointer' }}>
            <X size={18} />
          </button>
        </div>

        {/* Avatar + Name row */}
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 20, marginBottom: 20 }}>
          <ImageUpload
            currentImage={avatar || agent.avatar}
            onImageChange={(img) => setAvatar(img)}
            size={72}
            borderRadius={16}
          />
          <div style={{ flex: 1 }}>
            <label style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 6, display: 'block' }}>Agent Name</label>
            <input value={name} onChange={e => setName(e.target.value)} style={{
              width: '100%', padding: '10px 14px', borderRadius: 12, border: '1px solid var(--border)',
              background: 'var(--bg-tertiary)', fontSize: 15, color: 'var(--text-primary)', outline: 'none',
            }} />
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div>
            <label style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 6, display: 'block' }}>Bio</label>
            <textarea value={bio} onChange={e => setBio(e.target.value)} rows={2} style={{
              width: '100%', padding: '12px 16px', borderRadius: 12, border: '1px solid var(--border)',
              background: 'var(--bg-tertiary)', fontSize: 15, color: 'var(--text-primary)', outline: 'none', resize: 'none',
            }} />
          </div>
          <div>
            <label style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 6, display: 'block' }}>Role</label>
            <input value={role} onChange={e => setRole(e.target.value)} placeholder="e.g., researcher, coder" style={{
              width: '100%', padding: '12px 16px', borderRadius: 12, border: '1px solid var(--border)',
              background: 'var(--bg-tertiary)', fontSize: 15, color: 'var(--text-primary)', outline: 'none',
            }} />
          </div>
          <div>
            <label style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 6, display: 'block' }}>Capabilities (comma-separated)</label>
            <input value={capabilities} onChange={e => setCapabilities(e.target.value)} placeholder="chat, code, research" style={{
              width: '100%', padding: '12px 16px', borderRadius: 12, border: '1px solid var(--border)',
              background: 'var(--bg-tertiary)', fontSize: 15, color: 'var(--text-primary)', outline: 'none',
            }} />
          </div>

          {/* Provider & Model Selection */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 6, display: 'block' }}>Provider</label>
              <select
                value={provider}
                onChange={(e) => {
                  const next = e.target.value
                  setProvider(next)
                  const models = next ? (PROVIDER_MODELS[next] || []) : []
                  if (models.length > 0 && !models.includes(model)) setModel(models[0])
                  if (!next) setModel('')
                }}
                style={{
                  width: '100%', padding: '10px 14px', borderRadius: 12,
                  border: '1px solid var(--border)', background: 'var(--bg-tertiary)',
                  fontSize: 14, color: 'var(--text-primary)', outline: 'none',
                }}
              >
                <option value="">— Default —</option>
                {PROVIDER_LIST.map(p => <option key={p} value={p}>{p}</option>)}
              </select>
            </div>
            <div>
              <label style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 6, display: 'block' }}>Model</label>
              {provider && availableModels.length > 0 ? (
                <select
                  value={model}
                  onChange={e => setModel(e.target.value)}
                  style={{
                    width: '100%', padding: '10px 14px', borderRadius: 12,
                    border: '1px solid var(--border)', background: 'var(--bg-tertiary)',
                    fontSize: 14, color: 'var(--text-primary)', outline: 'none',
                  }}
                >
                  {availableModels.map(m => <option key={m} value={m}>{m}</option>)}
                </select>
              ) : (
                <input
                  value={model}
                  onChange={e => setModel(e.target.value)}
                  placeholder={provider ? 'Custom model name' : 'Select provider first'}
                  style={{
                    width: '100%', padding: '10px 14px', borderRadius: 12,
                    border: '1px solid var(--border)', background: 'var(--bg-tertiary)',
                    fontSize: 14, color: 'var(--text-primary)', outline: 'none',
                  }}
                />
              )}
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 12, marginTop: 24 }}>
          <button onClick={onClose} style={{
            flex: 1, padding: '14px', borderRadius: 12, border: '1px solid var(--border)',
            background: 'transparent', color: 'var(--text-secondary)', fontSize: 15, fontWeight: 600, cursor: 'pointer',
          }}>Cancel</button>
          <button onClick={() => {
            onSave({
              name,
              bio,
              role,
              avatar: avatar || undefined,
              capabilities: capabilities.split(',').map(s => s.trim()).filter(Boolean),
              provider: provider || undefined,
              model: model || undefined,
            })
            onClose()
          }} style={{
            flex: 1, padding: '14px', borderRadius: 12, border: 'none',
            background: 'var(--accent)', color: 'white', fontSize: 15, fontWeight: 600, cursor: 'pointer',
            boxShadow: '0 4px 14px rgba(124, 106, 237, 0.3)',
          }}>Save Changes</button>
        </div>
      </div>
    </div>
  )
}

export function Agents() {
  const navigate = useNavigate()
  const { agents, groups, currentGroup, setCurrentGroup, setCurrentAgent, updateAgentProfile, deleteGroup, createGroup, addAgentToGroup, loadAgents, loadGroups } = useAppStore()
  const [editingAgent, setEditingAgent] = useState<AgentStatus | null>(null)
  const [showNewGroup, setShowNewGroup] = useState(false)
  const [newGroupName, setNewGroupName] = useState('')
  
  const isMobile = useIsMobile()
  
  const filteredAgents = currentGroup 
    ? agents.filter(a => a.group_id === currentGroup.id)
    : agents
  
  const handleChat = async (agent: AgentStatus) => {
    // Set this as the current agent — session is created lazily on first message
    setCurrentAgent(agent)
    navigate('/chat')
  }
  
  const handleCreateGroup = () => {
    if (newGroupName.trim()) {
      createGroup(newGroupName.trim())
      setNewGroupName('')
      setShowNewGroup(false)
    }
  }
  
  return (
    <div style={{ animation: 'fadeIn 0.4s ease-out', padding: isMobile ? 12 : 0 }}>
      {/* Header */}
      <div style={{ 
        display: 'flex', 
        flexDirection: isMobile ? 'column' : 'row',
        justifyContent: 'space-between', 
        alignItems: isMobile ? 'flex-start' : 'center', 
        gap: isMobile ? 12 : 0,
        marginBottom: isMobile ? 24 : 32 
      }}>
        <div>
          <h1 style={{ fontSize: isMobile ? 24 : 28, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 6 }}>Agents</h1>
          <p style={{ fontSize: isMobile ? 14 : 15, color: 'var(--text-secondary)' }}>
            {agents.length} total • {groups.length} groups
          </p>
        </div>
        <div style={{ display: 'flex', gap: 12, width: isMobile ? '100%' : 'auto' }}>
          <button onClick={() => setShowNewGroup(true)} style={{
            padding: isMobile ? '10px 16px' : '12px 20px', 
            borderRadius: 12, border: '1px solid var(--border)',
            background: 'var(--bg-secondary)', color: 'var(--text-primary)', fontSize: 14, fontWeight: 600,
            display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', flex: isMobile ? 1 : 'none'
          }}>
            <Folder size={18} />
            New Group
          </button>
          <button style={{
            padding: isMobile ? '10px 16px' : '12px 20px', 
            borderRadius: 12, border: 'none',
            background: 'var(--accent)', color: 'white', fontSize: 14, fontWeight: 600,
            display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', flex: isMobile ? 1 : 'none'
          }}>
            <Plus size={18} />
            Add Agent
          </button>
        </div>
      </div>
      
      <div style={{ display: 'grid', gridTemplateColumns: isMobile ? '1fr' : '280px 1fr', gap: isMobile ? 16 : 24 }}>
        {/* Sidebar - Groups */}
        <div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <button onClick={() => setCurrentGroup(null)} style={{
              width: '100%', textAlign: 'left', padding: 16, borderRadius: 20,
              border: 'none', background: currentGroup === null ? 'var(--accent)' : 'var(--bg-secondary)',
              color: currentGroup === null ? 'white' : 'var(--text-primary)',
              cursor: 'pointer', transition: 'all 0.2s ease',
              boxShadow: currentGroup === null ? '0 4px 14px rgba(124, 106, 237, 0.4)' : '0 2px 8px rgba(0,0,0,0.06)',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div style={{
                  width: 40, height: 40, borderRadius: 12,
                  background: currentGroup === null ? 'rgba(255,255,255,0.2)' : 'var(--accent-soft)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  color: currentGroup === null ? 'white' : 'var(--accent)',
                }}>
                  <Users size={20} />
                </div>
                <div>
                  <div style={{ fontSize: 15, fontWeight: 700 }}>All Agents</div>
                  <div style={{ fontSize: 12, opacity: 0.8, marginTop: 2 }}>{agents.length} agents</div>
                </div>
              </div>
            </button>
            
            {groups.map((group) => (
              <GroupCard 
                key={group.id} 
                group={group} 
                isActive={currentGroup?.id === group.id}
                onClick={() => setCurrentGroup(group)}
                agentCount={agents.filter(a => a.group_id === group.id).length}
              />
            ))}
          </div>
        </div>
        
        {/* Main Content - Agent Grid */}
        <div>
          <div style={{ display: 'grid', gridTemplateColumns: isMobile ? '1fr' : 'repeat(auto-fill, minmax(320px, 1fr))', gap: isMobile ? 12 : 20 }}>
            {filteredAgents.map((agent) => (
              <AgentCard 
                key={agent.id} 
                agent={agent}
                onChat={() => handleChat(agent)}
                onEdit={() => setEditingAgent(agent)}
                onDelete={() => {/* TODO */}}
              />
            ))}
          </div>
          
          {filteredAgents.length === 0 && (
            <div style={{ 
              textAlign: 'center', padding: 60, color: 'var(--text-muted)',
              background: 'var(--bg-secondary)', borderRadius: 24, border: '1px solid var(--border)',
            }}>
              <Bot size={64} style={{ margin: '0 auto 20px', opacity: 0.5 }} />
              <p style={{ fontSize: 18, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 8 }}>No agents found</p>
              <p>{currentGroup ? 'This group is empty' : 'Connect to a Hermes instance to discover agents'}</p>
            </div>
          )}
        </div>
      </div>
      
      {/* Edit Modal */}
      {editingAgent && (
        <EditAgentModal 
          agent={editingAgent} 
          onSave={(updates) => updateAgentProfile(editingAgent.id, updates)}
          onClose={() => setEditingAgent(null)}
        />
      )}
      
      {/* New Group Modal */}
      {showNewGroup && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 100,
        }}>
          <div style={{
            background: 'var(--bg-secondary)', borderRadius: 24, padding: 28, width: '100%', maxWidth: 400,
          }}>
            <h2 style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)', marginBottom: 20 }}>Create New Group</h2>
            <input 
              value={newGroupName} 
              onChange={e => setNewGroupName(e.target.value)}
              placeholder="Group name"
              autoFocus
              style={{
                width: '100%', padding: '14px 18px', borderRadius: 12, border: '1px solid var(--border)',
                background: 'var(--bg-tertiary)', fontSize: 16, color: 'var(--text-primary)', outline: 'none',
                marginBottom: 20,
              }}
            />
            <div style={{ display: 'flex', gap: 12 }}>
              <button onClick={() => setShowNewGroup(false)} style={{
                flex: 1, padding: '14px', borderRadius: 12, border: '1px solid var(--border)',
                background: 'transparent', color: 'var(--text-secondary)', fontSize: 15, fontWeight: 600, cursor: 'pointer',
              }}>Cancel</button>
              <button onClick={handleCreateGroup} style={{
                flex: 1, padding: '14px', borderRadius: 12, border: 'none',
                background: 'var(--accent)', color: 'white', fontSize: 15, fontWeight: 600, cursor: 'pointer',
              }}>Create</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
