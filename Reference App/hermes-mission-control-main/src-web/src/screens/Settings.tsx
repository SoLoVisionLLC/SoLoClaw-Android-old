import { useState, useEffect } from 'react'
import { Palette, Bell, Info, Check, User } from 'lucide-react'

import { useAppStore } from '../hooks/use-store'
import { useIsMobile } from '../hooks/use-is-mobile'
import { getDefaultApiBase } from '../lib/platform'
const BUILD_ID = 'mc-android-2026-04-03-0948'
const API_BASE = getDefaultApiBase() || 'relative /api'
import { useTheme } from '../components/theme-provider'
import type { Theme } from '../lib/themes'
import { ImageUpload } from '../components/ImageUpload'

function SettingCard({ icon: Icon, title, description, children }: { icon: any, title: string, description: string, children: React.ReactNode }) {
  return (
    <div style={{
      background: 'var(--bg-secondary)', borderRadius: 24, padding: 28, 
      boxShadow: '0 2px 12px rgba(0,0,0,0.06)', border: '1px solid var(--border)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 8 }}>
        <div style={{
          width: 44, height: 44, borderRadius: 14, background: 'var(--accent-soft)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent)',
        }}>
          <Icon size={22} />
        </div>
        <div>
          <h3 style={{ fontSize: 18, fontWeight: 700, color: 'var(--text-primary)' }}>{title}</h3>
          <p style={{ fontSize: 13, color: 'var(--text-muted)', marginTop: 2 }}>{description}</p>
        </div>
      </div>
      <div style={{ marginTop: 20 }}>{children}</div>
    </div>
  )
}

function ThemePreview({ theme, isActive, onClick }: { theme: Theme, isActive: boolean, onClick: () => void }) {
  const c = theme.colors
  
  return (
    <button onClick={onClick} style={{
      width: '100%', textAlign: 'left', borderRadius: 16, overflow: 'hidden',
      border: isActive ? '2px solid var(--accent)' : '2px solid transparent',
      background: c.bgSecondary, cursor: 'pointer',
      boxShadow: isActive ? '0 4px 20px rgba(0,0,0,0.15)' : '0 2px 8px rgba(0,0,0,0.08)',
      transition: 'all 0.2s ease', position: 'relative',
    }}>
      {/* Color Strip Preview */}
      <div style={{ 
        display: 'flex', height: 32,
      }}>
        <div style={{ flex: 1, background: c.bgPrimary }} />
        <div style={{ flex: 1, background: c.bgSecondary }} />
        <div style={{ flex: 1, background: c.bgTertiary }} />
        <div style={{ flex: 1, background: c.accent }} />
        <div style={{ flex: 1, background: c.success }} />
      </div>
      
      {/* Content Bar */}
      <div style={{ 
        display: 'flex', alignItems: 'center', gap: 8, padding: '12px 16px',
        background: c.bgCard, borderTop: `1px solid ${c.border}`,
      }}>
        <div style={{ 
          flex: 1, height: 8, borderRadius: 4, background: c.textPrimary, opacity: 0.2,
        }} />
        <div style={{ 
          width: 40, height: 20, borderRadius: 4, background: c.accent,
        }} />
      </div>
      
      {/* Info */}
      <div style={{ padding: '14px 16px', background: c.bgCard }}>
        <div style={{ 
          fontSize: 15, fontWeight: 700, color: c.textPrimary, marginBottom: 4,
        }}>
          {theme.name}
        </div>
        <div style={{ fontSize: 12, color: c.textMuted }}>{theme.description}</div>
      </div>
      
      {/* Checkmark */}
      {isActive && (
        <div style={{
          position: 'absolute', top: 12, right: 12,
          width: 28, height: 28, borderRadius: '50%', background: 'var(--accent)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white',
          boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
        }}>
          <Check size={16} />
        </div>
      )}
    </button>
  )
}

function Toggle({ label, defaultChecked = false }: { label: string, defaultChecked?: boolean }) {
  const [checked, setChecked] = useState(defaultChecked)
  return (
    <label style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '14px 16px', borderRadius: 16, background: 'var(--bg-tertiary)', cursor: 'pointer',
    }}>
      <span style={{ fontSize: 14, fontWeight: 500, color: 'var(--text-primary)' }}>{label}</span>
      <button onClick={() => setChecked(!checked)} style={{
        width: 48, height: 26, borderRadius: 13, border: 'none', padding: 3,
        background: checked ? 'var(--accent)' : 'var(--border)',
        display: 'flex', alignItems: 'center', cursor: 'pointer',
        transition: 'all 0.2s ease', justifyContent: checked ? 'flex-end' : 'flex-start',
      }}>
        <div style={{ width: 20, height: 20, borderRadius: '50%', background: 'white' }} />
      </button>
    </label>
  )
}

export function Settings() {
  const { config, userAvatar, userName, setUserAvatar, setUserName } = useAppStore()
  const { themes: allThemes, themeId, setTheme } = useTheme()
  const [hermesUrl, setHermesUrl] = useState(config?.hermes_url || 'http://127.0.0.1:8642')
  const [localName, setLocalName] = useState(userName || '')
  const isMobile = useIsMobile()

  useEffect(() => {
    setHermesUrl(config?.hermes_url || 'http://127.0.0.1:8642')
  }, [config?.hermes_url])

  useEffect(() => {
    setLocalName(userName || '')
  }, [userName])

  const darkThemes = allThemes.filter(t => t.category === 'dark')
  const lightThemes = allThemes.filter(t => t.category === 'light')
  const specialThemes = allThemes.filter(t => t.category === 'special')

  return (
    <div style={{ maxWidth: 900, animation: 'fadeIn 0.4s ease-out', padding: isMobile ? '0 12px' : 0 }}>
      {/* Header */}
      <div style={{ marginBottom: isMobile ? 24 : 32 }}>
        <h1 style={{ fontSize: isMobile ? 24 : 28, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 6 }}>Settings</h1>
        <p style={{ fontSize: isMobile ? 14 : 15, color: 'var(--text-secondary)' }}>Customize your Hermes experience</p>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: isMobile ? 16 : 24 }}>
        {/* User Profile */}
        <SettingCard icon={User} title="Your Profile" description="Set your name and picture for chat">
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 20 }}>
            <ImageUpload
              currentImage={userAvatar ?? undefined}
              onImageChange={(img) => setUserAvatar(img || null)}
              size={72}
              borderRadius="50%"
            />
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-muted)', marginBottom: 6, display: 'block' }}>Display Name</label>
              <input
                value={localName}
                onChange={(e) => setLocalName(e.target.value)}
                onBlur={() => setUserName(localName || null)}
                placeholder="Enter your name"
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  borderRadius: 12,
                  border: '1px solid var(--border)',
                  background: 'var(--bg-tertiary)',
                  fontSize: 15,
                  color: 'var(--text-primary)',
                  outline: 'none',
                }}
              />
            </div>
          </div>
        </SettingCard>

        {/* Appearance / Themes */}
        <SettingCard icon={Palette} title="Appearance" description="Choose your theme">
          {/* Dark Themes */}
          <div style={{ marginBottom: 24 }}>
            <h4 style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 16 }}>
              Dark Themes
            </h4>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16 }}>
              {darkThemes.map(t => (
                <ThemePreview key={t.id} theme={t} isActive={themeId === t.id} onClick={() => setTheme(t.id)} />
              ))}
            </div>
          </div>

          {/* Light Themes */}
          <div style={{ marginBottom: 24 }}>
            <h4 style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 16 }}>
              Light Themes
            </h4>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16 }}>
              {lightThemes.map(t => (
                <ThemePreview key={t.id} theme={t} isActive={themeId === t.id} onClick={() => setTheme(t.id)} />
              ))}
            </div>
          </div>

          {/* Special Themes */}
          <div>
            <h4 style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 1, marginBottom: 16 }}>
              Special Themes
            </h4>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16 }}>
              {specialThemes.map(t => (
                <ThemePreview key={t.id} theme={t} isActive={themeId === t.id} onClick={() => setTheme(t.id)} />
              ))}
            </div>
          </div>
        </SettingCard>

        {/* Connection */}
        <SettingCard icon={Info} title="API Endpoint" description="Current Mission Control API target">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <input
              type="text"
              value={hermesUrl}
              onChange={(e) => setHermesUrl(e.target.value)}
              placeholder="http://127.0.0.1:8642"
              readOnly
              style={{
                flex: 1, padding: '14px 18px', borderRadius: 16, border: '1px solid var(--border)',
                background: 'var(--bg-tertiary)', fontSize: 14, color: 'var(--text-primary)', outline: 'none', opacity: 0.8,
              }}
            />
            <p style={{ fontSize: 13, color: 'var(--text-muted)', margin: 0 }}>
              This build is using <code>{API_BASE}</code>. Connection switching is not supported in HTTP mode from this screen.
            </p>
          </div>
        </SettingCard>

        {/* Notifications */}
        <SettingCard icon={Bell} title="Notifications" description="Manage your alerts">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <Toggle label="Push notifications" defaultChecked />
            <Toggle label="Task completion alerts" defaultChecked />
            <Toggle label="Agent status changes" />
            <Toggle label="New message sounds" defaultChecked />
          </div>
        </SettingCard>

        {/* About */}
        <SettingCard icon={Info} title="About" description="App information">
          <div style={{ display: 'grid', gridTemplateColumns: isMobile ? '1fr' : 'repeat(auto-fill, minmax(140px, 1fr))', gap: 16 }}>
            {[
              { label: 'Version', value: '0.1.0' },
              { label: 'Platform', value: 'Tauri v2' },
              { label: 'Build ID', value: BUILD_ID },
              { label: 'API Base', value: API_BASE },
              { label: 'React', value: 'v18' },
            ].map(({ label, value }) => (
              <div key={label} style={{ padding: 16, borderRadius: 16, background: 'var(--bg-tertiary)' }}>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.5 }}>{label}</div>
                <div style={{ fontSize: label === 'API Base' || label === 'Build ID' ? 13 : 18, fontWeight: 700, color: 'var(--text-primary)', marginTop: 4, fontFamily: label === 'API Base' || label === 'Build ID' ? 'monospace' : 'inherit', wordBreak: 'break-all' }}>{value}</div>
              </div>
            ))}
          </div>
        </SettingCard>
      </div>
    </div>
  )
}
