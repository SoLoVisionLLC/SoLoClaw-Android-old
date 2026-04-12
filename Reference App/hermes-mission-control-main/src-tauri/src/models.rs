use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentStatus {
    pub id: String,
    pub name: String,
    pub state: AgentState,
    pub model: Option<String>,
    pub provider: Option<String>,
    pub current_task: Option<String>,
    pub sub_agents_active: u32,
    pub last_seen: DateTime<Utc>,
    pub uptime_seconds: u64,
    pub message_count: u64,
    pub tool_calls_count: u64,
    // Profile fields
    pub avatar: Option<String>, // URL or base64 image
    pub bio: Option<String>,
    pub role: Option<String>, // e.g., "assistant", "researcher", "coder"
    pub capabilities: Vec<String>, // List of skills/tools
    pub group_id: Option<String>, // Belongs to group
    pub metadata: Option<serde_json::Value>, // Extra fields
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentGroup {
    pub id: String,
    pub name: String,
    pub description: Option<String>,
    pub icon: Option<String>,
    pub color: Option<String>,
    pub agent_ids: Vec<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum AgentState {
    Online,
    Working,
    Idle,
    Offline,
    Error,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Session {
    pub id: String,
    pub name: String,
    pub agent_id: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub message_count: u32,
    pub archived: bool,
    /// Hermes CLI session ID for conversation context continuity
    #[serde(default)]
    pub hermes_session_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    pub id: String,
    pub session_id: String,
    pub role: MessageRole,
    pub content: String,
    pub created_at: DateTime<Utc>,
    pub tool_calls: Option<Vec<ToolCall>>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum MessageRole {
    User,
    Assistant,
    System,
    Tool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolCall {
    pub id: String,
    pub name: String,
    pub arguments: serde_json::Value,
    pub result: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Task {
    pub id: String,
    pub title: String,
    pub description: Option<String>,
    pub status: TaskStatus,
    pub priority: TaskPriority,
    pub tags: Vec<String>,
    pub agent_id: Option<String>,
    pub session_id: Option<String>,
    pub source: Option<String>,
    pub department: Option<String>,
    pub blockers: Option<String>,
    pub notion_url: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub completed_at: Option<DateTime<Utc>>,
    pub due_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "snake_case")]
pub enum TaskStatus {
    #[default]
    Todo,
    InProgress,
    Done,
    Cancelled,
    Blocked,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Default)]
#[serde(rename_all = "snake_case")]
pub enum TaskPriority {
    #[default]
    Low,
    Medium,
    High,
    Critical,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Activity {
    pub id: String,
    pub agent_id: String,
    pub activity_type: ActivityType,
    pub message: String,
    pub details: Option<serde_json::Value>,
    pub timestamp: DateTime<Utc>,
    pub session_id: Option<String>,
    pub task_id: Option<String>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum ActivityType {
    Message,
    ToolCall,
    FileOperation,
    TaskCreated,
    TaskUpdated,
    TaskCompleted,
    AgentConnected,
    AgentDisconnected,
    Error,
    Warning,
    Info,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Note {
    pub id: String,
    pub title: String,
    pub content: String,
    pub agent_id: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub seen_by_agent: bool,
    pub pinned: bool,
    pub tags: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rule {
    pub id: String,
    pub title: String,
    pub content: String,
    pub category: String,
    pub agent_id: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub pinned: bool,
    pub tags: Vec<String>,
}

impl Rule {
    pub fn new(title: &str, content: &str, category: &str) -> Self {
        let now = Utc::now();
        Self {
            id: uuid::Uuid::new_v4().to_string(),
            title: title.to_string(),
            content: content.to_string(),
            category: category.to_string(),
            agent_id: None,
            created_at: now,
            updated_at: now,
            pinned: false,
            tags: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HermesInstance {
    pub id: String,
    pub name: String,
    pub url: String,
    pub version: Option<String>,
    pub status: InstanceStatus,
    pub last_seen: DateTime<Utc>,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum InstanceStatus {
    Online,
    Offline,
    Unknown,
}

impl AgentStatus {
    pub fn new(id: impl Into<String>, name: impl Into<String>) -> Self {
        let now = Utc::now();
        Self {
            id: id.into(),
            name: name.into(),
            state: AgentState::Idle,
            model: None,
            provider: None,
            current_task: None,
            sub_agents_active: 0,
            last_seen: now,
            uptime_seconds: 0,
            message_count: 0,
            tool_calls_count: 0,
            avatar: None,
            bio: None,
            role: Some("assistant".to_string()),
            capabilities: vec![],
            group_id: None,
            metadata: None,
        }
    }
}

impl Task {
    pub fn new(title: impl Into<String>) -> Self {
        let now = Utc::now();
        Self {
            id: Uuid::new_v4().to_string(),
            title: title.into(),
            description: None,
            status: TaskStatus::Todo,
            priority: TaskPriority::Medium,
            tags: vec![],
            agent_id: None,
            session_id: None,
            source: None,
            department: None,
            blockers: None,
            notion_url: None,
            created_at: now,
            updated_at: now,
            completed_at: None,
            due_at: None,
        }
    }
}

impl Activity {
    pub fn new(
        agent_id: impl Into<String>,
        activity_type: ActivityType,
        message: impl Into<String>,
    ) -> Self {
        Self {
            id: Uuid::new_v4().to_string(),
            agent_id: agent_id.into(),
            activity_type,
            message: message.into(),
            details: None,
            timestamp: Utc::now(),
            session_id: None,
            task_id: None,
        }
    }
}

impl Note {
    pub fn new(title: impl Into<String>, content: impl Into<String>) -> Self {
        let now = Utc::now();
        Self {
            id: Uuid::new_v4().to_string(),
            title: title.into(),
            content: content.into(),
            agent_id: None,
            created_at: now,
            updated_at: now,
            seen_by_agent: false,
            pinned: false,
            tags: vec![],
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CronJob {
    pub id: String,
    pub name: String,
    pub schedule: String,         // Cron expression like "0 9 * * *"
    pub command: String,          // The command/prompt to execute
    pub agent_id: Option<String>, // Which agent to run as
    pub session_target: String,   // "main" or "isolated"
    pub enabled: bool,
    pub delivery_mode: Option<String>, // "none", "announce", "webhook"
    pub delivery_target: Option<String>, // Channel or webhook URL
    pub provider: Option<String>,      // Model provider
    pub model: Option<String>,         // Model name
    pub last_run: Option<DateTime<Utc>>,
    pub next_run: Option<DateTime<Utc>>,
    pub failure_count: u32,
    pub consecutive_errors: u32,
    pub last_error: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CronJobRun {
    pub id: String,
    pub job_id: String,
    pub started_at: DateTime<Utc>,
    pub completed_at: Option<DateTime<Utc>>,
    pub success: bool,
    pub output: Option<String>,
    pub error: Option<String>,
}

impl CronJob {
    pub fn new(
        name: impl Into<String>,
        schedule: impl Into<String>,
        command: impl Into<String>,
    ) -> Self {
        let now = Utc::now();
        Self {
            id: Uuid::new_v4().to_string(),
            name: name.into(),
            schedule: schedule.into(),
            command: command.into(),
            agent_id: None,
            session_target: "main".to_string(),
            enabled: true,
            delivery_mode: Some("none".to_string()),
            delivery_target: None,
            provider: None,
            model: None,
            last_run: None,
            next_run: None,
            failure_count: 0,
            consecutive_errors: 0,
            last_error: None,
            created_at: now,
            updated_at: now,
        }
    }
}
