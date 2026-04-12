import { useEffect } from 'react'
import { HashRouter, Routes, Route } from 'react-router-dom'
import { ThemeProvider } from './components/theme-provider'
import { AppShell } from './components/app-shell'
import { Dashboard } from './screens/Dashboard'
import { Chat } from './screens/Chat'
import { Tasks } from './screens/Tasks'
import { Cron } from './screens/Cron'
import { Notes } from './screens/Notes'
import { Activity } from './screens/Activity'
import { Settings } from './screens/Settings'
import { Rules } from './screens/Rules'
import { Agents } from './screens/Agents'
import { Skills } from './screens/Skills'
import { useAppStore } from './hooks/use-store'

function App() {
  const { init } = useAppStore()

  useEffect(() => {
    init()
  }, [init])

  return (
    <ThemeProvider>
      <HashRouter>
        <AppShell>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/agents" element={<Agents />} />
            <Route path="/chat" element={<Chat />} />
            <Route path="/chat/:sessionId" element={<Chat />} />
            <Route path="/tasks" element={<Tasks />} />
            <Route path="/cron" element={<Cron />} />
            <Route path="/notes" element={<Notes />} />
            <Route path="/activity" element={<Activity />} />
            <Route path="/skills" element={<Skills />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/rules" element={<Rules />} />
          </Routes>
        </AppShell>
      </HashRouter>
    </ThemeProvider>
  )
}

export default App
