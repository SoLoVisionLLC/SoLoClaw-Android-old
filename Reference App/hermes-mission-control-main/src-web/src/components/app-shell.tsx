import { useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useIsMobile } from '../hooks/use-is-mobile'
import { AgentAvatar } from './AgentAvatar'
import { 
  LayoutDashboard, 
  MessageSquare, 
  CheckSquare,
  StickyNote,
  Settings,
  Bot,
  ChevronLeft,
  ChevronRight,
  Plus,
  Clock,
  ChevronDown,
  User,
  Timer,
  Wrench,
  BookOpen,
} from 'lucide-react'
import { useAppStore } from '../hooks/use-store'
import type { AgentStatus, Session } from '../types'

const mainNavItems = [
  { path: '/', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/chat', label: 'Chat', icon: MessageSquare },
  { path: '/tasks', label: 'Tasks', icon: CheckSquare },
  { path: '/cron', label: 'Cron Jobs', icon: Timer },
  { path: '/notes', label: 'Notes', icon: StickyNote },
  { path: '/agents', label: 'Agents', icon: Bot },
  { path: '/skills', label: 'Skills', icon: Wrench },
  { path: '/settings', label: 'Settings', icon: Settings },
  { path: '/rules', label: 'Rules', icon: BookOpen },
]

function MobileNavItem({ item, isActive, onClick }: { item: any, isActive: boolean, onClick?: () => void }) {
  return (
    <NavLink 
      to={item.path}
      onClick={onClick}
      style={{ 
        textDecoration: 'none',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 4,
        padding: '8px 4px',
        borderRadius: 12,
        background: isActive ? 'var(--accent)' : 'transparent',
        color: isActive ? 'white' : 'var(--text-secondary)',
        fontSize: 11,
        fontWeight: isActive ? 600 : 500,
        transition: 'all 0.2s ease',
        cursor: 'pointer',
        flex: 1,
        minWidth: 0,
      }}
    >
      <item.icon size={22} />
      <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '100%' }}>{item.label}</span>
    </NavLink>
  )
}

function NavItem({ 
  item, 
  collapsed, 
  isActive 
}: { 
  item: any, 
  collapsed: boolean, 
  isActive: boolean 
}) {
  return (
    <NavLink 
      to={item.path} 
      style={{ 
        textDecoration: 'none',
        display: 'flex',
        alignItems: 'center',
        gap: collapsed ? 0 : 12,
        padding: collapsed ? '12px' : '12px 16px',
        borderRadius: 12,
        background: isActive ? 'var(--accent)' : 'transparent',
        color: isActive ? 'white' : 'var(--text-secondary)',
        fontSize: 14,
        fontWeight: isActive ? 600 : 500,
        transition: 'all 0.2s ease',
        cursor: 'pointer',
        justifyContent: collapsed ? 'center' : 'flex-start',
        boxShadow: isActive ? '0 4px 14px rgba(124, 106, 237, 0.4)' : 'none',
      }}
    >
      <item.icon size={20} style={{ flexShrink: 0 }} />
      {!collapsed && <span>{item.label}</span>}
    </NavLink>
  )
}

function AgentSelector({ 
  agents, 
  currentAgent, 
  onSelect, 
  collapsed 
}: { 
  agents: AgentStatus[], 
  currentAgent: AgentStatus | null, 
  onSelect: (agent: AgentStatus) => void,
  collapsed: boolean
}) {
  const [isOpen, setIsOpen] = useState(false)

  if (collapsed) {
    return (
      <div style={{ position: 'relative' }}>
        <button
          onClick={() => setIsOpen(!isOpen)}
          style={{
            width: 44,
            height: 44,
            borderRadius: 12,
            border: '2px solid var(--accent)',
            background: 'var(--bg-tertiary)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            padding: 0,
          }}
        >
          {currentAgent ? (
            <AgentAvatar size={36} name={currentAgent.name} avatar={currentAgent.avatar} />
          ) : (
            <User size={20} color="var(--accent)" />
          )}
        </button>
        {isOpen && (
          <div style={{
            position: 'absolute',
            left: '100%',
            top: 0,
            marginLeft: 8,
            background: 'white',
            borderRadius: 12,
            boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
            minWidth: 200,
            zIndex: 100,
            border: '1px solid var(--border-light)',
            padding: '8px 0',
          }}>
            {agents.map(agent => (
              <button
                key={agent.id}
                onClick={() => { onSelect(agent); setIsOpen(false); }}
                style={{
                  width: '100%',
                  padding: '10px 16px',
                  border: 'none',
                  background: 'none',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  textAlign: 'left',
                }}
              >
                <AgentAvatar size={32} name={agent.name} avatar={agent.avatar} />
                <div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)' }}>
                    {agent.name}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    {agent.state}
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    )
  }

  return (
    <div style={{ position: 'relative' }}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        style={{
          width: '100%',
          padding: '12px 16px',
          borderRadius: 12,
          border: '1px solid var(--border)',
          background: 'var(--bg-tertiary)',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          cursor: 'pointer',
        }}
      >
        <AgentAvatar size={36} name={currentAgent?.name} avatar={currentAgent?.avatar} />
        <div style={{ flex: 1, textAlign: 'left' }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)' }}>
            {currentAgent?.name || 'Select Agent'}
          </div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {currentAgent?.state || 'Offline'}
          </div>
        </div>
        <ChevronDown size={16} color="var(--text-muted)" />
      </button>

      {isOpen && (
        <div style={{
          position: 'absolute',
          top: '100%',
          left: 0,
          right: 0,
          marginTop: 8,
          background: 'white',
          borderRadius: 12,
          boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
          zIndex: 100,
          border: '1px solid var(--border-light)',
          maxHeight: 300,
          overflowY: 'auto',
        }}>
          <div style={{ padding: '8px 0' }}>
            {agents.map(agent => (
              <button
                key={agent.id}
                onClick={() => { onSelect(agent); setIsOpen(false); }}
                style={{
                  width: '100%',
                  padding: '10px 16px',
                  border: 'none',
                  background: currentAgent?.id === agent.id ? 'var(--accent-soft)' : 'none',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  textAlign: 'left',
                }}
              >
                <AgentAvatar size={32} name={agent.name} avatar={agent.avatar} />
                <div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)' }}>
                    {agent.name}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    {agent.state} • {agent.role || 'Assistant'}
                  </div>
                </div>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function SessionList({ 
  sessions, 
  currentSession, 
  onSelect, 
  onNewSession,
  collapsed,
  isVisible
}: { 
  sessions: Session[], 
  currentSession: Session | null,
  onSelect: (session: Session) => void,
  onNewSession: () => void,
  collapsed: boolean,
  isVisible: boolean
}) {
  if (!isVisible) return null
  if (collapsed) return null

  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ 
        display: 'flex', 
        alignItems: 'center', 
        justifyContent: 'space-between',
        padding: '0 4px',
        marginBottom: 8 
      }}>
        <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.5 }}>
          Recent Chats
        </span>
        <button
          onClick={onNewSession}
          style={{
            width: 24,
            height: 24,
            borderRadius: 6,
            border: 'none',
            background: 'var(--accent-soft)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            color: 'var(--accent)',
          }}
        >
          <Plus size={14} />
        </button>
      </div>
      
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {sessions.length === 0 ? (
          <div style={{ padding: '12px', color: 'var(--text-muted)', fontSize: 13, textAlign: 'center' }}>
            No chats yet
          </div>
        ) : (
          sessions.slice(0, 8).map(session => (
            <button
              key={session.id}
              onClick={() => onSelect(session)}
              style={{
                width: '100%',
                padding: '10px 12px',
                borderRadius: 10,
                border: 'none',
                background: currentSession?.id === session.id ? 'var(--accent-soft)' : 'transparent',
                cursor: 'pointer',
                textAlign: 'left',
                display: 'flex',
                alignItems: 'center',
                gap: 10,
              }}
            >
              <Clock size={14} color="var(--text-muted)" />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ 
                  fontSize: 13, 
                  fontWeight: 500, 
                  color: currentSession?.id === session.id ? 'var(--accent)' : 'var(--text-primary)',
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                }}>
                  {session.name}
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                  {session.message_count} messages
                </div>
              </div>
            </button>
          ))
        )}
      </div>
    </div>
  )
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const [collapsed, setCollapsed] = useState(false)
  const isMobile = useIsMobile()
  const { agents, currentAgent, setCurrentAgent, sessions, currentSession, setCurrentSession } = useAppStore()
  const location = useLocation()
  const navigate = useNavigate()
  const isChatPage = location.pathname.startsWith('/chat')

  const handleAgentSelect = (agent: AgentStatus) => {
    setCurrentAgent(agent)
    const agentSessions = sessions.filter(s => s.agent_id === agent.id)
    if (agentSessions.length > 0) {
      setCurrentSession(agentSessions[0])
      navigate(`/chat/${agentSessions[0].id}`)
    } else {
      setCurrentSession(null)
      navigate('/chat')
    }
  }

  const handleSessionSelect = (session: Session) => {
    setCurrentSession(session)
    navigate(`/chat/${session.id}`)
  }

  const handleNewSession = () => {
    navigate('/chat')
  }

  return (
    <div style={{ display: 'flex', flexDirection: isMobile ? 'column' : 'row', height: '100dvh', overflow: 'hidden', background: 'var(--bg-primary)' }}>
      {/* Mobile Header */}
      {isMobile && (
        <header style={{
          height: 56,
          background: 'var(--bg-secondary)',
          borderBottom: '1px solid var(--border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'flex-start',
          padding: '0 16px',
          flexShrink: 0,
          zIndex: 50,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src="/assets/mission-control-logo.png" alt="Mission Control logo" style={{ width: 36, height: 36, objectFit: 'contain', flexShrink: 0 }} />
            <div>
              <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--text-primary)' }}>Hermes</div>
              <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>Mission Control</div>
            </div>
          </div>
        </header>
      )}

      {/* Desktop Sidebar */}
      {!isMobile && (
        <aside 
          style={{
            width: isMobile ? '100%' : (collapsed ? 80 : 280),
            background: 'var(--bg-secondary)',
            borderRight: isMobile ? 'none' : '1px solid var(--border)',
            borderBottom: isMobile ? '1px solid var(--border)' : 'none',
            display: 'flex',
            flexDirection: 'column',
            transition: 'width 0.3s ease',
            padding: isMobile ? '12px 16px' : (collapsed ? '16px 12px' : '16px'),
            flexShrink: 0,
            position: 'relative',
            top: 0,
            left: 0,
            right: 0,
            bottom: 'auto',
            zIndex: 40,
            height: isMobile ? 'auto' : 'auto',
          }}
        >
          {/* Logo - Desktop only */}
          {!isMobile && (
            <div style={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: collapsed ? 0 : 10, 
              marginBottom: 24, 
              justifyContent: collapsed ? 'center' : 'flex-start',
              padding: collapsed ? 0 : '0 4px',
            }}>
              <img
                src="/assets/mission-control-logo.png"
                alt="Mission Control logo"
                style={{
                  width: collapsed ? 40 : 36,
                  height: collapsed ? 40 : 36,
                  objectFit: 'contain',
                  flexShrink: 0,
                }}
              />
              {!collapsed && (
                <div>
                  <div style={{ fontSize: 18, fontWeight: 800, color: 'var(--text-primary)' }}>Hermes</div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Mission Control</div>
                </div>
              )}
            </div>
          )}

          {/* Agent Selector */}
          <div style={{ marginBottom: 20 }}>
            <AgentSelector 
              agents={agents} 
              currentAgent={currentAgent} 
              onSelect={(agent) => { handleAgentSelect(agent) }}
              collapsed={isMobile ? false : collapsed}
            />
          </div>

          {/* Main Navigation */}
          <nav style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {mainNavItems.map((item) => (
              <NavItem 
                key={item.path} 
                item={item} 
                collapsed={isMobile ? false : collapsed}
                isActive={location.pathname === item.path || (item.path !== '/' && location.pathname.startsWith(item.path))}
              />
            ))}
          </nav>

          {/* Session List (only on chat page) */}
          <SessionList 
            sessions={currentAgent ? sessions.filter(s => s.agent_id === currentAgent.id) : []}
            currentSession={currentSession}
            onSelect={(session) => { handleSessionSelect(session) }}
            onNewSession={handleNewSession}
            collapsed={isMobile ? false : collapsed}
            isVisible={isChatPage}
          />

          {/* Bottom Section */}
          <div style={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: 4 }}>
            {!isMobile && (
              <button 
                onClick={() => setCollapsed(!collapsed)} 
                style={{
                  marginTop: 12,
                  height: 36,
                  borderRadius: 10,
                  border: '1px solid var(--border)',
                  background: 'transparent',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'var(--text-muted)',
                  cursor: 'pointer',
                  transition: 'all 0.2s ease',
                }}
              >
                {collapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
              </button>
            )}
          </div>
        </aside>
      )}

      {/* Main Content Area */}
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        marginBottom: isMobile ? 80 : 0,
      }}>
        <main style={{
          flex: 1,
          overflowY: 'auto',
          overflowX: 'hidden',
          background: 'var(--bg-primary)',
          display: 'flex',
          flexDirection: 'column',
          minHeight: 0,
          /* iOS momentum scroll */
          WebkitOverflowScrolling: 'touch',
        }}>
          {children}
        </main>
      </div>

      {/* Mobile Bottom Navigation */}
      {isMobile && (
        <nav style={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          height: 72,
          background: 'var(--bg-secondary)',
          borderTop: '1px solid var(--border)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-around',
          padding: '8px 12px',
          zIndex: 50,
          paddingBottom: 'env(safe-area-inset-bottom, 8px)',
        }}>
          {mainNavItems.map((item) => (
            <MobileNavItem 
              key={item.path} 
              item={item}
              isActive={location.pathname === item.path || (item.path !== '/' && location.pathname.startsWith(item.path))}
              onClick={() => navigate(item.path)}
            />
          ))}
        </nav>
      )}
    </div>
  )
}
