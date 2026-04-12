import { useEffect, useMemo, useState } from 'react'
import { Search, Wrench, FileText, Save, RefreshCw, X, EyeOff, Eye, Trash2, RotateCw, ShieldCheck, Plus } from 'lucide-react'
import * as api from '../lib/api-client'
import type { SkillSummary, SkillFileEntry } from '../types'
import { useIsMobile } from '../hooks/use-is-mobile'

const HIDDEN_SKILLS_KEY = 'mission-control.hidden-skills.v1'

type SourceFilter = '' | 'bundled' | 'local' | 'openclaw-import'

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function readHiddenSkills(): string[] {
  try {
    return JSON.parse(localStorage.getItem(HIDDEN_SKILLS_KEY) || '[]')
  } catch {
    return []
  }
}

function writeHiddenSkills(skills: string[]) {
  localStorage.setItem(HIDDEN_SKILLS_KEY, JSON.stringify(skills))
}

function Chip({ label, tone = 'neutral' }: { label: string; tone?: 'neutral' | 'success' | 'warning' | 'accent' }) {
  const styles = {
    neutral: { bg: 'var(--bg-tertiary)', color: 'var(--text-secondary)', border: '1px solid var(--border-light)' },
    success: { bg: '#f0fdf4', color: '#166534', border: '1px solid #bbf7d0' },
    warning: { bg: '#fffbeb', color: '#92400e', border: '1px solid #fde68a' },
    accent: { bg: '#fff7ed', color: 'var(--accent)', border: '1px solid #fdba74' },
  }[tone]
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '4px 8px',
        minHeight: 22,
        borderRadius: 8,
        fontSize: 11,
        lineHeight: 1.1,
        fontWeight: 600,
        letterSpacing: 0,
        whiteSpace: 'nowrap',
        background: styles.bg,
        color: styles.color,
        border: styles.border,
        boxSizing: 'border-box',
      }}
    >
      {label}
    </span>
  )
}

function FilterButton({ active, children, onClick }: { active: boolean; children: React.ReactNode; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      style={{
        padding: '8px 12px',
        borderRadius: 999,
        border: `1px solid ${active ? 'var(--accent)' : 'var(--border)'}`,
        background: active ? 'var(--accent-soft)' : 'white',
        color: active ? 'var(--accent)' : 'var(--text-secondary)',
        fontSize: 12,
        fontWeight: 700,
        cursor: 'pointer',
      }}
    >
      {children}
    </button>
  )
}

export function Skills() {
  const isMobile = useIsMobile()
  const [skills, setSkills] = useState<SkillSummary[]>([])
  const [search, setSearch] = useState('')
  const [sourceFilter, setSourceFilter] = useState<SourceFilter>('')
  const [showHidden, setShowHidden] = useState(false)
  const [hiddenSkills, setHiddenSkills] = useState<string[]>(() => readHiddenSkills())
  const [selectedSkill, setSelectedSkill] = useState<SkillSummary | null>(null)
  const [skillFiles, setSkillFiles] = useState<SkillFileEntry[]>([])
  const [selectedFile, setSelectedFile] = useState<SkillFileEntry | null>(null)
  const [fileContent, setFileContent] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadingFiles, setLoadingFiles] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actingOnSkill, setActingOnSkill] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [actionLog, setActionLog] = useState<string | null>(null)
  const [hubOpen, setHubOpen] = useState(false)
  const [hubQuery, setHubQuery] = useState('')
  const [hubIdentifier, setHubIdentifier] = useState('')
  const [hubResult, setHubResult] = useState<string>('')
  const [hubSearchResults, setHubSearchResults] = useState<Array<{ name: string; description: string; source: string; identifier: string; trust_level: string; repo?: string; path?: string; tags?: string[]; extra?: Record<string, unknown> }>>([])
  const [hubBrowsePage, setHubBrowsePage] = useState(1)
  const [hubBrowseTotalPages, setHubBrowseTotalPages] = useState(1)
  const [hubBusy, setHubBusy] = useState<'search' | 'browse' | 'inspect' | 'install' | null>(null)
  const [showHubRawOutput, setShowHubRawOutput] = useState(false)

  const loadSkills = async () => {
    setLoading(true)
    try {
      const result = await api.getSkills()
      setSkills(result)
    } catch (error) {
      console.error('Failed to load skills:', error)
      setMessage('Failed to load skills')
    } finally {
      setLoading(false)
    }
  }

  const loadFiles = async (skill: SkillSummary) => {
    setLoadingFiles(true)
    try {
      const result = await api.getSkillFiles(skill.name)
      setSkillFiles(result.files)
      setSelectedSkill(skill)
      setSelectedFile(null)
      setFileContent('')
    } catch (error) {
      console.error('Failed to load skill files:', error)
      setMessage('Failed to load skill files')
    } finally {
      setLoadingFiles(false)
    }
  }

  const openFile = async (file: SkillFileEntry) => {
    if (!selectedSkill) return
    try {
      const result = await api.getSkillFile(selectedSkill.name, file.relative_path)
      setSelectedFile(file)
      setFileContent(result.content)
    } catch (error) {
      console.error('Failed to read skill file:', error)
      setMessage('Failed to read skill file')
    }
  }

  const saveFile = async () => {
    if (!selectedSkill || !selectedFile) return
    setSaving(true)
    try {
      await api.updateSkillFile(selectedSkill.name, selectedFile.relative_path, fileContent)
      setMessage('Skill file saved')
      await loadFiles(selectedSkill)
    } catch (error) {
      console.error('Failed to save skill file:', error)
      setMessage('Failed to save skill file')
    } finally {
      setSaving(false)
    }
  }

  const toggleHidden = (name: string) => {
    const next = hiddenSkills.includes(name)
      ? hiddenSkills.filter((n) => n !== name)
      : [...hiddenSkills, name]
    setHiddenSkills(next)
    writeHiddenSkills(next)
  }

  const runSkillAction = async (skill: SkillSummary, action: 'check' | 'update') => {
    setActingOnSkill(`${skill.name}:${action}`)
    try {
      const result = action === 'check' ? await api.checkSkill(skill.name) : await api.updateSkill(skill.name)
      setActionLog([result.command, '', result.stdout || '(no stdout)', result.stderr ? `STDERR:\n${result.stderr}` : ''].filter(Boolean).join('\n'))
      setMessage(`${action === 'check' ? 'Checked' : 'Updated'} ${skill.name}`)
      await loadSkills()
    } catch (error) {
      console.error(`Failed to ${action} skill:`, error)
      setMessage(`Failed to ${action} ${skill.name}`)
    } finally {
      setActingOnSkill(null)
    }
  }

  const uninstallSkill = async (skill: SkillSummary) => {
    if (!skill.can_uninstall) {
      setMessage('Cannot uninstall bundled skills. Hide them instead.')
      return
    }
    if (!confirm(`Uninstall skill “${skill.name}”?`)) return
    try {
      await api.deleteSkill(skill.name)
      setMessage(`Uninstalled ${skill.name}`)
      if (selectedSkill?.name === skill.name) {
        setSelectedSkill(null)
        setSelectedFile(null)
        setFileContent('')
        setSkillFiles([])
      }
      await loadSkills()
    } catch (error) {
      console.error('Failed to uninstall skill:', error)
      setMessage('Failed to uninstall skill')
    }
  }

  useEffect(() => { loadSkills() }, [])

  const filteredSkills = useMemo(() => {
    const q = search.trim().toLowerCase()
    return skills
      .filter((skill) => !q || skill.name.toLowerCase().includes(q) || skill.description.toLowerCase().includes(q) || skill.category.toLowerCase().includes(q))
      .filter((skill) => !sourceFilter || skill.source === sourceFilter)
      .filter((skill) => showHidden || !hiddenSkills.includes(skill.name))
  }, [skills, search, sourceFilter, showHidden, hiddenSkills])

  const sourceCounts = useMemo(() => ({
    bundled: skills.filter((s) => s.source === 'bundled').length,
    local: skills.filter((s) => s.source === 'local').length,
    openclaw: skills.filter((s) => s.source === 'openclaw-import').length,
  }), [skills])

  return (
    <div style={{ padding: isMobile ? 16 : 24, width: '100%', maxWidth: '100%', boxSizing: 'border-box', height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: isMobile ? 'stretch' : 'center', flexDirection: isMobile ? 'column' : 'row', gap: 12, marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: isMobile ? 24 : 28, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 6 }}>🧩 Skills</h1>
          <p style={{ fontSize: 15, color: 'var(--text-secondary)' }}>Browse, inspect, and manage Hermes skills</p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button onClick={() => setHubOpen(true)} style={{ padding: '10px 14px', borderRadius: 12, border: '1px solid var(--border)', background: 'white', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
            <Plus size={16} /> Install Skill
          </button>
          <button onClick={loadSkills} style={{ padding: '10px 14px', borderRadius: 12, border: '1px solid var(--border)', background: 'white', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
            <RefreshCw size={16} /> Refresh
          </button>
        </div>
      </div>

      {message && (
        <div style={{ marginBottom: 16, padding: '12px 14px', borderRadius: 10, background: 'var(--accent-soft)', color: 'var(--text-primary)' }}>{message}</div>
      )}
      {actionLog && (
        <div style={{ marginBottom: 16, padding: 14, borderRadius: 12, background: 'white', border: '1px solid var(--border-light)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <div style={{ fontWeight: 700, color: 'var(--text-primary)' }}>Last skills action</div>
            <button onClick={() => setActionLog(null)} style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: 'var(--text-muted)' }}><X size={16} /></button>
          </div>
          <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12, margin: 0, color: 'var(--text-secondary)', maxHeight: 220, overflow: 'auto' }}>{actionLog}</pre>
        </div>
      )}

      {hubOpen && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.45)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20 }}>
          <div style={{ width: 'min(900px, 100%)', maxHeight: '85vh', overflow: 'hidden', background: 'white', borderRadius: 18, border: '1px solid var(--border-light)', display: 'flex', flexDirection: 'column' }}>
            <div style={{ padding: 18, borderBottom: '1px solid var(--border-light)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ fontSize: 20, fontWeight: 800, color: 'var(--text-primary)' }}>Install Skill</div>
                <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Search the hub, inspect by identifier, then install</div>
              </div>
              <button onClick={() => setHubOpen(false)} style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: 'var(--text-muted)' }}><X size={18} /></button>
            </div>
            <div style={{ padding: 18, display: 'grid', gap: 12, overflow: 'auto' }}>
              <div style={{ display: 'grid', gap: 8 }}>
                <label style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-secondary)' }}>Search query</label>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <input value={hubQuery} onChange={(e) => setHubQuery(e.target.value)} placeholder='e.g. notion or github' style={{ flex: 1, minWidth: 220, padding: '10px 12px', borderRadius: 10, border: '1px solid var(--border)' }} />
                  <button onClick={async () => {
                    setHubBusy('search')
                    try {
                      const [result, structured] = await Promise.all([
                        api.searchSkillsHub(hubQuery),
                        api.searchSkillsHubStructured(hubQuery),
                      ])
                      setHubResult([result.command, '', result.stdout || '(no stdout)', result.stderr ? `STDERR:\n${result.stderr}` : ''].filter(Boolean).join('\n'))
                      setHubSearchResults(structured)
                      setShowHubRawOutput(false)
                    } finally { setHubBusy(null) }
                  }} style={{ padding: '10px 12px', borderRadius: 10, border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>{hubBusy === 'search' ? 'Searching…' : 'Search'}</button>
                  <button onClick={async () => {
                    setHubBusy('browse')
                    try {
                      const structured = await api.browseSkillsHubStructured(hubBrowsePage, 20, 'all')
                      setHubSearchResults(structured.items)
                      setHubBrowsePage(structured.page)
                      setHubBrowseTotalPages(structured.total_pages)
                      setHubResult(`Browse all skills\n\nPage ${structured.page} of ${structured.total_pages}\nTotal skills: ${structured.total}`)
                      setShowHubRawOutput(false)
                    } finally { setHubBusy(null) }
                  }} style={{ padding: '10px 12px', borderRadius: 10, border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>{hubBusy === 'browse' ? 'Loading…' : 'Browse All'}</button>
                </div>
              </div>
              <div style={{ display: 'grid', gap: 8 }}>
                <label style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-secondary)' }}>Identifier</label>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <input value={hubIdentifier} onChange={(e) => setHubIdentifier(e.target.value)} placeholder='e.g. skills-sh/openai/skills/skill-creator' style={{ flex: 1, minWidth: 260, padding: '10px 12px', borderRadius: 10, border: '1px solid var(--border)' }} />
                  <button onClick={async () => {
                    setHubBusy('inspect')
                    try {
                      const result = await api.inspectSkillHub(hubIdentifier)
                      setHubResult([result.command, '', result.stdout || '(no stdout)', result.stderr ? `STDERR:\n${result.stderr}` : ''].filter(Boolean).join('\n'))
                    } finally { setHubBusy(null) }
                  }} style={{ padding: '10px 12px', borderRadius: 10, border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>{hubBusy === 'inspect' ? 'Inspecting…' : 'Inspect'}</button>
                  <button onClick={async () => {
                    setHubBusy('install')
                    try {
                      const result = await api.installSkillHub(hubIdentifier)
                      setHubResult([result.command, '', result.stdout || '(no stdout)', result.stderr ? `STDERR:\n${result.stderr}` : ''].filter(Boolean).join('\n'))
                      await loadSkills()
                    } finally { setHubBusy(null) }
                  }} style={{ padding: '10px 12px', borderRadius: 10, border: '1px solid var(--accent)', background: 'var(--accent)', color: 'white', cursor: 'pointer' }}>{hubBusy === 'install' ? 'Installing…' : 'Install'}</button>
                </div>
              </div>
              {hubSearchResults.length > 0 ? (
                <div style={{ display: 'grid', gap: 10 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Search / browse results</div>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                      <button onClick={async () => {
                        if (hubBrowsePage <= 1) return
                        setHubBusy('browse')
                        try {
                          const structured = await api.browseSkillsHubStructured(hubBrowsePage - 1, 20, 'all')
                          setHubSearchResults(structured.items)
                          setHubBrowsePage(structured.page)
                          setHubBrowseTotalPages(structured.total_pages)
                          setHubResult(`Browse all skills\n\nPage ${structured.page} of ${structured.total_pages}\nTotal skills: ${structured.total}`)
                        } finally { setHubBusy(null) }
                      }} disabled={hubBrowsePage <= 1} style={{ padding: '6px 10px', borderRadius: 8, border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>Prev</button>
                      <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Page {hubBrowsePage}/{hubBrowseTotalPages}</span>
                      <button onClick={async () => {
                        if (hubBrowsePage >= hubBrowseTotalPages) return
                        setHubBusy('browse')
                        try {
                          const structured = await api.browseSkillsHubStructured(hubBrowsePage + 1, 20, 'all')
                          setHubSearchResults(structured.items)
                          setHubBrowsePage(structured.page)
                          setHubBrowseTotalPages(structured.total_pages)
                          setHubResult(`Browse all skills\n\nPage ${structured.page} of ${structured.total_pages}\nTotal skills: ${structured.total}`)
                        } finally { setHubBusy(null) }
                      }} disabled={hubBrowsePage >= hubBrowseTotalPages} style={{ padding: '6px 10px', borderRadius: 8, border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>Next</button>
                    </div>
                  </div>
                  <div style={{ display: 'grid', gap: 10, maxHeight: 280, overflow: 'auto' }}>
                    {hubSearchResults.map((item) => (
                      <div key={item.identifier} style={{ border: '1px solid var(--border-light)', borderRadius: 12, background: 'white', padding: 12 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, marginBottom: 8 }}>
                          <div style={{ minWidth: 0 }}>
                            <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)', marginBottom: 4 }}>{item.name}</div>
                            <div style={{ fontSize: 11, color: 'var(--text-muted)', wordBreak: 'break-all' }}>{item.identifier}</div>
                          </div>
                          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                            <Chip label={item.source} />
                            <Chip label={item.trust_level} tone={item.trust_level === 'trusted' ? 'success' : item.trust_level === 'community' ? 'warning' : 'neutral'} />
                          </div>
                        </div>
                        <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 10 }}>{item.description}</div>
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 10 }}>
                          {typeof item.extra?.weekly_installs === 'number' ? <Chip label={`${item.extra.weekly_installs}/wk`} tone='accent' /> : null}
                          {typeof item.extra?.installs === 'number' ? <Chip label={`${item.extra.installs} installs`} /> : null}
                          {Array.isArray(item.tags) ? item.tags.slice(0, 3).map(tag => <Chip key={tag} label={tag} />) : null}
                        </div>
                        {typeof item.extra?.repo_url === 'string' ? (
                          <div style={{ fontSize: 12, marginBottom: 10 }}>
                            <a href={item.extra.repo_url} target='_blank' rel='noreferrer' style={{ color: 'var(--accent)', textDecoration: 'none', wordBreak: 'break-all' }}>{item.extra.repo_url}</a>
                          </div>
                        ) : null}
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                          <button onClick={() => setHubIdentifier(item.identifier)} style={{ padding: '8px 10px', borderRadius: 10, border: hubIdentifier === item.identifier ? '1px solid var(--accent)' : '1px solid var(--border)', background: hubIdentifier === item.identifier ? 'var(--accent-soft)' : 'white', cursor: 'pointer' }}>Use identifier</button>
                          <button onClick={async () => {
                            setHubIdentifier(item.identifier)
                            setHubBusy('inspect')
                            try {
                              const result = await api.inspectSkillHub(item.identifier)
                              setHubResult([result.command, '', result.stdout || '(no stdout)', result.stderr ? `STDERR:\n${result.stderr}` : ''].filter(Boolean).join('\n'))
                            } finally { setHubBusy(null) }
                          }} style={{ padding: '8px 10px', borderRadius: 10, border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>Inspect</button>
                          <button onClick={async () => {
                            setHubIdentifier(item.identifier)
                            setHubBusy('install')
                            try {
                              const result = await api.installSkillHub(item.identifier)
                              setHubResult([result.command, '', result.stdout || '(no stdout)', result.stderr ? `STDERR:\n${result.stderr}` : ''].filter(Boolean).join('\n'))
                              await loadSkills()
                            } finally { setHubBusy(null) }
                          }} style={{ padding: '8px 10px', borderRadius: 10, border: '1px solid var(--accent)', background: 'var(--accent)', color: 'white', cursor: 'pointer' }}>Install</button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}
              {(!isMobile || showHubRawOutput) && (
                <div style={{ border: '1px solid var(--border-light)', borderRadius: 12, background: 'var(--bg-secondary)', padding: 14 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8, gap: 12 }}>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Hub output</div>
                    {isMobile ? (
                      <button onClick={() => setShowHubRawOutput(false)} style={{ border: 'none', background: 'transparent', color: 'var(--text-muted)', cursor: 'pointer', fontSize: 12 }}>Hide raw output</button>
                    ) : null}
                  </div>
                  <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12, margin: 0, color: 'var(--text-secondary)', maxHeight: 320, overflow: 'auto' }}>{hubResult || 'Run Search, Inspect, or Install to see output here.'}</pre>
                </div>
              )}
              {isMobile && !showHubRawOutput && hubResult && (
                <button onClick={() => setShowHubRawOutput(true)} style={{ padding: '10px 12px', borderRadius: 10, border: '1px solid var(--border)', background: 'white', cursor: 'pointer', justifySelf: 'start' }}>
                  Show raw output
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      <div style={{ display: 'grid', gap: 12, marginBottom: 16 }}>
        <div style={{ position: 'relative' }}>
          <Search size={18} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder='Search skills by name, category, or description'
            style={{ width: '100%', padding: '12px 14px 12px 42px', borderRadius: 14, border: '1px solid var(--border)', background: 'white', fontSize: 14 }}
          />
        </div>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
          <FilterButton active={sourceFilter === ''} onClick={() => setSourceFilter('')}>All ({skills.length})</FilterButton>
          <FilterButton active={sourceFilter === 'bundled'} onClick={() => setSourceFilter('bundled')}>Bundled ({sourceCounts.bundled})</FilterButton>
          <FilterButton active={sourceFilter === 'local'} onClick={() => setSourceFilter('local')}>Local ({sourceCounts.local})</FilterButton>
          <FilterButton active={sourceFilter === 'openclaw-import'} onClick={() => setSourceFilter('openclaw-import')}>OpenClaw Imports ({sourceCounts.openclaw})</FilterButton>
          <FilterButton active={showHidden} onClick={() => setShowHidden(!showHidden)}>{showHidden ? 'Hide Hidden' : 'Show Hidden'}</FilterButton>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: selectedSkill && !isMobile ? 'minmax(340px, 460px) minmax(0, 1fr)' : '1fr', gap: 20, flex: 1, minHeight: 0, overflow: 'hidden' }}>
        <div style={{ minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          {loading ? (
            <div style={{ padding: 20, borderRadius: 16, background: 'white', border: '1px solid var(--border-light)' }}>Loading skills…</div>
          ) : filteredSkills.length === 0 ? (
            <div style={{ padding: 20, borderRadius: 16, background: 'white', border: '1px solid var(--border-light)' }}>No matching skills.</div>
          ) : (
            <div style={{ display: 'grid', gap: 12, overflow: 'auto', minHeight: 0, paddingRight: 4 }}>
              {filteredSkills.map((skill) => {
                const isSelected = selectedSkill?.name === skill.name
                const isHidden = hiddenSkills.includes(skill.name)
                return (
                  <div key={skill.name} style={{ display: 'grid', gap: 12 }}>
                    <div style={{ width: '100%', textAlign: 'left', padding: 16, borderRadius: 18, border: `1px solid ${isSelected ? 'var(--accent)' : 'var(--border-light)'}`, background: isSelected ? 'var(--accent-soft)' : 'white' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 10 }}>
                        <button onClick={() => loadFiles(skill)} style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1, border: 'none', background: 'transparent', textAlign: 'left', cursor: 'pointer' }}>
                          <div style={{ width: 40, height: 40, borderRadius: 12, background: 'var(--accent-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent)', flexShrink: 0 }}>
                            <Wrench size={20} />
                          </div>
                          <div style={{ minWidth: 0 }}>
                            <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)', wordBreak: 'break-word', marginBottom: 6 }}>{skill.name}</div>
                            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                              <Chip label={skill.category} />
                            </div>
                          </div>
                        </button>
                        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end', alignItems: 'flex-start', maxWidth: isMobile ? '100%' : 180 }}>
                          {skill.source === 'openclaw-import' ? <Chip label='OpenClaw Import' tone='warning' /> : null}
                          {!skill.bundled ? <Chip label='Editable' tone='accent' /> : null}
                          {isHidden ? <Chip label='Hidden' tone='warning' /> : null}
                        </div>
                      </div>

                      <div style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.5, marginBottom: 10 }}>{skill.description || 'No description'}</div>

                      <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 12, wordBreak: 'break-all' }}>{skill.path}</div>

                      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <button onClick={() => loadFiles(skill)} style={{ padding: '8px 12px', borderRadius: 10, border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>📂 Files</button>
                        <button onClick={() => runSkillAction(skill, 'check')} disabled={actingOnSkill === `${skill.name}:check`} style={{ padding: '8px 12px', borderRadius: 10, border: '1px solid var(--border)', background: 'white', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}>
                          <ShieldCheck size={14} /> {actingOnSkill === `${skill.name}:check` ? 'Checking…' : 'Check'}
                        </button>
                        <button onClick={() => runSkillAction(skill, 'update')} disabled={actingOnSkill === `${skill.name}:update`} style={{ padding: '8px 12px', borderRadius: 10, border: '1px solid var(--border)', background: 'white', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}>
                          <RotateCw size={14} /> {actingOnSkill === `${skill.name}:update` ? 'Updating…' : 'Update'}
                        </button>
                        <button onClick={() => toggleHidden(skill.name)} style={{ padding: '8px 12px', borderRadius: 10, border: '1px solid var(--border)', background: 'white', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}>
                          {isHidden ? <Eye size={14} /> : <EyeOff size={14} />} {isHidden ? 'Unhide' : 'Hide'}
                        </button>
                        {skill.can_uninstall ? (
                          <button onClick={() => uninstallSkill(skill)} style={{ padding: '8px 12px', borderRadius: 10, border: '1px solid #fecaca', background: '#fff1f2', color: '#b91c1c', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}>
                            <Trash2 size={14} /> Uninstall
                          </button>
                        ) : null}
                      </div>
                    </div>

                    {isMobile && isSelected ? (
                      <div style={{ padding: 16, borderRadius: 16, background: 'white', border: '1px solid var(--border-light)', minHeight: 0, overflow: 'hidden' }}>
                        <SkillPanel
                          skill={selectedSkill}
                          files={skillFiles}
                          selectedFile={selectedFile}
                          fileContent={fileContent}
                          loadingFiles={loadingFiles}
                          saving={saving}
                          onClose={() => setSelectedSkill(null)}
                          onOpenFile={openFile}
                          onChangeContent={setFileContent}
                          onSave={saveFile}
                          isMobile
                        />
                      </div>
                    ) : null}
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {!isMobile && selectedSkill ? (
          <div style={{ padding: 16, borderRadius: 16, background: 'white', border: '1px solid var(--border-light)', minWidth: 0, minHeight: 0, overflow: 'hidden', display: 'flex' }}>
            <SkillPanel
              skill={selectedSkill}
              files={skillFiles}
              selectedFile={selectedFile}
              fileContent={fileContent}
              loadingFiles={loadingFiles}
              saving={saving}
              onClose={() => setSelectedSkill(null)}
              onOpenFile={openFile}
              onChangeContent={setFileContent}
              onSave={saveFile}
              isMobile={false}
            />
          </div>
        ) : null}
      </div>
    </div>
  )
}

function SkillPanel({ skill, files, selectedFile, fileContent, loadingFiles, saving, onClose, onOpenFile, onChangeContent, onSave, isMobile }: {
  skill: SkillSummary | null
  files: SkillFileEntry[]
  selectedFile: SkillFileEntry | null
  fileContent: string
  loadingFiles: boolean
  saving: boolean
  onClose: () => void
  onOpenFile: (file: SkillFileEntry) => void
  onChangeContent: (value: string) => void
  onSave: () => void
  isMobile: boolean
}) {
  if (!skill) return null
  return (
    <div style={{ width: '100%', minWidth: 0, height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: isMobile ? 'stretch' : 'flex-start', flexDirection: isMobile ? 'column' : 'row', gap: 12, marginBottom: 16 }}>
        <div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 8 }}>Selected skill</div>
          <div style={{ fontSize: 22, fontWeight: 800, color: 'var(--text-primary)', marginBottom: 6, wordBreak: 'break-word' }}>{skill.name}</div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
            <Chip label={skill.category} />
            <Chip label={skill.source} tone={skill.source === 'bundled' ? 'success' : skill.source === 'openclaw-import' ? 'warning' : 'accent'} />
            {skill.bundled ? <Chip label='Bundled' /> : <Chip label='Local' tone='accent' />}
          </div>
          <div style={{ fontSize: 13, color: 'var(--text-secondary)', wordBreak: 'break-all' }}>{skill.path}</div>
        </div>
        <button onClick={onClose} style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: 6 }}><X size={16} /> Hide panel</button>
      </div>

      <div style={{ marginBottom: 16, padding: 12, borderRadius: 12, background: 'var(--bg-secondary)', border: '1px solid var(--border-light)' }}>
        <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 6 }}>Description</div>
        <div style={{ fontSize: 14, color: 'var(--text-primary)' }}>{skill.description || 'No description'}</div>
      </div>

      {loadingFiles ? <div style={{ padding: 16, color: 'var(--text-muted)' }}>Loading files…</div> : (
        <div style={{ display: 'grid', gridTemplateColumns: isMobile ? '1fr' : '280px minmax(0,1fr)', gap: 16, flex: 1, minHeight: 0, overflow: 'hidden' }}>
          <div style={{ border: '1px solid var(--border-light)', borderRadius: 12, overflow: 'hidden', minWidth: 0, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
            <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border-light)', fontWeight: 700, fontSize: 14 }}>Files</div>
            <div style={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
              {files.map(file => (
                <button key={file.relative_path} onClick={() => onOpenFile(file)} style={{ width: '100%', textAlign: 'left', border: 'none', background: selectedFile?.relative_path === file.relative_path ? 'var(--accent-soft)' : 'white', padding: '12px 14px', borderBottom: '1px solid var(--border-light)', cursor: 'pointer' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}><FileText size={14} /><span style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', wordBreak: 'break-all' }}>{file.relative_path}</span></div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{formatBytes(file.size)} • {new Date(file.mtime).toLocaleString()}</div>
                </button>
              ))}
            </div>
          </div>
          <div style={{ minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, gap: 12 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)', wordBreak: 'break-all' }}>{selectedFile ? selectedFile.relative_path : 'Select a file'}</div>
              <button onClick={onSave} disabled={!selectedFile || saving} style={{ padding: '8px 12px', borderRadius: 10, border: '1px solid var(--border)', background: saving ? 'var(--accent-soft)' : 'white', cursor: saving ? 'wait' : 'pointer', display: 'flex', alignItems: 'center', gap: 6, flexShrink: 0 }}><Save size={14} /> {saving ? 'Saving...' : 'Save'}</button>
            </div>
            <textarea
              value={fileContent}
              onChange={(e) => onChangeContent(e.target.value)}
              placeholder='Select a file to view or edit its content'
              style={{ width: '100%', flex: 1, minHeight: isMobile ? 320 : 0, borderRadius: 12, border: '1px solid var(--border-light)', padding: 14, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 13, resize: 'none', background: 'white', color: 'var(--text-primary)', overflow: 'auto' }}
            />
          </div>
        </div>
      )}
    </div>
  )
}
