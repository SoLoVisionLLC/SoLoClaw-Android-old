import { useState, useRef, useEffect, useMemo } from 'react'
import { Send, Plus, Clock, ChevronDown, User, Bot, Trash2, Eye, EyeOff, Wrench } from 'lucide-react'
import { useIsMobile } from '../hooks/use-is-mobile'
import { AgentAvatar } from '../components/AgentAvatar'
import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import rehypeRaw from 'rehype-raw'
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize'
import remarkBreaks from 'remark-breaks'
import remarkGfm from 'remark-gfm'
import { useParams, useNavigate } from 'react-router-dom'
import { useAppStore } from '../hooks/use-store'
import * as api from '../lib/api-client'
import { openExternalUrl } from '../lib/platform'
import type { AgentStatus, Message, Session } from '../types'

const markdownSchema = {
  ...defaultSchema,
  tagNames: [
    ...(defaultSchema.tagNames || []),
    'details',
    'summary',
    'kbd',
    'mark',
    'sub',
    'sup',
    'del',
    'input',
    'img',
  ],
  attributes: {
    ...defaultSchema.attributes,
    a: [...(defaultSchema.attributes?.a || []), 'target', 'rel'],
    code: [...(defaultSchema.attributes?.code || []), ['className', /^language-./], ['className', 'hljs']],
    input: [
      ['type', 'checkbox'],
      'checked',
      'disabled',
    ],
    img: [
      'src',
      'alt',
      'title',
      'width',
      'height',
    ],
    '*': [...(defaultSchema.attributes?.['*'] || []), 'className'],
  },
} as const

/**
 * Filter out noisy system content from assistant messages:
 * - ASCII art banners (box-drawing chars, figlet-style decorations)
 * - Tools/functions listings
 * - Skills listings (<available_skills> blocks)
 * - MEMORY / USER PROFILE injection blocks
 */
function filterSystemNoise(text: string): string {
  let result = text

  // Remove <available_skills>...</available_skills> blocks (skills list)
  result = result.replace(/<available_skills>[\s\S]*?<\/available_skills>/g, '')

  // Remove MEMORY and USER PROFILE injection blocks
  // These look like: ══════...═══\nMEMORY (your personal notes) [...]\n══════...═══\n...content...\n══════...═══
  result = result.replace(
    /═{10,}\n(?:MEMORY|USER PROFILE)[^\n]*\n═{10,}\n[\s\S]*?(?=\n═{10,}\n|$)/g,
    ''
  )
  // Clean up any remaining separator lines from the above
  result = result.replace(/\n═{10,}\n/g, '\n')

  // Remove "## Skills (mandatory)" section that precedes <available_skills>
  result = result.replace(/## Skills \(mandatory\)[\s\S]*?(?=\n## |\n# |$)/g, '')

  // Remove tool/function definition blocks:
  // Pattern: lines starting with function signatures or tool descriptions in system prompts
  // e.g. "Here are the functions available..." blocks
  result = result.replace(/<functions>[\s\S]*?<\/functions>/g, '')
  result = result.replace(/<tools>[\s\S]*?<\/tools>/g, '')

  // Remove ASCII art banners — lines that are predominantly box-drawing characters
  // Match blocks of 3+ consecutive lines where each line is mostly special chars
  const lines = result.split('\n')
  const filtered: string[] = []
  let asciiBlockStart = -1

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    // Detect lines that are purely decorative: box-drawing, equals, dashes, pipes, etc.
    const stripped = line.replace(/[\s]/g, '')
    const isDecorative = stripped.length > 4 && 
      /^[═╔╗╚╝║━┃┏┓┗┛┣┫┳┻╋─│┌┐└┘├┤┬┴┼▀▄█▌▐░▒▓╣╠╩╦╬╥╨╞╡╢╤╧╪▪◆●○◎★☆]+$/.test(stripped)
    
    if (isDecorative) {
      if (asciiBlockStart === -1) asciiBlockStart = i
    } else {
      if (asciiBlockStart !== -1) {
        const blockLen = i - asciiBlockStart
        // Only strip if it's a substantial block (3+ lines) — preserve single hr-like lines
        if (blockLen < 3) {
          for (let j = asciiBlockStart; j < i; j++) {
            filtered.push(lines[j])
          }
        }
        asciiBlockStart = -1
      }
      filtered.push(line)
    }
  }
  // Handle trailing block
  if (asciiBlockStart !== -1) {
    const blockLen = lines.length - asciiBlockStart
    if (blockLen < 3) {
      for (let j = asciiBlockStart; j < lines.length; j++) {
        filtered.push(lines[j])
      }
    }
  }

  result = filtered.join('\n')

  // Clean up excessive blank lines left behind
  result = result.replace(/\n{4,}/g, '\n\n')
  
  return result.trim()
}

function MarkdownMessage({ content, isUser }: { content: string, isUser: boolean }) {
  if (isUser) {
    return <div style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{content}</div>
  }

  const filteredContent = useMemo(() => filterSystemNoise(content), [content])

  return (
    <div className='mission-control-markdown' style={{ overflowWrap: 'anywhere' }}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        rehypePlugins={[
          rehypeRaw,
          [rehypeSanitize, markdownSchema],
          rehypeHighlight,
        ]}
        components={{
          p: ({ children }) => <p style={{ margin: '0 0 14px 0' }}>{children}</p>,
          ul: ({ children }) => <ul style={{ margin: '0 0 14px 0', paddingLeft: 22 }}>{children}</ul>,
          ol: ({ children }) => <ol style={{ margin: '0 0 14px 0', paddingLeft: 22 }}>{children}</ol>,
          li: ({ children }) => <li style={{ marginBottom: 6 }}>{children}</li>,
          h1: ({ children }) => <h1 style={{ margin: '0 0 14px 0', fontSize: 26, fontWeight: 800, lineHeight: 1.2 }}>{children}</h1>,
          h2: ({ children }) => <h2 style={{ margin: '6px 0 14px 0', fontSize: 21, fontWeight: 750, lineHeight: 1.25 }}>{children}</h2>,
          h3: ({ children }) => <h3 style={{ margin: '6px 0 12px 0', fontSize: 17, fontWeight: 700, lineHeight: 1.3 }}>{children}</h3>,
          h4: ({ children }) => <h4 style={{ margin: '6px 0 12px 0', fontSize: 15, fontWeight: 700, lineHeight: 1.35 }}>{children}</h4>,
          h5: ({ children }) => <h5 style={{ margin: '6px 0 10px 0', fontSize: 14, fontWeight: 700, lineHeight: 1.4 }}>{children}</h5>,
          h6: ({ children }) => <h6 style={{ margin: '6px 0 10px 0', fontSize: 13, fontWeight: 700, lineHeight: 1.45, letterSpacing: '0.04em', textTransform: 'uppercase', color: 'var(--text-secondary)' }}>{children}</h6>,
          hr: () => <hr style={{ border: 0, borderTop: '1px solid var(--border)', margin: '16px 0' }} />,
          blockquote: ({ children }) => (
            <blockquote style={{ margin: '0 0 14px 0', padding: '4px 0 4px 14px', borderLeft: '3px solid var(--accent)', color: 'var(--text-secondary)', background: 'rgba(124, 106, 237, 0.05)', borderRadius: 4 }}>
              {children}
            </blockquote>
          ),
          a: ({ href, children }) => (
            <a
              href={href}
              onClick={(event) => {
                event.preventDefault()
                if (href) {
                  void openExternalUrl(href)
                }
              }}
              style={{ color: 'var(--accent)', textDecoration: 'underline', fontWeight: 500, cursor: 'pointer' }}
            >
              {children}
            </a>
          ),
          table: ({ children }) => (
            <div style={{ overflowX: 'auto', marginBottom: 14, border: '1px solid var(--border)', borderRadius: 12 }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14, background: 'rgba(255,255,255,0.75)' }}>{children}</table>
            </div>
          ),
          thead: ({ children }) => <thead style={{ background: 'rgba(124, 106, 237, 0.06)' }}>{children}</thead>,
          th: ({ children }) => (
            <th style={{ textAlign: 'left', padding: '10px 12px', borderBottom: '1px solid var(--border)', fontWeight: 700 }}>
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td style={{ padding: '10px 12px', borderBottom: '1px solid var(--border-light)', verticalAlign: 'top' }}>
              {children}
            </td>
          ),
          img: ({ src, alt, title }) => (
            <img src={src || ''} alt={alt || ''} title={title} style={{ display: 'block', maxWidth: '100%', height: 'auto', borderRadius: 12, margin: '0 0 14px 0', border: '1px solid var(--border)' }} />
          ),
          input: ({ checked, disabled, type }) => {
            if (type === 'checkbox') {
              return <input type='checkbox' checked={Boolean(checked)} disabled={disabled ?? true} style={{ marginRight: 8 }} />
            }
            return null
          },
          details: ({ children }) => (
            <details style={{ margin: '0 0 14px 0', padding: '10px 12px', background: 'rgba(124, 106, 237, 0.05)', border: '1px solid var(--border)', borderRadius: 12 }}>
              {children}
            </details>
          ),
          summary: ({ children }) => <summary style={{ cursor: 'pointer', fontWeight: 700, marginBottom: 8 }}>{children}</summary>,
          kbd: ({ children }) => (
            <kbd style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: '0.9em', background: 'rgba(17, 24, 39, 0.08)', border: '1px solid var(--border)', borderBottomWidth: 2, borderRadius: 6, padding: '1px 6px' }}>
              {children}
            </kbd>
          ),
          mark: ({ children }) => <mark style={{ background: 'rgba(251, 191, 36, 0.35)', color: 'inherit', padding: '0 2px', borderRadius: 3 }}>{children}</mark>,
          code: ({ className, children }: any) => {
            const text = String(children ?? '').replace(/\n$/, '')
            const isBlock = Boolean(className) || text.includes('\n')

            if (!isBlock) {
              return (
                <code style={{ background: 'rgba(124, 106, 237, 0.12)', borderRadius: 6, padding: '2px 6px', fontSize: '0.9em', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
                  {text}
                </code>
              )
            }

            return (
              <code className={className} style={{ display: 'block', whiteSpace: 'pre', overflowX: 'auto', background: '#111827', color: '#F9FAFB', borderRadius: 12, padding: '14px 16px', fontSize: 13, lineHeight: 1.6, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
                {text}
              </code>
            )
          },
          pre: ({ children }) => <div style={{ margin: '0 0 14px 0' }}>{children}</div>,
        }}
      >
        {filteredContent}
      </ReactMarkdown>
    </div>
  )
}

function ToolCallCard({ toolCall }: { toolCall: { id: string; name: string; arguments: Record<string, unknown>; result?: string } }) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div style={{
      marginTop: 8,
      border: '1px solid var(--border)',
      borderRadius: 8,
      background: 'rgba(124, 106, 237, 0.05)',
      overflow: 'hidden',
    }}>
      <button
        onClick={() => setExpanded(!expanded)}
        style={{
          width: '100%',
          padding: '8px 12px',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          border: 'none',
          background: 'transparent',
          cursor: 'pointer',
          fontSize: 13,
          color: 'var(--text-secondary)',
          textAlign: 'left',
        }}
      >
        <Wrench size={14} />
        <span style={{ fontWeight: 600, flex: 1 }}>{toolCall.name}</span>
        <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{expanded ? 'Hide' : 'Show'}</span>
      </button>
      {expanded && (
        <div style={{ padding: '0 12px 12px', fontSize: 12, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>
          <div style={{ color: 'var(--text-muted)', marginBottom: 4 }}>Arguments:</div>
          <pre style={{
            margin: '0 0 12px 0',
            padding: 8,
            background: 'rgba(0,0,0,0.05)',
            borderRadius: 6,
            overflow: 'auto',
            maxHeight: 200,
          }}>{JSON.stringify(toolCall.arguments, null, 2)}</pre>
          {toolCall.result && (
            <>
              <div style={{ color: 'var(--text-muted)', marginBottom: 4 }}>Result:</div>
              <pre style={{
                margin: 0,
                padding: 8,
                background: 'rgba(0,0,0,0.05)',
                borderRadius: 6,
                overflow: 'auto',
                maxHeight: 200,
              }}>{toolCall.result}</pre>
            </>
          )}
        </div>
      )}
    </div>
  )
}

function MessageBubble({ role, content, userAvatar, agentAvatar, agentName, toolCalls, showDetails }: {
  role: 'user' | 'assistant', content: string, userAvatar?: string, agentAvatar?: string, agentName?: string, toolCalls?: Array<{ id: string; name: string; arguments: Record<string, unknown>; result?: string }>, showDetails?: boolean
}) {
  const isUser = role === 'user'

  return (
    <div style={{ display: 'flex', gap: 12, flexDirection: isUser ? 'row-reverse' : 'row', alignItems: 'flex-start' }}>
      {isUser ? (
        userAvatar ? (
          <img src={userAvatar} alt="You" style={{ width: 36, height: 36, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
        ) : (
          <div style={{
            width: 36, height: 36, borderRadius: '50%', background: 'var(--accent)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', flexShrink: 0,
          }}>
            <User size={18} />
          </div>
        )
      ) : (
        <AgentAvatar size={36} avatar={agentAvatar} name={agentName} />
      )}

      <div style={{
        maxWidth: '75%', padding: '16px 20px', borderRadius: isUser ? '20px 20px 4px 20px' : '20px 20px 20px 4px',
        background: isUser ? 'var(--accent)' : 'white',
        color: isUser ? 'white' : 'var(--text-primary)',
        fontSize: 15, lineHeight: 1.6,
        boxShadow: isUser ? '0 4px 14px rgba(124, 106, 237, 0.3)' : '0 2px 12px rgba(0,0,0,0.06)',
        overflow: 'hidden',
      }}>
        <MarkdownMessage content={content} isUser={isUser} />
        {showDetails && toolCalls && toolCalls.length > 0 && (
          <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--border)' }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Tool Calls ({toolCalls.length})
            </div>
            {toolCalls.map((tc, i) => (
              <ToolCallCard key={tc.id || i} toolCall={tc} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

// Compact horizontal agent picker for mobile — shows a scrollable row of agent avatars with names
function MobileAgentBar({
  agents,
  currentAgent,
  onSelect,
}: {
  agents: AgentStatus[]
  currentAgent: AgentStatus | null
  onSelect: (agent: AgentStatus) => void
}) {
  const statusColors: Record<string, string> = {
    online: '#22c55e', working: '#3b82f6', idle: '#f59e0b', offline: '#ef4444', error: '#ef4444'
  }

  return (
    <div style={{
      display: 'flex',
      gap: 12,
      overflowX: 'auto',
      padding: '10px 16px',
      background: 'var(--bg-secondary)',
      borderBottom: '1px solid var(--border)',
      scrollbarWidth: 'none',
    }}>
      {agents.map(agent => {
        const isSelected = currentAgent?.id === agent.id
        const statusColor = statusColors[agent.state] || '#64748b'
        return (
          <div
            key={agent.id}
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 4,
              flexShrink: 0,
            }}
          >
            <button
              onClick={() => onSelect(agent)}
              title={agent.name}
              style={{
                flexShrink: 0,
                width: 44,
                height: 44,
                borderRadius: '50%',
                border: isSelected ? '2.5px solid var(--accent)' : '2.5px solid transparent',
                background: 'var(--accent-soft)',
                cursor: 'pointer',
                position: 'relative',
                transition: 'all 0.15s ease',
                padding: 0,
                outline: isSelected ? '2px solid var(--accent)' : 'none',
                outlineOffset: 2,
              }}
            >
              {agent.avatar ? (
                <img src={agent.avatar} alt={agent.name} style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }} />
              ) : (
                <div style={{
                  width: '100%',
                  height: '100%',
                  borderRadius: '50%',
                  background: agent.name ? 'linear-gradient(135deg, #7C6AED 0%, #A78BFA 50%, #C4B5FD 100%)' : 'var(--bg-tertiary)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'white',
                  fontSize: 16,
                  fontWeight: 700,
                  boxShadow: '0 4px 14px rgba(124, 106, 237, 0.4)',
                }}>
                  {agent.name ? agent.name.charAt(0).toUpperCase() : '?'}
                </div>
              )}
              {/* Status dot */}
              <div style={{
                position: 'absolute',
                bottom: 1,
                right: 1,
                width: 10,
                height: 10,
                borderRadius: '50%',
                background: statusColor,
                border: '2px solid var(--bg-secondary)',
              }} />
            </button>
            {/* Agent name label */}
            <span style={{
              fontSize: 10,
              fontWeight: isSelected ? 600 : 500,
              color: isSelected ? 'var(--accent)' : 'var(--text-secondary)',
              textAlign: 'center',
              maxWidth: 56,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              lineHeight: 1.2,
            }}>
              {agent.name}
            </span>
          </div>
        )
      })}
    </div>
  )
}

function AgentSwitcher({
  agents,
  currentAgent,
  onSelect,
}: {
  agents: AgentStatus[]
  currentAgent: AgentStatus | null
  onSelect: (agent: AgentStatus) => void
}) {
  const [isOpen, setIsOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  return (
    <div ref={dropdownRef} style={{ position: 'relative' }}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '10px 16px',
          borderRadius: 14,
          border: '2px solid var(--accent)',
          background: 'var(--accent-soft)',
          cursor: 'pointer',
          transition: 'all 0.2s ease',
        }}
      >
        <AgentAvatar size={36} name={currentAgent?.name} avatar={currentAgent?.avatar} />
        <div style={{ textAlign: 'left' }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)' }}>
            {currentAgent?.name || 'Select Agent'}
          </div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {currentAgent?.role || 'Assistant'}
          </div>
        </div>
        <ChevronDown size={18} color="var(--text-muted)" style={{ marginLeft: 4, transition: 'transform 0.2s', transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)' }} />
      </button>

      {isOpen && (
        <div style={{
          position: 'absolute',
          top: 'calc(100% + 8px)',
          left: 0,
          minWidth: 260,
          background: 'white',
          borderRadius: 16,
          boxShadow: '0 10px 40px rgba(0,0,0,0.15)',
          zIndex: 100,
          border: '1px solid var(--border)',
          overflow: 'hidden',
        }}>
          <div style={{ padding: '8px 0' }}>
            {agents.map(agent => (
              <button
                key={agent.id}
                onClick={() => { onSelect(agent); setIsOpen(false); }}
                style={{
                  width: '100%',
                  padding: '12px 16px',
                  border: 'none',
                  background: currentAgent?.id === agent.id ? 'var(--accent-soft)' : 'transparent',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  textAlign: 'left',
                  transition: 'background 0.15s ease',
                }}
              >
                <AgentAvatar size={36} name={agent.name} avatar={agent.avatar} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)' }}>
                    {agent.name}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    {agent.role || 'Assistant'}
                  </div>
                </div>
                {currentAgent?.id === agent.id && (
                  <Bot size={16} color="var(--accent)" />
                )}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export function Chat() {
  const { sessionId } = useParams()
  const navigate = useNavigate()
  const { agents, sessions, currentSession, setCurrentSession, createSession, loadRecentSessions, updateSessionName, deleteSession, currentAgent, setCurrentAgent, userAvatar, userName, showDetails, toggleShowDetails } = useAppStore()
  const [message, setMessage] = useState('')
  const [messages, setMessages] = useState<Message[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [pendingMessage, setPendingMessage] = useState<string | null>(null)
  const [showSessionDropdown, setShowSessionDropdown] = useState(false)
  const [hasAutoLoaded, setHasAutoLoaded] = useState(false)
  const agentScopedSessions = useMemo(() => {
    if (!currentAgent) return []
    return sessions.filter(s => s.agent_id === currentAgent.id)
  }, [sessions, currentAgent])
  const messagesContainerRef = useRef<HTMLDivElement>(null)
  const sessionDropdownRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  
  const isMobile = useIsMobile()

  useEffect(() => {
    if (messagesContainerRef.current) {
      messagesContainerRef.current.scrollTop = messagesContainerRef.current.scrollHeight
    }
  }, [messages])

  useEffect(() => {
    const initSessions = async () => {
      if (currentAgent && !hasAutoLoaded) {
        await loadRecentSessions(50)
        if (sessionId) {
          // Existing session: will be handled by the sessionId useEffect below
        } else {
          // No sessionId: leave page blank (user will create session on first send)
          setMessages([])
        }
        setHasAutoLoaded(true)
      }
    }
    initSessions()
  }, [currentAgent, hasAutoLoaded, sessionId, loadRecentSessions])

  useEffect(() => {
    if (sessionId) {
      const session = sessions.find(s => s.id === sessionId)
      if (session) {
        setCurrentSession(session)
        loadMessages(sessionId)
      } else if (hasAutoLoaded) {
        // Session ID in URL but not in our list (stale/deleted) - clear it
        console.log('Session not in list, clearing...')
        setCurrentSession(null)
        navigate('/chat', { replace: true })
      }
    }
  }, [sessionId, sessions, setCurrentSession, hasAutoLoaded, navigate])

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (sessionDropdownRef.current && !sessionDropdownRef.current.contains(event.target as Node)) {
        setShowSessionDropdown(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleAgentSelect = (agent: AgentStatus) => {
    setCurrentAgent(agent)
    setShowSessionDropdown(false)
    const agentSessions = sessions.filter(s => s.agent_id === agent.id)
    if (agentSessions.length > 0) {
      setCurrentSession(agentSessions[0])
      navigate(`/chat/${agentSessions[0].id}`)
    } else {
      setCurrentSession(null)
      navigate('/chat')
      setMessages([])
    }
  }

  const handleSessionSelect = (session: Session) => {
    setCurrentSession(session)
    setShowSessionDropdown(false)
    navigate(`/chat/${session.id}`)
  }

  const handleDeleteSession = async (event: React.MouseEvent, session: Session) => {
    event.stopPropagation()
    const deletingCurrent = currentSession?.id === session.id
    await deleteSession(session.id, currentAgent?.id)
    setShowSessionDropdown(false)
    if (deletingCurrent) {
      setCurrentSession(null)
      setMessages([])
      navigate('/chat', { replace: true })
    }
  }

  const generateSessionName = async (content: string, sessionId: string) => {
    const words = content.split(' ').slice(0, 6)
    let name = words.join(' ')
    if (content.split(' ').length > 6) {
      name += '...'
    }
    name = name.charAt(0).toUpperCase() + name.slice(1)
    const session = sessions.find(s => s.id === sessionId)
    if (session && (session.name.startsWith('Chat with') || session.name === 'New Chat')) {
      await updateSessionName(sessionId, name)
    }
  }

  const loadMessages = async (sid: string) => {
    try {
      const msgs = await api.getMessages(sid)
      setMessages(msgs)
    } catch (error: any) {
      console.error('Failed to load messages:', error)
      setMessages([])
      // If session not found on backend, clear it so user can create a new one
      if (error?.message?.includes('404') || error?.message?.includes('not found')) {
        console.log('Session not found on backend, clearing current session')
        setCurrentSession(null)
        navigate('/chat', { replace: true })
      }
    }
  }

  const handleSend = async () => {
    if (!message.trim() || !currentAgent) return

    const trimmedMessage = message.trim()
    setPendingMessage(trimmedMessage)
    setIsLoading(true)
    setMessage('')

    // Reset textarea height
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
    }

    try {
      // Lazy session creation: only create on first message
      let session = currentSession
      let sessionCreated = false
      if (!session) {
        session = await createSession(`Chat with ${currentAgent.name}`, currentAgent.id)
        if (!session) {
          setIsLoading(false)
          setMessage(trimmedMessage)
          return
        }
        sessionCreated = true
        // Navigate to the new session URL so it appears in the address bar
        navigate(`/chat/${session.id}`, { replace: true })
      }

      if (messages.length === 0) {
        await generateSessionName(trimmedMessage, session.id)
      }

      try {
        if (showDetails) {
          // Use streaming for real-time tool call display
          
          // 1. Add user message to local state immediately
          const tempUserMessage: Message = {
            id: `user-temp-${Date.now()}`,
            session_id: session.id,
            role: 'user',
            content: trimmedMessage,
            created_at: new Date().toISOString(),
            tool_calls: undefined,
          }
          
          // 2. Add streaming assistant message placeholder
          const streamingMessage: Message = {
            id: `streaming-${Date.now()}`,
            session_id: session.id,
            role: 'assistant',
            content: '',
            created_at: new Date().toISOString(),
            tool_calls: [],
          }
          
          setMessages(prev => [...prev, tempUserMessage, streamingMessage])
          
          let currentContent = ''
          const streamingTools: Array<{ id: string; name: string; arguments: Record<string, unknown>; result?: string }> = []
          let serverUserMessageId: string | null = null
          
          await api.sendMessageStream(
            session.id,
            trimmedMessage,
            currentAgent.id,
            (event) => {
              if (event.event === 'user_message') {
                // Server confirmed user message - update with actual server ID
                const userMsg = JSON.parse(event.data)
                serverUserMessageId = userMsg.id
                setMessages(prev => prev.map(m => 
                  m.id === tempUserMessage.id ? userMsg : m
                ))
              } else if (event.event === 'tool_call') {
                // Tool call received - add to streaming message
                const toolCall = JSON.parse(event.data)
                streamingTools.push(toolCall)
                setMessages(prev => prev.map(m => 
                  m.id === streamingMessage.id 
                    ? { ...m, tool_calls: [...streamingTools] }
                    : m
                ))
              } else if (event.event === 'content') {
                // Content chunk received
                currentContent += event.data + '\n'
                setMessages(prev => prev.map(m => 
                  m.id === streamingMessage.id 
                    ? { ...m, content: currentContent.trim() }
                    : m
                ))
              } else if (event.event === 'complete') {
                // Final message - replace with server data
                const finalMsg = JSON.parse(event.data)
                setMessages(prev => prev.map(m => 
                  m.id === streamingMessage.id ? finalMsg : m
                ))
              } else if (event.event === 'error') {
                console.error('Stream error:', event.data)
                setMessages(prev => prev.map(m => 
                  m.id === streamingMessage.id 
                    ? { ...m, content: `Error: ${event.data}` }
                    : m
                ))
              }
            },
            (error) => {
              console.error('Stream error:', error)
              setMessages(prev => prev.map(m => 
                m.id === streamingMessage.id 
                  ? { ...m, content: `Error: ${error.message}` }
                  : m
              ))
            }
          )
          await loadRecentSessions(10)
        } else {
          // Use regular non-streaming endpoint
          const response = await api.sendMessage(session.id, trimmedMessage, currentAgent.id)
          setMessages(prev => [...prev, response.user_message, response.agent_message])
          await loadRecentSessions(10)
        }
      } catch (sendError: any) {
        // If session not found on backend and we didn't just create it,
        // create a new session and retry
        if (!sessionCreated && (sendError?.message?.includes('404') || sendError?.message?.includes('not found'))) {
          console.log('Session not found on backend, creating new session and retrying...')
          setCurrentSession(null)
          session = await createSession(`Chat with ${currentAgent.name}`, currentAgent.id)
          if (session) {
            navigate(`/chat/${session.id}`, { replace: true })
            if (showDetails) {
              // Retry with streaming
              const tempUserMessage: Message = {
                id: `user-temp-${Date.now()}`,
                session_id: session.id,
                role: 'user',
                content: trimmedMessage,
                created_at: new Date().toISOString(),
                tool_calls: undefined,
              }
              const streamingMessage: Message = {
                id: `streaming-${Date.now()}`,
                session_id: session.id,
                role: 'assistant',
                content: '',
                created_at: new Date().toISOString(),
                tool_calls: [],
              }
              setMessages(prev => [...prev, tempUserMessage, streamingMessage])
              
              let currentContent = ''
              const streamingTools: Array<{ id: string; name: string; arguments: Record<string, unknown>; result?: string }> = []
              
              await api.sendMessageStream(
                session.id,
                trimmedMessage,
                currentAgent.id,
                (event) => {
                  if (event.event === 'user_message') {
                    const userMsg = JSON.parse(event.data)
                    setMessages(prev => prev.map(m => 
                      m.id === tempUserMessage.id ? userMsg : m
                    ))
                  } else if (event.event === 'tool_call') {
                    const toolCall = JSON.parse(event.data)
                    streamingTools.push(toolCall)
                    setMessages(prev => prev.map(m => 
                      m.id === streamingMessage.id 
                        ? { ...m, tool_calls: [...streamingTools] }
                        : m
                    ))
                  } else if (event.event === 'content') {
                    currentContent += event.data + '\n'
                    setMessages(prev => prev.map(m => 
                      m.id === streamingMessage.id 
                        ? { ...m, content: currentContent.trim() }
                        : m
                    ))
                  } else if (event.event === 'complete') {
                    const finalMsg = JSON.parse(event.data)
                    setMessages(prev => prev.map(m => 
                      m.id === streamingMessage.id ? finalMsg : m
                    ))
                  }
                }
              )
            } else {
              const response = await api.sendMessage(session.id, trimmedMessage, currentAgent.id)
              setMessages(prev => [...prev, response.user_message, response.agent_message])
            }
            await loadRecentSessions(10)
          }
        } else {
          throw sendError
        }
      }
    } catch (error) {
      console.error('Failed to send message:', error)
    } finally {
      setIsLoading(false)
      setPendingMessage(null)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleNewSession = async () => {
    if (!currentAgent) return
    const session = await createSession('New Chat', currentAgent.id)
    if (session) {
      navigate(`/chat/${session.id}`)
      setMessages([])
    }
  }

  // Auto-expand textarea
  const handleTextareaChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setMessage(e.target.value)
    // Auto-expand
    const textarea = e.target
    textarea.style.height = 'auto'
    textarea.style.height = Math.min(textarea.scrollHeight, 160) + 'px'
  }

  return (
    <div style={{ 
      display: 'flex', 
      flexDirection: 'column',
      height: '100%', 
      minHeight: 0,
      background: 'var(--bg-primary)' 
    }}>
      {/* Mobile: Horizontal agent strip */}
      {isMobile && (
        <MobileAgentBar
          agents={agents}
          currentAgent={currentAgent}
          onSelect={handleAgentSelect}
        />
      )}

      {/* Header with Agent Switcher (desktop), Session Selector, and New Chat */}
      <header style={{
        padding: isMobile ? '10px 12px' : '16px 24px',
        borderBottom: '1px solid var(--border)',
        background: 'white',
        display: 'flex',
        flexDirection: isMobile ? 'row' : 'row',
        alignItems: 'center',
        gap: 10,
        justifyContent: 'space-between',
        flexShrink: 0,
      }}>
        {/* Agent Switcher - Desktop only; mobile uses MobileAgentBar above */}
        {!isMobile && (
          <AgentSwitcher
            agents={agents}
            currentAgent={currentAgent}
            onSelect={handleAgentSelect}
          />
        )}

        {/* Session Controls - Right side */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1, justifyContent: 'flex-end' }}>
          {/* Session Dropdown */}
          <div ref={sessionDropdownRef} style={{ position: 'relative', maxWidth: '100%' }}>
            <button
              onClick={() => setShowSessionDropdown(v => !v)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '8px 12px',
                borderRadius: 12,
                border: '1px solid var(--border)',
                background: 'white',
                cursor: 'pointer',
                color: 'var(--text-secondary)',
                width: '100%',
                maxWidth: isMobile ? 'calc(100vw - 120px)' : 240,
              }}
            >
              <Clock size={15} />
              <span style={{
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                flex: 1,
                textAlign: 'left',
                fontSize: 13,
              }}>
                {currentSession?.name || 'Session'}
              </span>
              <ChevronDown size={14} />
            </button>

            {showSessionDropdown && (
              <div style={{
                position: 'absolute',
                right: 0,
                top: 'calc(100% + 6px)',
                width: Math.min(320, window.innerWidth - 24),
                maxHeight: 360,
                overflow: 'auto',
                background: 'white',
                border: '1px solid var(--border)',
                borderRadius: 16,
                boxShadow: '0 10px 30px rgba(0,0,0,0.12)',
                zIndex: 20
              }}>
                {agentScopedSessions.length === 0 ? (
                  <div style={{ padding: 20, textAlign: 'center', color: 'var(--text-muted)', fontSize: 14 }}>
                    No sessions yet
                  </div>
                ) : (
                  agentScopedSessions.map(session => (
                    <div
                      key={session.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        padding: '12px 16px',
                        background: currentSession?.id === session.id ? 'var(--accent-soft)' : 'transparent',
                        transition: 'background 0.15s ease',
                      }}
                    >
                      <button
                        onClick={() => handleSessionSelect(session)}
                        style={{
                          display: 'block',
                          flex: 1,
                          textAlign: 'left',
                          border: 'none',
                          background: 'transparent',
                          cursor: 'pointer',
                          padding: 0,
                        }}
                      >
                        <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)' }}>{session.name}</div>
                        <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
                          {new Date(session.updated_at).toLocaleString()}
                        </div>
                      </button>
                      <button
                        onClick={(event) => handleDeleteSession(event, session)}
                        title="Delete session"
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          width: 28,
                          height: 28,
                          border: 'none',
                          borderRadius: 8,
                          background: 'transparent',
                          color: '#ef4444',
                          cursor: 'pointer',
                          flexShrink: 0,
                        }}
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  ))
                )}
              </div>
            )}
          </div>

          {/* Show Details Toggle */}
          <button
            onClick={toggleShowDetails}
            title={showDetails ? 'Hide tool details' : 'Show tool details'}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 6,
              padding: '8px 12px',
              borderRadius: 12,
              border: showDetails ? '2px solid var(--accent)' : '1px solid var(--border)',
              background: showDetails ? 'var(--accent-soft)' : 'white',
              color: showDetails ? 'var(--accent)' : 'var(--text-secondary)',
              cursor: 'pointer',
              fontWeight: 600,
              fontSize: 13,
              transition: 'all 0.2s ease',
              flexShrink: 0,
            }}
          >
            {showDetails ? <Eye size={16} /> : <EyeOff size={16} />}
            {!isMobile && <span>{showDetails ? 'Details On' : 'Details'}</span>}
          </button>

          {/* New Chat Button */}
          <button
            onClick={handleNewSession}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 6,
              padding: '8px 12px',
              borderRadius: 12,
              border: 'none',
              background: 'var(--accent)',
              color: 'white',
              cursor: 'pointer',
              fontWeight: 600,
              fontSize: 13,
              transition: 'all 0.2s ease',
              boxShadow: '0 2px 8px rgba(124, 106, 237, 0.3)',
              flexShrink: 0,
            }}
          >
            <Plus size={16} />
            {!isMobile && <span>New Chat</span>}
          </button>
        </div>
      </header>

      {/* Messages Area */}
      <div ref={messagesContainerRef} style={{ flex: 1, overflow: 'auto', padding: isMobile ? 12 : 24, minHeight: 0 }}>
        {messages.length === 0 && !isLoading ? (
          /* Empty State */
          <div style={{
            maxWidth: 700,
            margin: '0 auto',
            paddingTop: isMobile ? 40 : 80,
            textAlign: 'center',
          }}>
            {/* Agent Avatar */}
            <div style={{ marginBottom: 24 }}>
              <AgentAvatar size={isMobile ? 64 : 80} name={currentAgent?.name} avatar={currentAgent?.avatar} />
            </div>

            {/* Title */}
            <h1 style={{
              fontSize: isMobile ? 24 : 32,
              fontWeight: 800,
              color: 'var(--text-primary)',
              margin: '0 0 12px 0',
              letterSpacing: '-0.02em',
            }}>
              {currentAgent ? `Chat with ${currentAgent.name}` : 'Welcome to Mission Control'}
            </h1>

            {/* Subtitle */}
            <p style={{
              fontSize: isMobile ? 15 : 17,
              color: 'var(--text-secondary)',
              margin: '0 0 32px 0',
              maxWidth: 480,
              marginLeft: 'auto',
              marginRight: 'auto',
              lineHeight: 1.5,
            }}>
              {currentAgent
                ? `Ask me anything — I'll help you research, plan, code, and more.`
                : 'Select an agent from the dropdown above to start chatting.'}
            </p>

            {/* Quick Start Suggestions */}
            {currentAgent && (
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 10,
                maxWidth: 400,
                margin: '0 auto',
              }}>
                {[
                  'Help me plan a new project',
                  'Research the latest AI trends',
                  'Review and optimize my code',
                  'Debug this issue with me',
                ].map((suggestion, i) => (
                  <button
                    key={i}
                    onClick={() => setMessage(suggestion)}
                    style={{
                      padding: '14px 20px',
                      borderRadius: 12,
                      border: '1px solid var(--border)',
                      background: 'white',
                      cursor: 'pointer',
                      fontSize: 14,
                      color: 'var(--text-primary)',
                      textAlign: 'left',
                      transition: 'all 0.2s ease',
                      boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
                    }}
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            )}
          </div>
        ) : (
          /* Messages */
          <div style={{
            maxWidth: 800,
            margin: '0 auto',
            display: 'flex',
            flexDirection: 'column',
            gap: isMobile ? 16 : 20
          }}>
            {messages.map(msg => (
              <MessageBubble
                key={msg.id}
                role={msg.role as 'user' | 'assistant'}
                content={msg.content}
                userAvatar={msg.role === 'user' ? (userAvatar ?? undefined) : undefined}
                agentAvatar={msg.role === 'assistant' ? (currentAgent?.avatar ?? undefined) : undefined}
                agentName={msg.role === 'assistant' ? (currentAgent?.name ?? undefined) : undefined}
                toolCalls={msg.tool_calls}
                showDetails={showDetails}
              />
            ))}
            {/* Pending message (shown immediately while sending in new chats) */}
            {isLoading && pendingMessage && (
              <MessageBubble role='user' content={pendingMessage} userAvatar={userAvatar ?? undefined} showDetails={showDetails} />
            )}
            {isLoading && (
              <MessageBubble role='assistant' content='Thinking…' agentAvatar={currentAgent?.avatar} agentName={currentAgent?.name} showDetails={showDetails} />
            )}
          </div>
        )}
      </div>

      {/* Input Area */}
      <div style={{ 
        padding: isMobile ? '12px 16px calc(12px + env(safe-area-inset-bottom, 0px))' : 20, 
        borderTop: '1px solid var(--border)', 
        background: 'white',
        flexShrink: 0,
      }}>
        <div style={{ 
          maxWidth: 800, 
          margin: '0 auto', 
          display: 'flex', 
          gap: 12,
          alignItems: 'flex-end',
        }}>
          <textarea
            ref={textareaRef}
            value={message}
            onChange={handleTextareaChange}
            onKeyDown={handleKeyDown}
            placeholder={currentAgent ? `Message ${currentAgent.name}…` : 'Select an agent to start chatting'}
            disabled={!currentAgent || isLoading}
            rows={1}
            style={{
              flex: 1, 
              resize: 'none', 
              borderRadius: 16, 
              border: '1px solid var(--border)', 
              background: 'var(--bg-primary)',
              padding: '14px 18px', 
              fontSize: 15, 
              outline: 'none',
              minHeight: 52,
              maxHeight: 160,
              lineHeight: 1.5,
              transition: 'border-color 0.2s ease',
              fontFamily: 'inherit',
            }}
          />
          <button 
            onClick={handleSend} 
            disabled={isLoading || !message.trim() || !currentAgent} 
            style={{
              width: 52, 
              height: 52, 
              borderRadius: 16, 
              border: 'none', 
              background: 'var(--accent)', 
              color: 'white',
              cursor: isLoading || !message.trim() || !currentAgent ? 'not-allowed' : 'pointer', 
              opacity: isLoading || !message.trim() || !currentAgent ? 0.5 : 1,
              display: 'flex', 
              alignItems: 'center', 
              justifyContent: 'center',
              transition: 'all 0.2s ease',
              boxShadow: '0 4px 12px rgba(124, 106, 237, 0.3)',
              flexShrink: 0,
            }}
          >
            <Send size={20} />
          </button>
        </div>
        
        {/* Hint text */}
        <div style={{ 
          maxWidth: 800, 
          margin: '8px auto 0', 
          fontSize: 12, 
          color: 'var(--text-muted)',
          textAlign: 'center',
        }}>
          Press Enter to send, Shift+Enter for new line
        </div>
      </div>
    </div>
  )
}
