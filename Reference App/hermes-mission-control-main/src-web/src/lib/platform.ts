const isTauriShell = typeof globalThis !== 'undefined' && (
  '__TAURI_INTERNALS__' in globalThis || '__TAURI__' in globalThis
)

const protocol = typeof window !== 'undefined' ? window.location.protocol : ''
const isBundledTauriApp = isTauriShell && protocol !== 'http:' && protocol !== 'https:'

export async function openExternalUrl(url: string): Promise<void> {
  if (!url) return

  if (isTauriShell) {
    try {
      const { open } = await import('@tauri-apps/plugin-shell')
      await open(url)
      return
    } catch (error) {
      console.warn('Falling back to window.open for external URL:', error)
    }
  }

  if (typeof window !== 'undefined') {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}

export function getDefaultApiBase(): string {
  const explicit = (globalThis as any).__MISSION_CONTROL_API_BASE__
  if (typeof explicit === 'string' && explicit.trim()) {
    return explicit.trim().replace(/\/$/, '')
  }

  if (isBundledTauriApp) {
    return 'https://hermes.solobot.cloud'
  }

  return ''
}

export function isTauriEnvironment(): boolean {
  return isTauriShell
}
