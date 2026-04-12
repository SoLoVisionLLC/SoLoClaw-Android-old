export interface Theme {
  id: string
  name: string
  description: string
  category: 'dark' | 'light' | 'special'
  colors: {
    bgPrimary: string
    bgSecondary: string
    bgTertiary: string
    bgCard: string
    textPrimary: string
    textSecondary: string
    textMuted: string
    accent: string
    accentLight: string
    accentSoft: string
    border: string
    borderLight: string
    success: string
    warning: string
    error: string
    info: string
  }
}

export const themes: Theme[] = [
  // DARK THEMES
  {
    id: 'midnight',
    name: 'Midnight',
    description: 'Deep slate grays',
    category: 'dark',
    colors: {
      bgPrimary: '#0f1115',
      bgSecondary: '#161922',
      bgTertiary: '#1e212b',
      bgCard: '#252a36',
      textPrimary: '#f0f1f5',
      textSecondary: '#a0a3b1',
      textMuted: '#5a5e6e',
      accent: '#60a5fa',
      accentLight: '#93c5fd',
      accentSoft: 'rgba(96, 165, 250, 0.15)',
      border: '#2a2e3a',
      borderLight: '#3a3f4d',
      success: '#4ade80',
      warning: '#fbbf24',
      error: '#fb7185',
      info: '#60a5fa',
    },
  },
  {
    id: 'obsidian',
    name: 'Obsidian',
    description: 'Pure black elegance',
    category: 'dark',
    colors: {
      bgPrimary: '#000000',
      bgSecondary: '#0a0a0a',
      bgTertiary: '#141414',
      bgCard: '#1a1a1a',
      textPrimary: '#ffffff',
      textSecondary: '#a3a3a3',
      textMuted: '#737373',
      accent: '#ffffff',
      accentLight: '#e5e5e5',
      accentSoft: 'rgba(255, 255, 255, 0.1)',
      border: '#262626',
      borderLight: '#404040',
      success: '#22c55e',
      warning: '#f59e0b',
      error: '#ef4444',
      info: '#3b82f6',
    },
  },
  {
    id: 'nord',
    name: 'Nord',
    description: 'Nordic blue-gray',
    category: 'dark',
    colors: {
      bgPrimary: '#2e3440',
      bgSecondary: '#3b4252',
      bgTertiary: '#434c5e',
      bgCard: '#4c566a',
      textPrimary: '#eceff4',
      textSecondary: '#d8dee9',
      textMuted: '#81a1c1',
      accent: '#88c0d0',
      accentLight: '#8fbcbb',
      accentSoft: 'rgba(136, 192, 208, 0.2)',
      border: '#434c5e',
      borderLight: '#4c566a',
      success: '#a3be8c',
      warning: '#ebcb8b',
      error: '#bf616a',
      info: '#5e81ac',
    },
  },
  {
    id: 'dracula',
    name: 'Dracula',
    description: 'Purple & pink',
    category: 'dark',
    colors: {
      bgPrimary: '#282a36',
      bgSecondary: '#44475a',
      bgTertiary: '#6272a4',
      bgCard: '#44475a',
      textPrimary: '#f8f8f2',
      textSecondary: '#bd93f9',
      textMuted: '#6272a4',
      accent: '#ff79c6',
      accentLight: '#ffb86c',
      accentSoft: 'rgba(255, 121, 198, 0.2)',
      border: '#44475a',
      borderLight: '#6272a4',
      success: '#50fa7b',
      warning: '#f1fa8c',
      error: '#ff5555',
      info: '#8be9fd',
    },
  },
  {
    id: 'tokyo-night',
    name: 'Tokyo Night',
    description: 'Neon blue accents',
    category: 'dark',
    colors: {
      bgPrimary: '#1a1b26',
      bgSecondary: '#24283b',
      bgTertiary: '#2a2f45',
      bgCard: '#343a52',
      textPrimary: '#c0caf5',
      textSecondary: '#7aa2f7',
      textMuted: '#565f89',
      accent: '#7aa2f7',
      accentLight: '#bb9af7',
      accentSoft: 'rgba(122, 162, 247, 0.2)',
      border: '#2a2f45',
      borderLight: '#343a52',
      success: '#9ece6a',
      warning: '#e0af68',
      error: '#f7768e',
      info: '#73daca',
    },
  },
  {
    id: 'monokai',
    name: 'Monokai',
    description: 'Warm & vibrant',
    category: 'dark',
    colors: {
      bgPrimary: '#272822',
      bgSecondary: '#383830',
      bgTertiary: '#49483e',
      bgCard: '#57584f',
      textPrimary: '#f8f8f2',
      textSecondary: '#a6e22e',
      textMuted: '#75715e',
      accent: '#fd971f',
      accentLight: '#f4bf75',
      accentSoft: 'rgba(253, 151, 31, 0.2)',
      border: '#49483e',
      borderLight: '#57584f',
      success: '#a6e22e',
      warning: '#e6db74',
      error: '#f92672',
      info: '#66d9ef',
    },
  },
  {
    id: 'catppuccin',
    name: 'Catppuccin Mocha',
    description: 'Soft pastels',
    category: 'dark',
    colors: {
      bgPrimary: '#1e1e2e',
      bgSecondary: '#302d41',
      bgTertiary: '#414358',
      bgCard: '#45475a',
      textPrimary: '#cdd6f4',
      textSecondary: '#b4befe',
      textMuted: '#7f849c',
      accent: '#f38ba8',
      accentLight: '#fab387',
      accentSoft: 'rgba(243, 139, 168, 0.2)',
      border: '#414358',
      borderLight: '#585b70',
      success: '#a6e3a1',
      warning: '#f9e2af',
      error: '#f38ba8',
      info: '#89dceb',
    },
  },

  // LIGHT THEMES
  {
    id: 'snow',
    name: 'Snow',
    description: 'Clean & minimal',
    category: 'light',
    colors: {
      bgPrimary: '#f8fafc',
      bgSecondary: '#ffffff',
      bgTertiary: '#f1f5f9',
      bgCard: '#ffffff',
      textPrimary: '#0f172a',
      textSecondary: '#475569',
      textMuted: '#94a3b8',
      accent: '#3b82f6',
      accentLight: '#60a5fa',
      accentSoft: 'rgba(59, 130, 246, 0.12)',
      border: '#e2e8f0',
      borderLight: '#cbd5e1',
      success: '#22c55e',
      warning: '#f59e0b',
      error: '#ef4444',
      info: '#3b82f6',
    },
  },
  {
    id: 'latte',
    name: 'Latte',
    description: 'Warm cream tones',
    category: 'light',
    colors: {
      bgPrimary: '#FAF5F0',
      bgSecondary: '#FFFFFF',
      bgTertiary: '#F5EDE4',
      bgCard: '#FFFFFF',
      textPrimary: '#2D2A26',
      textSecondary: '#6B6560',
      textMuted: '#9A928C',
      accent: '#F97316',
      accentLight: '#FB923C',
      accentSoft: 'rgba(249, 115, 22, 0.12)',
      border: '#E8E0D8',
      borderLight: '#F0EAE3',
      success: '#4ADE80',
      warning: '#FBBF24',
      error: '#FB7185',
      info: '#60A5FA',
    },
  },
  {
    id: 'rose-pine',
    name: 'Rose Pine Dawn',
    description: 'Soft pink warmth',
    category: 'light',
    colors: {
      bgPrimary: '#faf4ed',
      bgSecondary: '#fffaf3',
      bgTertiary: '#f2e9e1',
      bgCard: '#fffaf3',
      textPrimary: '#575279',
      textSecondary: '#797593',
      textMuted: '#9893a5',
      accent: '#d7827e',
      accentLight: '#ea9d34',
      accentSoft: 'rgba(215, 130, 126, 0.15)',
      border: '#f2e9e1',
      borderLight: '#dfdad9',
      success: '#56949f',
      warning: '#f6c177',
      error: '#b4637a',
      info: '#286983',
    },
  },
  {
    id: 'solarized',
    name: 'Solarized Light',
    description: 'Classic warm',
    category: 'light',
    colors: {
      bgPrimary: '#fdf6e3',
      bgSecondary: '#eee8d5',
      bgTertiary: '#e4dcc9',
      bgCard: '#eee8d5',
      textPrimary: '#073642',
      textSecondary: '#586e75',
      textMuted: '#93a1a1',
      accent: '#cb4b16',
      accentLight: '#dc322f',
      accentSoft: 'rgba(203, 75, 22, 0.15)',
      border: '#e4dcc9',
      borderLight: '#d3cbb8',
      success: '#859900',
      warning: '#b58900',
      error: '#dc322f',
      info: '#268bd2',
    },
  },
  {
    id: 'paper',
    name: 'Paper',
    description: 'Ultra minimal',
    category: 'light',
    colors: {
      bgPrimary: '#ffffff',
      bgSecondary: '#fafafa',
      bgTertiary: '#f5f5f5',
      bgCard: '#ffffff',
      textPrimary: '#171717',
      textSecondary: '#525252',
      textMuted: '#a3a3a3',
      accent: '#171717',
      accentLight: '#404040',
      accentSoft: 'rgba(23, 23, 23, 0.08)',
      border: '#e5e5e5',
      borderLight: '#d4d4d4',
      success: '#16a34a',
      warning: '#ca8a04',
      error: '#dc2626',
      info: '#2563eb',
    },
  },

  // SPECIAL THEMES
  {
    id: 'solovision',
    name: 'SoLoVision Red',
    description: 'Brand essence',
    category: 'special',
    colors: {
      bgPrimary: '#0f0505',
      bgSecondary: '#1a0a0a',
      bgTertiary: '#2a1515',
      bgCard: '#351515',
      textPrimary: '#ffe4e4',
      textSecondary: '#ff9999',
      textMuted: '#b36666',
      accent: '#dc2626',
      accentLight: '#ef4444',
      accentSoft: 'rgba(220, 38, 38, 0.2)',
      border: '#451515',
      borderLight: '#552525',
      success: '#22c55e',
      warning: '#f59e0b',
      error: '#ff4444',
      info: '#3b82f6',
    },
  },
  {
    id: 'cyberpunk',
    name: 'Cyberpunk',
    description: 'Neon future',
    category: 'special',
    colors: {
      bgPrimary: '#0d0221',
      bgSecondary: '#1a0b2e',
      bgTertiary: '#261447',
      bgCard: '#2e1a5e',
      textPrimary: '#f0e6ff',
      textSecondary: '#c77dff',
      textMuted: '#7a3db8',
      accent: '#00ff41',
      accentLight: '#39ff14',
      accentSoft: 'rgba(0, 255, 65, 0.2)',
      border: '#3d1f7a',
      borderLight: '#4d2799',
      success: '#00ff41',
      warning: '#ffee00',
      error: '#ff0066',
      info: '#00ccff',
    },
  },
  {
    id: 'ocean',
    name: 'Ocean',
    description: 'Deep teal waters',
    category: 'special',
    colors: {
      bgPrimary: '#0c4a6e',
      bgSecondary: '#075985',
      bgTertiary: '#0369a1',
      bgCard: '#0ea5e9',
      textPrimary: '#f0f9ff',
      textSecondary: '#7dd3fc',
      textMuted: '#38bdf8',
      accent: '#06b6d4',
      accentLight: '#22d3ee',
      accentSoft: 'rgba(6, 182, 212, 0.2)',
      border: '#0284c7',
      borderLight: '#0ea5e9',
      success: '#10b981',
      warning: '#f59e0b',
      error: '#ef4444',
      info: '#38bdf8',
    },
  },
]

export const getThemeById = (id: string): Theme => {
  return themes.find(t => t.id === id) || themes[0]
}

export const applyTheme = (theme: Theme) => {
  const root = document.documentElement
  const c = theme.colors
  
  root.style.setProperty('--bg-primary', c.bgPrimary)
  root.style.setProperty('--bg-secondary', c.bgSecondary)
  root.style.setProperty('--bg-tertiary', c.bgTertiary)
  root.style.setProperty('--bg-card', c.bgCard)
  root.style.setProperty('--text-primary', c.textPrimary)
  root.style.setProperty('--text-secondary', c.textSecondary)
  root.style.setProperty('--text-muted', c.textMuted)
  root.style.setProperty('--accent', c.accent)
  root.style.setProperty('--accent-light', c.accentLight)
  root.style.setProperty('--accent-soft', c.accentSoft)
  root.style.setProperty('--border', c.border)
  root.style.setProperty('--border-light', c.borderLight)
  root.style.setProperty('--success', c.success)
  root.style.setProperty('--warning', c.warning)
  root.style.setProperty('--error', c.error)
  root.style.setProperty('--info', c.info)
}
