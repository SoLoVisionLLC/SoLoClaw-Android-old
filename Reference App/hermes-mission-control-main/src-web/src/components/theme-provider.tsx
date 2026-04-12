import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { themes, applyTheme, getThemeById, type Theme } from '../lib/themes'

interface ThemeProviderProps {
  children: ReactNode
  defaultTheme?: string
}

const ThemeContext = createContext<{
  theme: Theme
  themeId: string
  setTheme: (themeId: string) => void
  themes: typeof themes
} | null>(null)

export function ThemeProvider({ children, defaultTheme = 'latte' }: ThemeProviderProps) {
  const [themeId, setThemeId] = useState<string>(() => {
    const stored = localStorage.getItem('hermes-theme-id')
    return stored || defaultTheme
  })

  const theme = getThemeById(themeId)

  useEffect(() => {
    applyTheme(theme)
    localStorage.setItem('hermes-theme-id', themeId)
  }, [theme, themeId])

  const setTheme = (newThemeId: string) => {
    setThemeId(newThemeId)
  }

  return (
    <ThemeContext.Provider value={{ theme, themeId, setTheme, themes }}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme must be used within ThemeProvider')
  return context
}
