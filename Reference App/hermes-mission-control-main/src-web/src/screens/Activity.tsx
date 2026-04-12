import { 
  MessageSquare, 
  CheckSquare, 
  Bot, 
  FileText,
  AlertCircle,
  Info,
  Zap,
  Clock
} from 'lucide-react'
import { useAppStore } from '../hooks/use-store'
import { cn } from '../lib/utils'
import type { Activity as ActivityType } from '../types'

const ACTIVITY_ICONS: Record<ActivityType['activity_type'], React.ComponentType<{ className?: string }>> = {
  message: MessageSquare,
  tool_call: Zap,
  file_operation: FileText,
  task_created: CheckSquare,
  task_updated: CheckSquare,
  task_completed: CheckSquare,
  agent_connected: Bot,
  agent_disconnected: Bot,
  error: AlertCircle,
  warning: AlertCircle,
  info: Info,
}

const ACTIVITY_COLORS: Record<ActivityType['activity_type'], string> = {
  message: 'text-blue-500 bg-blue-500/10',
  tool_call: 'text-purple-500 bg-purple-500/10',
  file_operation: 'text-yellow-500 bg-yellow-500/10',
  task_created: 'text-green-500 bg-green-500/10',
  task_updated: 'text-blue-500 bg-blue-500/10',
  task_completed: 'text-green-500 bg-green-500/10',
  agent_connected: 'text-green-500 bg-green-500/10',
  agent_disconnected: 'text-red-500 bg-red-500/10',
  error: 'text-red-500 bg-red-500/10',
  warning: 'text-yellow-500 bg-yellow-500/10',
  info: 'text-blue-500 bg-blue-500/10',
}

function ActivityItem({ activity, index }: { activity: ActivityType; index: number }) {
  const Icon = ACTIVITY_ICONS[activity.activity_type]
  const colorClass = ACTIVITY_COLORS[activity.activity_type]
  
  return (
    <div className={cn(
      "flex items-start gap-4 py-4",
      index !== 0 && "border-t border-hermes-border"
    )}>
      <div className={cn(
        "flex h-10 w-10 shrink-0 items-center justify-center rounded-lg",
        colorClass
      )}>
        <Icon className="h-5 w-5" />
      </div>
      
      <div className="flex-1 min-w-0">
        <p className="text-sm text-hermes-text-primary">{activity.message}</p>
        <div className="mt-1 flex items-center gap-2 text-xs text-hermes-text-muted">
          <span>{new Date(activity.timestamp).toLocaleString()}</span>
          <span>•</span>
          <span className="capitalize">{activity.activity_type.replace(/_/g, ' ')}</span>
          {activity.agent_id && (
            <>
              <span>•</span>
              <span>{activity.agent_id}</span>
            </>
          )}
        </div>
        
        {activity.details && (
          <pre className="mt-2 rounded bg-hermes-bg-tertiary p-2 text-xs text-hermes-text-secondary overflow-x-auto">
            {JSON.stringify(activity.details, null, 2)}
          </pre>
        )}
      </div>
    </div>
  )
}

export function Activity() {
  const { activities, agents, loadActivities } = useAppStore()

  return (
    <div className="space-y-4 animate-fade-in">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-hermes-text-primary">Activity Log</h1>
          <p className="text-sm text-hermes-text-muted">
            {activities.length} entries from {agents.length} agents
          </p>
        </div>
        <button
          onClick={() => loadActivities(100)}
          className="flex items-center gap-2 rounded-lg border border-hermes-border bg-hermes-bg-secondary px-4 py-2 text-sm font-medium text-hermes-text-primary hover:bg-hermes-bg-tertiary transition-colors"
        >
          <Clock className="h-4 w-4" />
          Refresh
        </button>
      </div>

      {/* Stats */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {(['message', 'tool_call', 'task_created', 'error'] as const).map(type => {
          const count = activities.filter(a => a.activity_type === type).length
          const Icon = ACTIVITY_ICONS[type]
          return (
            <div key={type} className="rounded-xl border border-hermes-border bg-hermes-bg-secondary p-4">
              <div className="flex items-center gap-3">
                <div className={cn(
                  "flex h-10 w-10 items-center justify-center rounded-lg",
                  ACTIVITY_COLORS[type]
                )}>
                  <Icon className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-2xl font-semibold text-hermes-text-primary">{count}</p>
                  <p className="text-xs text-hermes-text-muted capitalize">{type.replace(/_/g, ' ')}</p>
                </div>
              </div>
            </div>
          )
        })}
      </div>

      {/* Activity List */}
      <div className="rounded-xl border border-hermes-border bg-hermes-bg-secondary px-4">
        {activities.map((activity, index) => (
          <ActivityItem key={activity.id} activity={activity} index={index} />
        ))}
        
        {activities.length === 0 && (
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-hermes-bg-tertiary">
              <Clock className="h-8 w-8 text-hermes-text-muted" />
            </div>
            <p className="mt-4 text-lg font-medium text-hermes-text-primary">No activity yet</p>
            <p className="text-sm text-hermes-text-muted">Activity will appear here as you interact with agents</p>
          </div>
        )}
      </div>
    </div>
  )
}
