import { Bot } from 'lucide-react'

export function AgentAvatar({
  size = 40,
  name,
  avatar,
  fallback = 'bot',
}: {
  size?: number
  name?: string
  avatar?: string
  fallback?: 'bot' | 'logo'
}) {
  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        background: avatar ? 'transparent' : (name ? 'linear-gradient(135deg, #7C6AED 0%, #A78BFA 50%, #C4B5FD 100%)' : 'var(--bg-tertiary)'),
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: 'white',
        fontSize: size * 0.35,
        fontWeight: 700,
        boxShadow: name && !avatar ? '0 4px 14px rgba(124, 106, 237, 0.4)' : 'none',
        overflow: 'hidden',
        flexShrink: 0,
      }}
    >
      {avatar ? (
        <img src={avatar} alt={name ?? 'Agent'} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
      ) : name ? (
        name.charAt(0).toUpperCase()
      ) : fallback === 'logo' ? (
        <img src="/assets/mission-control-logo.png" alt="Mission Control logo" style={{ width: size * 0.72, height: size * 0.72, objectFit: 'contain' }} />
      ) : (
        <Bot size={size * 0.5} />
      )}
    </div>
  )
}
