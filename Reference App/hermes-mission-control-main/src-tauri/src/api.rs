use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::{IntoResponse, Response, Sse},
    routing::{delete, get, patch, post},
    Json, Router,
};
use futures::Stream;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use std::{fs, path::PathBuf};
use tokio::sync::RwLock;
use tower_http::{
    cors::CorsLayer,
    services::{ServeDir, ServeFile},
};

use crate::{config::AppConfig, models::*, store::AppStore};

type SharedStore = Arc<RwLock<AppStore>>;

fn persist_store_snapshot(store: &AppStore) -> Result<(), ApiError> {
    store
        .persist()
        .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, e))
}

#[derive(Debug)]
struct ApiError(StatusCode, String);

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (self.0, Json(serde_json::json!({ "error": self.1 }))).into_response()
    }
}

type ApiResult<T> = Result<Json<T>, ApiError>;

#[derive(Debug, Deserialize)]
struct ListParams {
    #[serde(default)]
    limit: Option<usize>,
}

#[derive(Debug, Deserialize)]
struct SetAgentGroupPayload {
    agent_id: String,
    group_id: Option<String>,
}

#[derive(Debug, Deserialize)]
struct CreateGroupPayload {
    name: String,
    #[serde(default)]
    description: Option<String>,
    #[serde(default)]
    color: Option<String>,
}

#[derive(Debug, Deserialize)]
struct UpdateGroupPayload {
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    description: Option<String>,
    #[serde(default)]
    color: Option<String>,
}

#[derive(Debug, Deserialize)]
struct CreateTaskPayload {
    title: String,
    #[serde(default)]
    description: Option<String>,
    #[serde(default)]
    priority: Option<TaskPriority>,
    #[serde(default)]
    agent_id: Option<String>,
    #[serde(default)]
    source: Option<String>,
    #[serde(default)]
    department: Option<String>,
    #[serde(default)]
    blockers: Option<String>,
    #[serde(default)]
    due_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Deserialize)]
struct UpdateTaskPayload {
    #[serde(default)]
    title: Option<String>,
    #[serde(default)]
    description: Option<String>,
    #[serde(default)]
    status: Option<TaskStatus>,
    #[serde(default)]
    priority: Option<TaskPriority>,
    #[serde(default)]
    agent_id: Option<String>,
    #[serde(default)]
    source: Option<String>,
    #[serde(default)]
    department: Option<String>,
    #[serde(default)]
    blockers: Option<String>,
    #[serde(default)]
    due_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Deserialize)]
struct CreateNotePayload {
    title: String,
    content: String,
    #[serde(default)]
    agent_id: Option<String>,
}

#[derive(Debug, Deserialize)]
struct UpdateNotePayload {
    #[serde(default)]
    title: Option<String>,
    #[serde(default)]
    content: Option<String>,
    #[serde(default)]
    seen_by_agent: Option<bool>,
    #[serde(default)]
    pinned: Option<bool>,
}

#[derive(Debug, Deserialize)]
struct CreateSessionPayload {
    name: String,
    #[serde(default)]
    agent_id: Option<String>,
}

#[derive(Debug, Deserialize)]
struct UpdateSessionPayload {
    name: String,
}

#[derive(Debug, Deserialize, Clone)]
struct SendMessagePayload {
    session_id: String,
    content: String,
    agent_id: String,
}

#[derive(Debug, Deserialize)]
struct CreateCronJobPayload {
    name: String,
    schedule: String,
    command: String,
    #[serde(default)]
    agent_id: Option<String>,
    #[serde(default)]
    session_target: Option<String>,
    #[serde(default)]
    enabled: Option<bool>,
    #[serde(default)]
    delivery_mode: Option<String>,
    #[serde(default)]
    delivery_target: Option<String>,
    #[serde(default)]
    provider: Option<String>,
    #[serde(default)]
    model: Option<String>,
}

#[derive(Debug, Deserialize)]
struct UpdateCronJobPayload {
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    schedule: Option<String>,
    #[serde(default)]
    command: Option<String>,
    #[serde(default)]
    agent_id: Option<String>,
    #[serde(default)]
    session_target: Option<String>,
    #[serde(default)]
    enabled: Option<bool>,
    #[serde(default)]
    delivery_mode: Option<String>,
    #[serde(default)]
    delivery_target: Option<String>,
    #[serde(default)]
    provider: Option<String>,
    #[serde(default)]
    model: Option<String>,
}

#[derive(Debug, Serialize)]
struct SendMessageResponse {
    user_message: Message,
    agent_message: Message,
}

#[derive(Debug, Deserialize)]
struct HermesCronJob {
    id: String,
    name: String,
    prompt: String,
    #[serde(default)]
    agent_id: Option<String>,
    #[serde(default)]
    enabled: bool,
    schedule: HermesSchedule,
    #[serde(default)]
    last_run_at: Option<String>,
    #[serde(default)]
    next_run_at: Option<String>,
    #[serde(default)]
    last_status: Option<String>,
    #[serde(default)]
    last_error: Option<String>,
    #[serde(default)]
    created_at: Option<String>,
    #[serde(default)]
    model: Option<String>,
    #[serde(default)]
    provider: Option<String>,
    #[serde(default)]
    deliver: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(untagged)]
enum HermesSchedule {
    Cron { expr: String },
    Minutes { minutes: u32 },
    Raw(serde_json::Value),
}

impl HermesSchedule {
    fn to_cron_string(&self) -> String {
        match self {
            HermesSchedule::Cron { expr } => expr.clone(),
            HermesSchedule::Minutes { minutes } => format!("*/{} * * * *", minutes),
            HermesSchedule::Raw(v) => {
                // Fallback: try to extract anything that looks like a cron expr
                v.get("expr")
                    .and_then(|e| e.as_str())
                    .map(|s| s.to_string())
                    .unwrap_or_else(|| "* * * * *".to_string())
            }
        }
    }
}

#[derive(Debug, Deserialize)]
struct HermesCronFile {
    jobs: Vec<HermesCronJob>,
}

#[derive(Debug, Serialize)]
struct CronJobRunDetail {
    id: String,
    job_id: String,
    started_at: chrono::DateTime<chrono::Utc>,
    completed_at: Option<chrono::DateTime<chrono::Utc>>,
    success: bool,
    status: String,
    output: Option<String>,
    error: Option<String>,
    provider: Option<String>,
    model: Option<String>,
    session_id: Option<String>,
    session_key: Option<String>,
    duration_ms: Option<i64>,
    delivery_status: Option<String>,
}

fn parse_cron_output_sections(content: &str) -> (Option<String>, Option<String>, String) {
    let response = content
        .split("## Response")
        .nth(1)
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty() && s.trim() != "(No response generated)");

    let error_block = content
        .split("## Error")
        .nth(1)
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty());

    let lower = content.to_lowercase();
    let status = if lower.contains("(failed)")
        || lower.contains("## error")
        || lower.contains("status: blocked")
        || lower.contains("status: error")
        || lower.contains("**status: blocked")
    {
        "error".to_string()
    } else {
        "ok".to_string()
    };

    let error_line = error_block.or_else(|| {
        content
            .lines()
            .find(|line| {
                let l = line.to_lowercase();
                l.contains("root cause:")
                    || l.contains("error details")
                    || l.starts_with("status: blocked")
                    || l.starts_with("**status: blocked")
            })
            .map(|s| s.trim().to_string())
    });

    (response, error_line, status)
}

async fn get_cron_job_runs(
    State(store): State<SharedStore>,
    Path(job_id): Path<String>,
    Query(params): Query<ListParams>,
) -> ApiResult<Vec<CronJobRunDetail>> {
    use serde_json::Value;
    use std::path::PathBuf;

    let limit = params.limit.unwrap_or(24).min(100);
    let home = crate::store::home_dir();
    let output_dir = PathBuf::from(&home)
        .join(".hermes")
        .join("cron")
        .join("output")
        .join(&job_id);
    let session_dir = PathBuf::from(&home).join(".hermes").join("sessions");

    let agent_id = {
        let store = store.read().await;
        store
            .cron_jobs
            .get(&job_id)
            .and_then(|j| j.agent_id.clone())
    };

    let mut session_candidates: Vec<(Option<chrono::DateTime<chrono::Utc>>, Value)> = Vec::new();
    if let Ok(mut entries) = tokio::fs::read_dir(&session_dir).await {
        while let Ok(Some(entry)) = entries.next_entry().await {
            let path = entry.path();
            let Some(name) = path.file_name().and_then(|n| n.to_str()) else {
                continue;
            };
            if !name.starts_with(&format!("session_cron_{}", job_id)) || !name.ends_with(".json") {
                continue;
            }
            if let Ok(content) = tokio::fs::read_to_string(&path).await {
                if let Ok(value) = serde_json::from_str::<Value>(&content) {
                    let last_updated = value
                        .get("last_updated")
                        .and_then(|v| v.as_str())
                        .and_then(|s| chrono::DateTime::parse_from_rfc3339(s).ok())
                        .map(|dt| dt.with_timezone(&chrono::Utc));
                    session_candidates.push((last_updated, value));
                }
            }
        }
    }

    let mut runs = Vec::new();
    if let Ok(mut entries) = tokio::fs::read_dir(&output_dir).await {
        let mut files = Vec::new();
        while let Ok(Some(entry)) = entries.next_entry().await {
            let path = entry.path();
            if path.extension().and_then(|s| s.to_str()) == Some("md") {
                files.push(path);
            }
        }
        files.sort();
        files.reverse();

        for path in files.into_iter().take(limit) {
            let Some(stem) = path.file_stem().and_then(|s| s.to_str()) else {
                continue;
            };
            let completed_at = chrono::NaiveDateTime::parse_from_str(stem, "%Y-%m-%d_%H-%M-%S")
                .ok()
                .map(|dt| {
                    chrono::DateTime::<chrono::Utc>::from_naive_utc_and_offset(dt, chrono::Utc)
                });
            let content = tokio::fs::read_to_string(&path).await.unwrap_or_default();
            let (response, error_line, inferred_status) = parse_cron_output_sections(&content);

            let matched_session = completed_at.and_then(|target| {
                session_candidates
                    .iter()
                    .filter(|(ts, _)| {
                        ts.map(|t| {
                            (t.timestamp_millis() - target.timestamp_millis()).abs()
                                <= 15 * 60 * 1000
                        })
                        .unwrap_or(false)
                    })
                    .min_by_key(|(ts, _)| {
                        ts.map(|t| (t.timestamp_millis() - target.timestamp_millis()).abs())
                            .unwrap_or(i64::MAX)
                    })
            });

            let (started_at, session_id, provider, model, duration_ms) = if let Some((_, session)) =
                matched_session
            {
                let started = session
                    .get("session_start")
                    .and_then(|v| v.as_str())
                    .and_then(|s| {
                        chrono::NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S%.f").ok()
                    })
                    .map(|dt| {
                        chrono::DateTime::<chrono::Utc>::from_naive_utc_and_offset(dt, chrono::Utc)
                    })
                    .or(completed_at);
                let sid = session
                    .get("session_id")
                    .and_then(|v| v.as_str())
                    .map(|s| s.to_string());
                let model = session
                    .get("model")
                    .and_then(|v| v.as_str())
                    .map(|s| s.to_string());
                let provider = session.get("base_url").and_then(|v| v.as_str()).map(|url| {
                    if url.contains("openrouter") {
                        "openrouter".to_string()
                    } else if url.contains("minimax") {
                        "minimax".to_string()
                    } else if url.contains("googleapis") {
                        "google".to_string()
                    } else {
                        url.to_string()
                    }
                });
                let dur = match (started, completed_at) {
                    (Some(s), Some(c)) => Some((c - s).num_milliseconds()),
                    _ => None,
                };
                (started, sid, provider, model, dur)
            } else {
                (completed_at, None, None, None, None)
            };

            let status = inferred_status.clone();
            let success = status == "ok";
            let session_key = match (&agent_id, &session_id) {
                (Some(agent), Some(sid)) => {
                    Some(format!("agent:{}:cron:{}:run:{}", agent, job_id, sid))
                }
                _ => None,
            };

            runs.push(CronJobRunDetail {
                id: path
                    .file_name()
                    .and_then(|n| n.to_str())
                    .unwrap_or(stem)
                    .to_string(),
                job_id: job_id.clone(),
                started_at: started_at.or(completed_at).unwrap_or_else(chrono::Utc::now),
                completed_at,
                success,
                status,
                output: response,
                error: if success { None } else { error_line },
                provider,
                model,
                session_id,
                session_key,
                duration_ms,
                delivery_status: Some(if success {
                    "not-delivered".to_string()
                } else {
                    "error".to_string()
                }),
            });
        }
    }

    Ok(Json(runs))
}

fn parse_soul_md(content: &str) -> (Option<String>, Option<String>) {
    let mut role = None;
    let mut bio = None;

    for line in content.lines() {
        if line.starts_with("You are **") && line.contains("**") {
            if let Some(start) = line.find("**") {
                if let Some(end) = line.find("**,") {
                    role = Some(line[start + 2..end].trim().to_string());
                } else if let Some(end) = line.rfind("**") {
                    if end > start + 2 {
                        role = Some(line[start + 2..end].trim().to_string());
                    }
                }
            }
        }
        if bio.is_none()
            && role.is_some()
            && !line.trim().is_empty()
            && !line.starts_with('#')
            && !line.starts_with('-')
        {
            bio = Some(line.trim().to_string());
        }
    }

    (role, bio)
}

fn parse_config_yaml(content: &str) -> (Option<String>, Option<String>, Vec<String>) {
    let mut provider = None;
    let mut model = None;
    let mut capabilities = vec!["chat".to_string()];
    let mut in_model = false;
    let mut in_toolsets = false;

    for line in content.lines() {
        let trimmed = line.trim();

        if trimmed == "model:" {
            in_model = true;
            continue;
        }
        if trimmed == "toolsets:" || trimmed.starts_with("agent:") {
            in_model = false;
        }

        if in_model {
            if trimmed.starts_with("provider: ") {
                provider = Some(trimmed[10..].trim().to_string());
            }
            if trimmed.starts_with("default: ") {
                model = Some(trimmed[9..].trim().to_string());
            }
        }

        if trimmed == "toolsets:" {
            in_toolsets = true;
            capabilities.clear();
            continue;
        }
        if in_toolsets && trimmed.starts_with("- ") {
            capabilities.push(trimmed[2..].trim().to_string());
        }
        if in_toolsets
            && !trimmed.starts_with('-')
            && !trimmed.is_empty()
            && !trimmed.starts_with('#')
        {
            if trimmed.starts_with("agent:") || trimmed.starts_with("terminal:") {
                in_toolsets = false;
            }
        }
    }

    (provider, model, capabilities)
}

async fn discover_agents_from_filesystem(store: &mut AppStore) {
    use std::fs;

    let profiles_dir = crate::store::home_dir().join(".hermes").join("profiles");

    if let Ok(entries) = fs::read_dir(profiles_dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if !path.is_dir() {
                continue;
            }

            let agent_id = path
                .file_name()
                .and_then(|n| n.to_str())
                .unwrap_or("unknown")
                .to_string();
            let soul_path = path.join("SOUL.md");
            let (role, bio) = if let Ok(content) = fs::read_to_string(&soul_path) {
                parse_soul_md(&content)
            } else {
                (Some(agent_id.clone()), Some("Hermes Agent".to_string()))
            };

            let config_path = path.join("config.yaml");
            let (provider, model, capabilities) =
                if let Ok(content) = fs::read_to_string(&config_path) {
                    parse_config_yaml(&content)
                } else {
                    (
                        Some("unknown".to_string()),
                        Some("unknown".to_string()),
                        vec!["chat".to_string()],
                    )
                };

            let group_id = store.agents.get(&agent_id).and_then(|a| a.group_id.clone());

            // Load avatar from agent_avatars.json if present
            let avatar_url = {
                let avatars_path = crate::store::home_dir().join(".hermes").join("agent_avatars.json");
                if let Ok(content) = fs::read_to_string(&avatars_path) {
                    if let Ok(avatars) = serde_json::from_str::<serde_json::Value>(&content) {
                        avatars
                            .get(&agent_id)
                            .and_then(|v| v.as_str())
                            .map(|s| s.to_string())
                    } else {
                        None
                    }
                } else {
                    None
                }
            };

            let agent = AgentStatus {
                id: agent_id.clone(),
                name: agent_id.clone(),
                state: AgentState::Online,
                model,
                provider,
                current_task: None,
                sub_agents_active: 0,
                last_seen: Utc::now(),
                uptime_seconds: 0,
                message_count: store
                    .agents
                    .get(&agent_id)
                    .map(|a| a.message_count)
                    .unwrap_or(0),
                tool_calls_count: store
                    .agents
                    .get(&agent_id)
                    .map(|a| a.tool_calls_count)
                    .unwrap_or(0),
                avatar: avatar_url,
                bio,
                role,
                capabilities,
                group_id,
                metadata: None,
            };

            store.agents.insert(agent_id.clone(), agent.clone());
            store.add_activity(Activity::new(
                agent_id,
                ActivityType::AgentConnected,
                format!("Loaded Hermes agent: {}", agent.name),
            ));
        }
    }
}

async fn sync_cron_jobs_from_file(store: &mut AppStore) {
    use std::path::Path;

    let home = crate::store::home_dir();
    let cron_file = Path::new(&home)
        .join(".hermes")
        .join("cron")
        .join("jobs.json");

    if !cron_file.exists() {
        return;
    }

    let Ok(content) = tokio::fs::read_to_string(&cron_file).await else {
        return;
    };
    let Ok(cron_file_data) = serde_json::from_str::<HermesCronFile>(&content) else {
        return;
    };

    for hermes_job in cron_file_data.jobs {
        let created_at = hermes_job
            .created_at
            .and_then(|s| chrono::DateTime::parse_from_rfc3339(&s).ok())
            .map(|dt| dt.with_timezone(&chrono::Utc))
            .unwrap_or_else(Utc::now);

        let last_run = hermes_job
            .last_run_at
            .and_then(|s| chrono::DateTime::parse_from_rfc3339(&s).ok())
            .map(|dt| dt.with_timezone(&chrono::Utc));

        let next_run = hermes_job
            .next_run_at
            .and_then(|s| chrono::DateTime::parse_from_rfc3339(&s).ok())
            .map(|dt| dt.with_timezone(&chrono::Utc));

        let consecutive_errors = matches!(hermes_job.last_status.as_deref(), Some("error"))
            .then_some(1)
            .unwrap_or(0);

        let job = CronJob {
            id: hermes_job.id.clone(),
            name: hermes_job.name,
            schedule: hermes_job.schedule.to_cron_string(),
            command: hermes_job.prompt,
            agent_id: hermes_job.agent_id,
            session_target: "main".to_string(),
            enabled: hermes_job.enabled,
            delivery_mode: hermes_job.deliver,
            delivery_target: None,
            provider: hermes_job.provider,
            model: hermes_job.model,
            last_run,
            next_run,
            failure_count: if consecutive_errors > 0 { 1 } else { 0 },
            consecutive_errors,
            last_error: hermes_job.last_error,
            created_at,
            updated_at: Utc::now(),
        };

        store.cron_jobs.insert(job.id.clone(), job);
    }
}

async fn invoke_hermes_chat(
    agent_id: &str,
    message: &str,
    hermes_session_id: Option<&str>,
) -> Result<(String, String, Vec<crate::models::ToolCall>), ApiError> {
    use std::process::Stdio;
    use tokio::process::Command;

    let home = crate::store::home_dir();
    let profile_dir = std::path::Path::new(&home)
        .join(".hermes")
        .join("profiles")
        .join(agent_id);

    // Check if profile directory exists
    if !profile_dir.exists() {
        return Err(ApiError(
            StatusCode::NOT_FOUND,
            format!("Agent profile not found: {}", profile_dir.display()),
        ));
    }

    let escaped_msg = message.replace('"', "\\\"");
    
    // Find hermes binary - check common locations
    let hermes_bin = if std::path::Path::new("/usr/local/bin/hermes").exists() {
        "/usr/local/bin/hermes".to_string()
    } else if std::path::Path::new("/usr/bin/hermes").exists() {
        "/usr/bin/hermes".to_string()
    } else if std::path::Path::new(&format!("{}/.local/bin/hermes", home.display())).exists() {
        format!("{}/.local/bin/hermes", home.display())
    } else if std::path::Path::new(&format!("{}/.cargo/bin/hermes", home.display())).exists() {
        format!("{}/.cargo/bin/hermes", home.display())
    } else {
        "hermes".to_string() // fallback to PATH
    };
    
    // Use -q (quiet) flag for non-interactive mode, -Q for session_id output
    let hermes_cmd = if let Some(sid) = hermes_session_id {
        format!(
            "{} chat -q \"{}\" -Q --resume {} --source mission-control",
            hermes_bin, escaped_msg, sid
        )
    } else {
        format!("{} chat -q \"{}\" -Q --source mission-control", hermes_bin, escaped_msg)
    };

    let output = Command::new("bash")
        .arg("-c")
        .arg(&hermes_cmd)
        .env("HOME", &home)
        .env("HERMES_HOME", profile_dir.display().to_string())
        .current_dir(&profile_dir)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .output()
        .await
        .map_err(|e| {
            ApiError(
                StatusCode::INTERNAL_SERVER_ERROR,
                format!("Failed to execute hermes chat: {e}"),
            )
        })?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    let combined_output = format!("{}\n{}", stdout, stderr);

    if !output.status.success() && !stdout.contains("session_id:") {
        return Err(ApiError(
            StatusCode::BAD_GATEWAY,
            format!("Hermes CLI error (exit {}): stdout='{}' stderr='{}'", 
                output.status.code().map_or("?".to_string(), |c| c.to_string()),
                stdout.chars().take(200).collect::<String>(),
                stderr.chars().take(200).collect::<String>()
            ),
        ));
    }

    // Note: -q mode doesn't show tool calls, so tool_calls will be empty
    // To capture tools, we'd need to use a different approach (e.g., hermes verbose mode or logging)
    let (cleaned, session_id, _tool_calls) = clean_hermes_output_with_session_and_tools(&combined_output);
    eprintln!("[DEBUG] invoke_hermes_chat: extracted session_id='{}' for agent='{}'", session_id, agent_id);
    Ok((cleaned, session_id, Vec::new()))
}

fn clean_hermes_output_with_session(output: &str) -> (String, String) {
    let (cleaned, session_id, _) = clean_hermes_output_with_session_and_tools(output);
    (cleaned, session_id)
}

/// Parse tool calls from Hermes CLI output
/// Captures tool usage patterns from verbose CLI output while preserving response content
fn clean_hermes_output_with_session_and_tools(output: &str) -> (String, String, Vec<crate::models::ToolCall>) {
    use crate::models::ToolCall;
    use serde_json::json;
    use uuid::Uuid;

    let mut session_id = String::new();
    let mut tool_calls: Vec<ToolCall> = Vec::new();
    let mut result_lines: Vec<&str> = Vec::new();

    for line in output.lines() {
        let trimmed = line.trim();

        // Extract session_id if present
        if trimmed.starts_with("session_id:") {
            session_id = trimmed["session_id:".len()..].trim().to_string();
            continue;
        }

        // Skip UI decoration lines (keep original logic)
        if line.contains('⚕')
            || line.starts_with('╭')
            || line.starts_with('╰')
            || line.starts_with('┊')
            || line.contains("────")
            || line.contains("preparing")
            || line.contains("mcp_memory")
            || line.contains("memory +user")
            || (line.contains("0.0s") && !line.contains("Tool:"))
        {
            continue;
        }

        // Look for tool call patterns - be very specific to avoid catching response content
        // Only match clear tool indicators, not lines that might be part of the response
        let is_tool_invocation = trimmed.starts_with("> ") && trimmed.contains('(') && 
            (trimmed.contains("mcp_") || trimmed.contains("search") || trimmed.contains("read_") || 
             trimmed.contains("write_") || trimmed.contains("execute_") || trimmed.contains("list_"));
        
        let has_explicit_tool_marker = trimmed.starts_with("tool:") || 
            trimmed.starts_with("Tool:") ||
            trimmed.starts_with("Using tool:") ||
            trimmed.starts_with("Calling tool:");
        
        let has_tool_emoji = line.contains('🛠') || line.contains('🔧') || line.contains('⚡');

        if is_tool_invocation || has_explicit_tool_marker || has_tool_emoji {
            // Extract tool name - look for patterns like "tool_name(args)"
            let tool_name = if let Some(start) = trimmed.find("mcp_") {
                let end = trimmed[start..].find(|c: char| c.is_whitespace() || c == '(' || c == ')')
                    .map(|i| start + i)
                    .unwrap_or(trimmed.len());
                trimmed[start..end].to_string()
            } else if let Some(pos) = trimmed.find('(') {
                let start = trimmed[..pos].rfind(|c: char| c.is_whitespace() || c == '>')
                    .map(|i| i + 1)
                    .unwrap_or(0);
                trimmed[start..pos].trim().to_string()
            } else if has_explicit_tool_marker {
                let parts: Vec<&str> = trimmed.splitn(2, ':').collect();
                if parts.len() > 1 {
                    parts[1].trim().split_whitespace().next().unwrap_or("unknown").to_string()
                } else {
                    "unknown".to_string()
                }
            } else {
                trimmed.split_whitespace().next().unwrap_or("unknown").to_string()
            };

            // Extract arguments if present
            let args = if let Some(start) = trimmed.find('(') {
                if let Some(end) = trimmed[start..].find(')') {
                    let args_str = &trimmed[start + 1..start + end];
                    let mut arg_map = serde_json::Map::new();
                    for pair in args_str.split(',') {
                        if let Some(eq_pos) = pair.find('=') {
                            let key = pair[..eq_pos].trim().to_string();
                            let val = pair[eq_pos + 1..].trim().trim_matches('"').to_string();
                            arg_map.insert(key, json!(val));
                        }
                    }
                    json!(arg_map)
                } else {
                    json!({})
                }
            } else {
                json!({})
            };

            tool_calls.push(ToolCall {
                id: Uuid::new_v4().to_string(),
                name: tool_name,
                arguments: args,
                result: None,
            });
            continue; // Don't include tool lines in main output
        }

        // Check for tool result indicators - only if we have active tool calls
        if !tool_calls.is_empty() && (trimmed.starts_with("Result:") || trimmed.starts_with("Output:")) {
            if let Some(last_tool) = tool_calls.last_mut() {
                let result_text = trimmed.splitn(2, ':').nth(1).unwrap_or("").trim().to_string();
                if !result_text.is_empty() {
                    last_tool.result = Some(result_text);
                }
            }
            continue;
        }

        // Keep all other lines (including the actual response!)
        result_lines.push(line);
    }

    let result = result_lines.join("\n").trim().to_string();

    (result, session_id, tool_calls)
}

/// Sort agents in the canonical order: Halo, Haven, Elon, Orion, Vector, Sterling, Atlas,
/// Dev, Chip, Quill, Forge, Luma, Nova, Chase, Pulse, Snip, Canon, Knox, Sentinel, Ledger, Conductor
fn sort_agents_by_canonical_order(agents: &mut Vec<AgentStatus>) {
    let order = [
        "halo", "haven", "elon", "orion", "vector", "sterling", "atlas",
        "dev", "chip", "quill", "forge", "luma", "nova", "chase", "pulse",
        "snip", "canon", "knox", "sentinel", "ledger", "conductor",
    ];

    agents.sort_by_key(|a| {
        let id_lower = a.id.to_lowercase();
        order
            .iter()
            .position(|&o| o == id_lower)
            .unwrap_or(usize::MAX)
    });
}

async fn get_agents(State(store): State<SharedStore>) -> ApiResult<Vec<AgentStatus>> {
    let mut store = store.write().await;
    if store.agents.is_empty() {
        discover_agents_from_filesystem(&mut store).await;
    }
    let mut agents: Vec<AgentStatus> = store.agents.values().cloned().collect();
    sort_agents_by_canonical_order(&mut agents);
    Ok(Json(agents))
}

async fn discover_agents(State(store): State<SharedStore>) -> ApiResult<Vec<AgentStatus>> {
    let mut store = store.write().await;
    discover_agents_from_filesystem(&mut store).await;
    let mut agents: Vec<AgentStatus> = store.agents.values().cloned().collect();
    sort_agents_by_canonical_order(&mut agents);
    Ok(Json(agents))
}

async fn update_agent_profile(
    State(store): State<SharedStore>,
    Path(agent_id): Path<String>,
    Json(payload): Json<serde_json::Value>,
) -> ApiResult<AgentStatus> {
    let mut store = store.write().await;
    let agent = store.agents.get_mut(&agent_id).ok_or_else(|| {
        ApiError(
            StatusCode::NOT_FOUND,
            format!("Agent not found: {agent_id}"),
        )
    })?;

    if let Some(name) = payload.get("name").and_then(|v| v.as_str()) {
        agent.name = name.to_string();
    }
    if let Some(bio) = payload.get("bio").and_then(|v| v.as_str()) {
        agent.bio = Some(bio.to_string());
    }
    if let Some(role) = payload.get("role").and_then(|v| v.as_str()) {
        agent.role = Some(role.to_string());
    }
    if let Some(avatar) = payload.get("avatar").and_then(|v| v.as_str()) {
        agent.avatar = Some(avatar.to_string());
    }
    if let Some(caps) = payload.get("capabilities").and_then(|v| v.as_array()) {
        agent.capabilities = caps
            .iter()
            .filter_map(|v| v.as_str().map(ToString::to_string))
            .collect();
    }

    // Persist the updated avatar back to agent_avatars.json so it survives restarts
    if let Some(avatar) = &agent.avatar {
        let avatars_path = crate::store::home_dir().join(".hermes").join("agent_avatars.json");
        let avatars: serde_json::Map<String, serde_json::Value> =
            if let Ok(content) = fs::read_to_string(&avatars_path) {
                serde_json::from_str(&content).unwrap_or_default()
            } else {
                serde_json::Map::new()
            };
        let mut updated = avatars;
        updated.insert(agent_id.clone(), serde_json::Value::String(avatar.clone()));
        if let Some(parent) = avatars_path.parent() {
            let _ = fs::create_dir_all(parent);
        }
        if let Err(e) = fs::write(&avatars_path, serde_json::to_string_pretty(&updated).unwrap()) {
            eprintln!("Failed to persist agent_avatars.json: {e}");
        }
    }

    Ok(Json(agent.clone()))
}

async fn set_agent_group(
    State(store): State<SharedStore>,
    Json(payload): Json<SetAgentGroupPayload>,
) -> ApiResult<AgentStatus> {
    let mut store = store.write().await;

    let old_group_id = store
        .agents
        .get(&payload.agent_id)
        .and_then(|a| a.group_id.clone());
    if let Some(old_gid) = old_group_id {
        if let Some(group) = store.groups.get_mut(&old_gid) {
            group.agent_ids.retain(|id| id != &payload.agent_id);
        }
    }

    if let Some(gid) = &payload.group_id {
        if let Some(group) = store.groups.get_mut(gid) {
            if !group.agent_ids.contains(&payload.agent_id) {
                group.agent_ids.push(payload.agent_id.clone());
            }
        }
    }

    let agent = store.agents.get_mut(&payload.agent_id).ok_or_else(|| {
        ApiError(
            StatusCode::NOT_FOUND,
            format!("Agent not found: {}", payload.agent_id),
        )
    })?;
    agent.group_id = payload.group_id;
    Ok(Json(agent.clone()))
}

async fn get_groups(State(store): State<SharedStore>) -> ApiResult<Vec<AgentGroup>> {
    let store = store.read().await;
    Ok(Json(store.groups.values().cloned().collect()))
}

async fn create_group(
    State(store): State<SharedStore>,
    Json(payload): Json<CreateGroupPayload>,
) -> ApiResult<AgentGroup> {
    let mut store = store.write().await;
    let group = AgentGroup {
        id: uuid::Uuid::new_v4().to_string(),
        name: payload.name,
        description: payload.description,
        icon: None,
        color: payload.color,
        agent_ids: vec![],
        created_at: Utc::now(),
        updated_at: Utc::now(),
    };
    store.groups.insert(group.id.clone(), group.clone());
    Ok(Json(group))
}

async fn update_group(
    State(store): State<SharedStore>,
    Path(group_id): Path<String>,
    Json(payload): Json<UpdateGroupPayload>,
) -> ApiResult<AgentGroup> {
    let mut store = store.write().await;
    let group = store.groups.get_mut(&group_id).ok_or_else(|| {
        ApiError(
            StatusCode::NOT_FOUND,
            format!("Group not found: {group_id}"),
        )
    })?;

    if let Some(name) = payload.name {
        group.name = name;
    }
    if let Some(description) = payload.description {
        group.description = Some(description);
    }
    if let Some(color) = payload.color {
        group.color = Some(color);
    }
    group.updated_at = Utc::now();
    Ok(Json(group.clone()))
}

async fn delete_group(
    State(store): State<SharedStore>,
    Path(group_id): Path<String>,
) -> Result<StatusCode, ApiError> {
    let mut store = store.write().await;
    for agent in store.agents.values_mut() {
        if agent.group_id == Some(group_id.clone()) {
            agent.group_id = None;
        }
    }
    store.groups.remove(&group_id).ok_or_else(|| {
        ApiError(
            StatusCode::NOT_FOUND,
            format!("Group not found: {group_id}"),
        )
    })?;
    Ok(StatusCode::NO_CONTENT)
}


const NOTION_TASK_BOARD_DB_ID: &str = "8e85701f-81a6-490f-a859-5c0bc9e52827";
const NOTION_VERSION: &str = "2022-06-28";

fn notion_token() -> Option<String> {
    if let Ok(value) = std::env::var("NOTION_API_KEY") {
        let trimmed = value.trim();
        if !trimmed.is_empty() { return Some(trimmed.to_string()); }
    }
    let home = crate::store::home_dir();
    let direct = PathBuf::from(&home).join(".config/notion/api_key");
    if let Ok(value) = fs::read_to_string(&direct) {
        let trimmed = value.trim();
        if !trimmed.is_empty() { return Some(trimmed.to_string()); }
    }
    let secrets = PathBuf::from(&home).join(".openclaw/secrets.json");
    if let Ok(raw) = fs::read_to_string(secrets) {
        if let Ok(json) = serde_json::from_str::<serde_json::Value>(&raw) {
            if let Some(token) = json.get("skills").and_then(|v| v.get("entries")).and_then(|v| v.get("notion")).and_then(|v| v.get("apiKey")).and_then(|v| v.as_str()) {
                let trimmed = token.trim();
                if !trimmed.is_empty() { return Some(trimmed.to_string()); }
            }
        }
    }
    None
}

fn notion_status_to_task_status(value: &str) -> TaskStatus {
    match value {
        "In Progress" => TaskStatus::InProgress,
        "Done" => TaskStatus::Done,
        "Blocked" => TaskStatus::Blocked,
        "Cancelled" | "Canceled" => TaskStatus::Cancelled,
        _ => TaskStatus::Todo,
    }
}

fn task_status_to_notion_status(value: TaskStatus) -> &'static str {
    match value {
        TaskStatus::InProgress => "In Progress",
        TaskStatus::Done => "Done",
        TaskStatus::Blocked => "To Do",
        TaskStatus::Cancelled => "To Do",
        TaskStatus::Todo => "To Do",
    }
}

fn notion_priority_to_task_priority(value: Option<&str>) -> TaskPriority {
    match value.unwrap_or("") {
        v if v.contains("P0") || v.eq_ignore_ascii_case("critical") => TaskPriority::Critical,
        v if v.contains("P1") || v.eq_ignore_ascii_case("high") => TaskPriority::High,
        v if v.contains("P3") || v.eq_ignore_ascii_case("low") => TaskPriority::Low,
        _ => TaskPriority::Medium,
    }
}

fn task_priority_to_notion_priority(value: TaskPriority) -> &'static str {
    match value {
        TaskPriority::Critical => "P0 Critical",
        TaskPriority::High => "P1 High",
        TaskPriority::Medium => "P2 Medium",
        TaskPriority::Low => "P3 Low",
    }
}

fn task_from_notion_page(page: &serde_json::Value) -> Task {
    let props = page.get("properties").and_then(|v| v.as_object()).cloned().unwrap_or_default();
    let title = props.get("Task")
        .and_then(|v| v.get("title"))
        .and_then(|v| v.as_array())
        .and_then(|arr| arr.first())
        .and_then(|v| v.get("plain_text"))
        .and_then(|v| v.as_str())
        .unwrap_or("Untitled")
        .to_string();
    let description = props.get("Notes")
        .and_then(|v| v.get("rich_text"))
        .and_then(|v| v.as_array())
        .map(|arr| arr.iter().filter_map(|x| x.get("plain_text").and_then(|v| v.as_str())).collect::<Vec<_>>().join("\n"))
        .filter(|s| !s.is_empty());
    let status_name = props.get("Status").and_then(|v| v.get("select")).and_then(|v| v.get("name")).and_then(|v| v.as_str()).unwrap_or("To Do");
    let priority_name = props.get("Priority").and_then(|v| v.get("select")).and_then(|v| v.get("name")).and_then(|v| v.as_str());
    let agent = props.get("Assigned Agent").and_then(|v| v.get("select")).and_then(|v| v.get("name")).and_then(|v| v.as_str()).map(|s| s.to_lowercase());
    let source = props.get("Source").and_then(|v| v.get("rich_text")).and_then(|v| v.as_array()).map(|arr| arr.iter().filter_map(|x| x.get("plain_text").and_then(|v| v.as_str())).collect::<Vec<_>>().join("
")).filter(|s| !s.is_empty());
    let department = props.get("Department").and_then(|v| v.get("select")).and_then(|v| v.get("name")).and_then(|v| v.as_str()).map(|s| s.to_string());
    let blockers = props.get("Blockers").and_then(|v| v.get("rich_text")).and_then(|v| v.as_array()).map(|arr| arr.iter().filter_map(|x| x.get("plain_text").and_then(|v| v.as_str())).collect::<Vec<_>>().join("
")).filter(|s| !s.is_empty());
    let due_at = props.get("Due Date").and_then(|v| v.get("date")).and_then(|v| v.get("start")).and_then(|v| v.as_str()).and_then(|s| DateTime::parse_from_rfc3339(&format!("{s}T00:00:00+00:00")).ok()).map(|d| d.with_timezone(&Utc));
    let created_at = page.get("created_time").and_then(|v| v.as_str()).and_then(|s| DateTime::parse_from_rfc3339(s).ok()).map(|d| d.with_timezone(&Utc)).unwrap_or_else(Utc::now);
    let updated_at = page.get("last_edited_time").and_then(|v| v.as_str()).and_then(|s| DateTime::parse_from_rfc3339(s).ok()).map(|d| d.with_timezone(&Utc)).unwrap_or_else(Utc::now);
    let completed_at = if notion_status_to_task_status(status_name) == TaskStatus::Done { Some(updated_at) } else { None };
    Task {
        id: page.get("id").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
        title,
        description,
        status: notion_status_to_task_status(status_name),
        priority: notion_priority_to_task_priority(priority_name),
        tags: vec!["notion".to_string()],
        agent_id: agent,
        session_id: None,
        source,
        department,
        blockers,
        notion_url: page.get("url").and_then(|v| v.as_str()).map(|s| s.to_string()),
        created_at,
        updated_at,
        completed_at,
        due_at,
    }
}


async fn fetch_notion_task_by_id(page_id: &str) -> Result<Task, ApiError> {
    let Some(token) = notion_token() else { return Err(ApiError(StatusCode::BAD_REQUEST, "Notion credential unavailable".to_string())) };
    let client = reqwest::Client::new();
    let response = client
        .get(format!("https://api.notion.com/v1/pages/{}", page_id))
        .header("Authorization", format!("Bearer {}", token))
        .header("Notion-Version", NOTION_VERSION)
        .send()
        .await
        .map_err(|e| ApiError(StatusCode::BAD_GATEWAY, format!("Failed to fetch Notion task page: {e}")))?;
    if !response.status().is_success() {
        let text = response.text().await.unwrap_or_default();
        return Err(ApiError(StatusCode::BAD_GATEWAY, format!("Notion task page fetch failed: {text}")));
    }
    let page: serde_json::Value = response.json().await.map_err(|e| ApiError(StatusCode::BAD_GATEWAY, format!("Failed to decode Notion page response: {e}")))?;
    Ok(task_from_notion_page(&page))
}

async fn fetch_notion_tasks() -> Result<Option<Vec<Task>>, ApiError> {
    let Some(token) = notion_token() else { return Ok(None) };
    let client = reqwest::Client::new();
    let response = client
        .post(format!("https://api.notion.com/v1/databases/{}/query", NOTION_TASK_BOARD_DB_ID))
        .header("Authorization", format!("Bearer {}", token))
        .header("Notion-Version", NOTION_VERSION)
        .json(&serde_json::json!({"page_size": 100}))
        .send()
        .await
        .map_err(|e| ApiError(StatusCode::BAD_GATEWAY, format!("Failed to query Notion Task Board: {e}")))?;
    if !response.status().is_success() {
        let text = response.text().await.unwrap_or_default();
        return Err(ApiError(StatusCode::BAD_GATEWAY, format!("Notion Task Board query failed: {text}")));
    }
    let payload: serde_json::Value = response.json().await.map_err(|e| ApiError(StatusCode::BAD_GATEWAY, format!("Failed to decode Notion Task Board response: {e}")))?;
    let mut tasks = payload.get("results").and_then(|v| v.as_array()).cloned().unwrap_or_default().iter().map(task_from_notion_page).collect::<Vec<_>>();
    tasks.sort_by(|a, b| b.updated_at.cmp(&a.updated_at));
    Ok(Some(tasks))
}

async fn update_notion_task_page(page_id: &str, properties: serde_json::Value) -> Result<(), ApiError> {
    let Some(token) = notion_token() else { return Err(ApiError(StatusCode::BAD_REQUEST, "Notion credential unavailable".to_string())) };
    let client = reqwest::Client::new();
    let response = client
        .patch(format!("https://api.notion.com/v1/pages/{}", page_id))
        .header("Authorization", format!("Bearer {}", token))
        .header("Notion-Version", NOTION_VERSION)
        .json(&serde_json::json!({"properties": properties}))
        .send()
        .await
        .map_err(|e| ApiError(StatusCode::BAD_GATEWAY, format!("Failed to update Notion task: {e}")))?;
    if !response.status().is_success() {
        let text = response.text().await.unwrap_or_default();
        return Err(ApiError(StatusCode::BAD_GATEWAY, format!("Notion task update failed: {text}")));
    }
    Ok(())
}

async fn create_notion_task(payload: &CreateTaskPayload) -> Result<Task, ApiError> {
    let Some(token) = notion_token() else { return Err(ApiError(StatusCode::BAD_REQUEST, "Notion credential unavailable".to_string())) };
    let client = reqwest::Client::new();
    let assigned_name = payload.agent_id.as_deref().map(|s| {
        let mut chars = s.chars();
        match chars.next() { Some(first) => first.to_uppercase().collect::<String>() + chars.as_str(), None => String::new() }
    });
    let mut properties = serde_json::Map::new();
    properties.insert("Task".to_string(), serde_json::json!({"title": [{"text": {"content": payload.title}}]}));
    properties.insert("Status".to_string(), serde_json::json!({"select": {"name": "To Do"}}));
    properties.insert("Priority".to_string(), serde_json::json!({"select": {"name": task_priority_to_notion_priority(payload.priority.unwrap_or(TaskPriority::Medium))}}));
    properties.insert("Source".to_string(), serde_json::json!({"rich_text": [{"text": {"content": "Mission Control"}}]}));
    if let Some(description) = &payload.description {
        if !description.trim().is_empty() {
            properties.insert("Notes".to_string(), serde_json::json!({"rich_text": [{"text": {"content": description}}]}));
        }
    }
    if let Some(name) = assigned_name.as_ref() {
        if !name.is_empty() {
            properties.insert("Assigned Agent".to_string(), serde_json::json!({"select": {"name": name}}));
        }
    }
    if let Some(source) = &payload.source {
        if !source.trim().is_empty() {
            properties.insert("Source".to_string(), serde_json::json!({"rich_text": [{"text": {"content": source}}]}));
        }
    }
    if let Some(department) = &payload.department {
        if !department.trim().is_empty() {
            properties.insert("Department".to_string(), serde_json::json!({"select": {"name": department}}));
        }
    }
    if let Some(blockers) = &payload.blockers {
        if !blockers.trim().is_empty() {
            properties.insert("Blockers".to_string(), serde_json::json!({"rich_text": [{"text": {"content": blockers}}]}));
        }
    }
    if let Some(due_at) = payload.due_at {
        properties.insert("Due Date".to_string(), serde_json::json!({"date": {"start": due_at.format("%Y-%m-%d").to_string()}}));
    }
    let response = client
        .post("https://api.notion.com/v1/pages")
        .header("Authorization", format!("Bearer {}", token))
        .header("Notion-Version", NOTION_VERSION)
        .json(&serde_json::json!({
            "parent": {"database_id": NOTION_TASK_BOARD_DB_ID},
            "properties": serde_json::Value::Object(properties)
        }))
        .send()
        .await
        .map_err(|e| ApiError(StatusCode::BAD_GATEWAY, format!("Failed to create Notion task: {e}")))?;
    if !response.status().is_success() {
        let text = response.text().await.unwrap_or_default();
        return Err(ApiError(StatusCode::BAD_GATEWAY, format!("Notion task creation failed: {text}")));
    }
    let page: serde_json::Value = response.json().await.map_err(|e| ApiError(StatusCode::BAD_GATEWAY, format!("Failed to decode Notion task creation response: {e}")))?;
    Ok(task_from_notion_page(&page))
}

async fn archive_notion_task(page_id: &str) -> Result<(), ApiError> {
    let Some(token) = notion_token() else { return Err(ApiError(StatusCode::BAD_REQUEST, "Notion credential unavailable".to_string())) };
    let client = reqwest::Client::new();
    let response = client
        .patch(format!("https://api.notion.com/v1/pages/{}", page_id))
        .header("Authorization", format!("Bearer {}", token))
        .header("Notion-Version", NOTION_VERSION)
        .json(&serde_json::json!({"archived": true}))
        .send()
        .await
        .map_err(|e| ApiError(StatusCode::BAD_GATEWAY, format!("Failed to archive Notion task: {e}")))?;
    if !response.status().is_success() {
        let text = response.text().await.unwrap_or_default();
        return Err(ApiError(StatusCode::BAD_GATEWAY, format!("Notion task archive failed: {text}")));
    }
    Ok(())
}

async fn get_tasks(State(store): State<SharedStore>) -> ApiResult<Vec<Task>> {
    if let Some(tasks) = fetch_notion_tasks().await? {
        return Ok(Json(tasks));
    }
    let store = store.read().await;
    let mut tasks: Vec<Task> = store.tasks.values().cloned().collect();
    tasks.sort_by(|a, b| b.updated_at.cmp(&a.updated_at));
    Ok(Json(tasks))
}

async fn create_task(
    State(store): State<SharedStore>,
    Json(payload): Json<CreateTaskPayload>,
) -> ApiResult<Task> {
    if notion_token().is_some() {
        let task = create_notion_task(&payload).await?;
        return Ok(Json(task));
    }
    let mut store = store.write().await;
    let mut task = Task::new(payload.title);
    task.description = payload.description;
    task.priority = payload.priority.unwrap_or(TaskPriority::Medium);
    task.agent_id = payload.agent_id.clone();
    task.source = payload.source.clone();
    task.department = payload.department.clone();
    task.blockers = payload.blockers.clone();
    task.due_at = payload.due_at;
    store.tasks.insert(task.id.clone(), task.clone());
    store.add_activity(Activity::new(
        payload.agent_id.unwrap_or_else(|| "system".to_string()),
        ActivityType::TaskCreated,
        format!("Created task: {}", task.title),
    ));
    Ok(Json(task))
}

async fn update_task(
    State(store): State<SharedStore>,
    Path(task_id): Path<String>,
    Json(payload): Json<UpdateTaskPayload>,
) -> ApiResult<Task> {
    if notion_token().is_some() && task_id.contains('-') {
        let mut properties = serde_json::Map::new();
        if let Some(title) = &payload.title {
            properties.insert("Task".to_string(), serde_json::json!({"title": [{"text": {"content": title}}]}));
        }
        if let Some(description) = &payload.description {
            properties.insert("Notes".to_string(), serde_json::json!({"rich_text": [{"text": {"content": description}}]}));
        }
        if let Some(status) = payload.status {
            properties.insert("Status".to_string(), serde_json::json!({"select": {"name": task_status_to_notion_status(status)}}));
        }
        if let Some(priority) = payload.priority {
            properties.insert("Priority".to_string(), serde_json::json!({"select": {"name": task_priority_to_notion_priority(priority)}}));
        }
        if let Some(agent_id) = &payload.agent_id {
            let mut chars = agent_id.chars();
            let agent_name = match chars.next() { Some(first) => first.to_uppercase().collect::<String>() + chars.as_str(), None => String::new() };
            if !agent_name.is_empty() {
                properties.insert("Assigned Agent".to_string(), serde_json::json!({"select": {"name": agent_name}}));
            }
        }
        if let Some(source) = &payload.source {
            properties.insert("Source".to_string(), serde_json::json!({"rich_text": [{"text": {"content": source}}]}));
        }
        if let Some(department) = &payload.department {
            properties.insert("Department".to_string(), serde_json::json!({"select": {"name": department}}));
        }
        if let Some(blockers) = &payload.blockers {
            properties.insert("Blockers".to_string(), serde_json::json!({"rich_text": [{"text": {"content": blockers}}]}));
        }
        if let Some(due_at) = payload.due_at {
            properties.insert("Due Date".to_string(), serde_json::json!({"date": {"start": due_at.format("%Y-%m-%d").to_string()}}));
        }
        update_notion_task_page(&task_id, serde_json::Value::Object(properties)).await?;
        let task = fetch_notion_task_by_id(&task_id).await?;
        return Ok(Json(task));
    }
    let mut store = store.write().await;
    let task = store
        .tasks
        .get_mut(&task_id)
        .ok_or_else(|| ApiError(StatusCode::NOT_FOUND, format!("Task not found: {task_id}")))?;

    if let Some(title) = payload.title {
        task.title = title;
    }
    if let Some(description) = payload.description {
        task.description = Some(description);
    }
    if let Some(status) = payload.status {
        task.status = status;
        if status == TaskStatus::Done {
            task.completed_at = Some(Utc::now());
        }
    }
    if let Some(priority) = payload.priority {
        task.priority = priority;
    }
    if let Some(agent_id) = payload.agent_id {
        task.agent_id = Some(agent_id);
    }
    if let Some(source) = payload.source {
        task.source = Some(source);
    }
    if let Some(department) = payload.department {
        task.department = Some(department);
    }
    if let Some(blockers) = payload.blockers {
        task.blockers = Some(blockers);
    }
    if payload.due_at.is_some() {
        task.due_at = payload.due_at;
    }
    task.updated_at = Utc::now();
    Ok(Json(task.clone()))
}

async fn delete_task(
    State(store): State<SharedStore>,
    Path(task_id): Path<String>,
) -> Result<StatusCode, ApiError> {
    if notion_token().is_some() && task_id.contains('-') {
        archive_notion_task(&task_id).await?;
        return Ok(StatusCode::NO_CONTENT);
    }
    let mut store = store.write().await;
    store
        .tasks
        .remove(&task_id)
        .ok_or_else(|| ApiError(StatusCode::NOT_FOUND, format!("Task not found: {task_id}")))?;
    Ok(StatusCode::NO_CONTENT)
}

async fn get_notes(State(store): State<SharedStore>) -> ApiResult<Vec<Note>> {
    let store = store.read().await;
    let mut notes: Vec<Note> = store.notes.values().cloned().collect();
    notes.sort_by(|a, b| {
        let pin_cmp = b.pinned.cmp(&a.pinned);
        if pin_cmp != std::cmp::Ordering::Equal {
            return pin_cmp;
        }
        b.updated_at.cmp(&a.updated_at)
    });
    Ok(Json(notes))
}

async fn create_note(
    State(store): State<SharedStore>,
    Json(payload): Json<CreateNotePayload>,
) -> ApiResult<Note> {
    let mut store = store.write().await;
    let mut note = Note::new(payload.title, payload.content);
    note.agent_id = payload.agent_id;
    store.notes.insert(note.id.clone(), note.clone());
    Ok(Json(note))
}

async fn update_note(
    State(store): State<SharedStore>,
    Path(note_id): Path<String>,
    Json(payload): Json<UpdateNotePayload>,
) -> ApiResult<Note> {
    let mut store = store.write().await;
    let note = store
        .notes
        .get_mut(&note_id)
        .ok_or_else(|| ApiError(StatusCode::NOT_FOUND, format!("Note not found: {note_id}")))?;

    if let Some(title) = payload.title {
        note.title = title;
    }
    if let Some(content) = payload.content {
        note.content = content;
    }
    if let Some(seen) = payload.seen_by_agent {
        note.seen_by_agent = seen;
    }
    if let Some(pinned) = payload.pinned {
        note.pinned = pinned;
    }
    note.updated_at = Utc::now();
    Ok(Json(note.clone()))
}

async fn delete_note(
    State(store): State<SharedStore>,
    Path(note_id): Path<String>,
) -> Result<StatusCode, ApiError> {
    let mut store = store.write().await;
    store
        .notes
        .remove(&note_id)
        .ok_or_else(|| ApiError(StatusCode::NOT_FOUND, format!("Note not found: {note_id}")))?;
    Ok(StatusCode::NO_CONTENT)
}

// ── Rules ────────────────────────────────────────────────────────────────────

/// Parse a Rule from a section of RULES.md
fn parse_rule_section(title: &str, body: &str, category: &str) -> Rule {
    let mut rule = Rule::new(title, body.trim(), category);
    // Extract tags from **Tags:** line if present
    for line in body.lines() {
        let line = line.trim();
        if line.starts_with("**Tags:**") {
            let tags_str = line.trim_start_matches("**Tags:**").trim();
            rule.tags = tags_str.split(',').map(|s| s.trim().to_string()).filter(|s| !s.is_empty()).collect();
        }
    }
    rule
}

/// Load rules from ~/.hermes/RULES.md into the store. Idempotent — safe to call on every get_rules.
fn load_rules_from_disk(store: &mut AppStore) {
    let home = crate::store::home_dir();
    let path = std::path::PathBuf::from(&home).join(".hermes").join("RULES.md");
    let content = match std::fs::read_to_string(&path) {
        Ok(c) => c,
        Err(_) => return,
    };

    // Split on ## headings (## Category Name or ## Section)
    let mut current_title = String::new();
    let mut current_body = String::new();
    let mut current_category = "custom".to_string();

    for line in content.lines() {
        let trimmed = line.trim();
        // ## is a rule heading; flush previous before starting new one
        if trimmed.starts_with("## ") && !trimmed.starts_with("### ") {
            if !current_title.is_empty() {
                let rule = parse_rule_section(&current_title, &current_body, &current_category);
                store.rules.insert(rule.id.clone(), rule);
            }
            current_title = trimmed.trim_start_matches("## ").to_string();
            current_body.clear();
            current_category = match current_title.to_lowercase().as_str() {
                s if s.contains("drive") || s.contains("folder") => "drive-org",
                s if s.contains("file") || s.contains("naming") => "file-naming",
                s if s.contains("agent") || s.contains("behavior") => "agent-behavior",
                s if s.contains("operational") || s.contains("policy") => "operational",
                _ => "custom",
            }.to_string();
        } else if !current_title.is_empty() {
            // Accumulate body content after we've seen the first ## heading
            current_body.push_str(line);
            current_body.push('\n');
        }
    }
    // Flush final rule
    if !current_title.is_empty() {
        let rule = parse_rule_section(&current_title, &current_body, &current_category);
        store.rules.insert(rule.id.clone(), rule);
    }
}

async fn get_rules(State(store): State<SharedStore>) -> ApiResult<Vec<Rule>> {
    {
        let mut store = store.write().await;
        if store.rules.is_empty() {
            load_rules_from_disk(&mut store);
        }
    }
    let store = store.read().await;
    let mut rules: Vec<Rule> = store.rules.values().cloned().collect();
    rules.sort_by(|a, b| {
        let pin_cmp = b.pinned.cmp(&a.pinned);
        if pin_cmp != std::cmp::Ordering::Equal { return pin_cmp; }
        b.updated_at.cmp(&a.updated_at)
    });
    Ok(Json(rules))
}

#[derive(Deserialize)]
struct CreateRulePayload {
    title: String,
    content: String,
    category: String,
    agent_id: Option<String>,
    tags: Option<Vec<String>>,
}

async fn create_rule(
    State(store): State<SharedStore>,
    Json(payload): Json<CreateRulePayload>,
) -> ApiResult<Rule> {
    let mut store = store.write().await;
    let mut rule = Rule::new(&payload.title, &payload.content, &payload.category);
    rule.agent_id = payload.agent_id;
    if let Some(tags) = payload.tags {
        rule.tags = tags;
    }
    store.rules.insert(rule.id.clone(), rule.clone());
    Ok(Json(rule))
}

#[derive(Deserialize)]
struct UpdateRulePayload {
    title: Option<String>,
    content: Option<String>,
    category: Option<String>,
    pinned: Option<bool>,
    tags: Option<Vec<String>>,
}

async fn update_rule(
    State(store): State<SharedStore>,
    Path(rule_id): Path<String>,
    Json(payload): Json<UpdateRulePayload>,
) -> ApiResult<Rule> {
    let mut store = store.write().await;
    let rule = store
        .rules
        .get_mut(&rule_id)
        .ok_or_else(|| ApiError(StatusCode::NOT_FOUND, format!("Rule not found: {rule_id}")))?;
    if let Some(title) = payload.title { rule.title = title; }
    if let Some(content) = payload.content { rule.content = content; }
    if let Some(category) = payload.category { rule.category = category; }
    if let Some(pinned) = payload.pinned { rule.pinned = pinned; }
    if let Some(tags) = payload.tags { rule.tags = tags; }
    rule.updated_at = Utc::now();
    Ok(Json(rule.clone()))
}

async fn delete_rule(
    State(store): State<SharedStore>,
    Path(rule_id): Path<String>,
) -> Result<StatusCode, ApiError> {
    let mut store = store.write().await;
    store
        .rules
        .remove(&rule_id)
        .ok_or_else(|| ApiError(StatusCode::NOT_FOUND, format!("Rule not found: {rule_id}")))?;
    Ok(StatusCode::NO_CONTENT)
}

/// Write all rules to ~/.hermes/RULES.md. Called at startup to rebuild from disk.
#[allow(dead_code)]
fn sync_rules_to_disk(rules: &HashMap<String, Rule>) -> Result<(), String> {
    let home = crate::store::home_dir();
    let path = std::path::PathBuf::from(home).join(".hermes").join("RULES.md");
    let header = r#"# RULES.md — SoLoVision Agent Operating Rules

_This file is the authoritative home for all agent operational policy. It persists across sessions like memory but has no size limit. Rules here apply to all agents unless overridden in an agent's individual SOUL.md._
";
---

## Google Drive File Organization
"#;
    let mut lines = vec![header.to_string()];
    for rule in rules.values() {
        lines.push(format!("### {}", rule.title));
        if !rule.category.is_empty() {
            lines.push(format!("**Category:** {}", rule.category));
        }
        lines.push(String::new());
        lines.push(rule.content.clone());
        if !rule.tags.is_empty() {
            lines.push(format!("\n**Tags:** {}", rule.tags.join(", ")));
        }
        lines.push(String::new());
        lines.push("---".to_string());
        lines.push(String::new());
    }
    std::fs::write(&path, lines.join("\n"))
        .map_err(|e| format!("Failed to write RULES.md: {e}"))
}

async fn get_activity(
    State(store): State<SharedStore>,
    Query(params): Query<ListParams>,
) -> ApiResult<Vec<Activity>> {
    let store = store.read().await;
    let mut activities = store.activities.clone();
    activities.sort_by(|a, b| b.timestamp.cmp(&a.timestamp));
    if let Some(limit) = params.limit {
        activities.truncate(limit);
    }
    Ok(Json(activities))
}

async fn get_sessions(State(store): State<SharedStore>) -> ApiResult<Vec<Session>> {
    let store = store.read().await;
    let mut sessions: Vec<Session> = store.sessions.values().cloned().collect();
    sessions.sort_by(|a, b| b.updated_at.cmp(&a.updated_at));
    Ok(Json(sessions))
}

async fn create_session(
    State(store): State<SharedStore>,
    Json(payload): Json<CreateSessionPayload>,
) -> ApiResult<Session> {
    let mut store = store.write().await;
    let session = Session {
        id: uuid::Uuid::new_v4().to_string(),
        name: payload.name,
        agent_id: payload.agent_id.unwrap_or_else(|| "halo".to_string()),
        created_at: Utc::now(),
        updated_at: Utc::now(),
        message_count: 0,
        archived: false,
        hermes_session_id: None,
    };
    store.sessions.insert(session.id.clone(), session.clone());
    persist_store_snapshot(&store)?;
    Ok(Json(session))
}

async fn update_session(
    State(store): State<SharedStore>,
    Path(session_id): Path<String>,
    Json(payload): Json<UpdateSessionPayload>,
) -> ApiResult<Session> {
    let mut store = store.write().await;
    let session = store.sessions.get_mut(&session_id).ok_or_else(|| {
        ApiError(
            StatusCode::NOT_FOUND,
            format!("Session not found: {session_id}"),
        )
    })?;
    session.name = payload.name;
    session.updated_at = Utc::now();
    let response = session.clone();
    persist_store_snapshot(&store)?;
    Ok(Json(response))
}

async fn delete_session(
    State(store): State<SharedStore>,
    Path(session_id): Path<String>,
) -> Result<StatusCode, ApiError> {
    let mut store = store.write().await;
    store.sessions.remove(&session_id);
    store.messages.remove(&session_id);
    persist_store_snapshot(&store)?;
    Ok(StatusCode::NO_CONTENT)
}

async fn get_messages(
    State(store): State<SharedStore>,
    Path(session_id): Path<String>,
) -> ApiResult<Vec<Message>> {
    let store = store.read().await;
    Ok(Json(
        store.messages.get(&session_id).cloned().unwrap_or_default(),
    ))
}

async fn send_message(
    State(store): State<SharedStore>,
    Json(payload): Json<SendMessagePayload>,
) -> ApiResult<SendMessageResponse> {
    // Fetch hermes_session_id before taking the lock
    let hermes_session_id = {
        let store = store.read().await;
        store.sessions.get(&payload.session_id)
            .and_then(|s| s.hermes_session_id.clone())
    };

    // Validate session exists
    if hermes_session_id.is_none() {
        let store = store.read().await;
        if !store.sessions.contains_key(&payload.session_id) {
            return Err(ApiError(
                StatusCode::NOT_FOUND,
                format!("Session not found: {}", payload.session_id),
            ));
        }
    }

    let user_message = Message {
        id: uuid::Uuid::new_v4().to_string(),
        session_id: payload.session_id.clone(),
        role: MessageRole::User,
        content: payload.content.clone(),
        created_at: Utc::now(),
        tool_calls: None,
    };

    {
        let mut store = store.write().await;
        store.messages.entry(payload.session_id.clone())
            .or_default()
            .push(user_message.clone());
        if let Some(session) = store.sessions.get_mut(&payload.session_id) {
            session.message_count += 1;
            session.updated_at = Utc::now();
        }
        persist_store_snapshot(&store)?;
    }

    let (response_content, new_hermes_session_id, tool_calls) = match invoke_hermes_chat(
        &payload.agent_id,
        &payload.content,
        hermes_session_id.as_deref(),
    ).await {
        Ok((response, sid, tools)) => (response, sid, tools),
        Err(e) => {
            eprintln!("[ERROR] invoke_hermes_chat failed for agent '{}': {:?}", payload.agent_id, e);
            return Err(ApiError(
                StatusCode::BAD_GATEWAY,
                format!("Agent '{}' failed to respond: {:?}", payload.agent_id, e),
            ));
        }
    };

    let agent_message = Message {
        id: uuid::Uuid::new_v4().to_string(),
        session_id: payload.session_id.clone(),
        role: MessageRole::Assistant,
        content: response_content,
        created_at: Utc::now(),
        tool_calls: if tool_calls.is_empty() { None } else { Some(tool_calls) },
    };

    let mut store = store.write().await;
    store.messages.entry(payload.session_id.clone())
        .or_default()
        .push(agent_message.clone());
    if let Some(session) = store.sessions.get_mut(&payload.session_id) {
        session.message_count += 1;
        session.updated_at = Utc::now();
        // Persist hermes_session_id on first message
        if session.hermes_session_id.is_none() && !new_hermes_session_id.is_empty() {
            session.hermes_session_id = Some(new_hermes_session_id.clone());
            eprintln!("[DEBUG] send_message: saved hermes_session_id='{}' for session='{}'", new_hermes_session_id, payload.session_id);
        } else {
            eprintln!("[DEBUG] send_message: NOT saving hermes_session_id - is_none={}, is_empty={}", session.hermes_session_id.is_none(), new_hermes_session_id.is_empty());
        }
    }
    if let Some(agent) = store.agents.get_mut(&payload.agent_id) {
        agent.message_count += 2;
    }
    store.add_activity(Activity::new(
        payload.agent_id,
        ActivityType::Message,
        format!("Chat message in session {}", payload.session_id),
    ));
    persist_store_snapshot(&store)?;

    Ok(Json(SendMessageResponse {
        user_message,
        agent_message,
    }))
}

// SSE streaming chat endpoint for real-time tool call display
#[derive(Debug, Clone, Serialize)]
struct ChatStreamEvent {
    #[serde(rename = "type")]
    pub event_type: String,
    pub data: serde_json::Value,
}

async fn send_message_stream(
    State(store): State<SharedStore>,
    Json(payload): Json<SendMessagePayload>,
) -> Sse<impl Stream<Item = Result<axum::response::sse::Event, std::convert::Infallible>>> {
    use axum::response::sse::Event;
    use std::process::Stdio;
    use tokio::io::{AsyncBufReadExt, BufReader};
    use tokio::process::Command;
    use tokio::sync::mpsc;
    use std::pin::Pin;
    use std::task::{Context, Poll};

    // Create channel for streaming events
    let (tx, mut rx) = mpsc::channel::<Result<Event, std::convert::Infallible>>(100);
    
    // Clone what we need for the async task
    let store_clone = store.clone();
    let payload_clone = payload.clone();
    
    // Spawn the streaming task
    tokio::spawn(async move {
        // Fetch hermes_session_id
        let hermes_session_id = {
            let store = store_clone.read().await;
            store.sessions.get(&payload_clone.session_id)
                .and_then(|s| s.hermes_session_id.clone())
        };

        // Save user message
        let user_message = Message {
            id: uuid::Uuid::new_v4().to_string(),
            session_id: payload_clone.session_id.clone(),
            role: MessageRole::User,
            content: payload_clone.content.clone(),
            created_at: Utc::now(),
            tool_calls: None,
        };

        {
            let mut store = store_clone.write().await;
            store.messages.entry(payload_clone.session_id.clone())
                .or_default()
                .push(user_message.clone());
            if let Some(session) = store.sessions.get_mut(&payload_clone.session_id) {
                session.message_count += 1;
                session.updated_at = Utc::now();
            }
            let _ = persist_store_snapshot(&store);
        }

        // Send user message event
        let _ = tx.send(Ok(Event::default()
            .event("user_message")
            .data(serde_json::to_string(&user_message).unwrap_or_default()))).await;

        // Setup hermes command
        let home = crate::store::home_dir();
        let profile_dir = std::path::Path::new(&home)
            .join(".hermes")
            .join("profiles")
            .join(&payload_clone.agent_id);

        if !profile_dir.exists() {
            let _ = tx.send(Ok(Event::default()
                .event("error")
                .data(format!("Agent profile not found: {}", profile_dir.display())))).await;
            return;
        }

        let escaped_msg = payload_clone.content.replace('"', "\\\"");
        let hermes_bin = if std::path::Path::new("/usr/local/bin/hermes").exists() {
            "/usr/local/bin/hermes".to_string()
        } else if std::path::Path::new("/usr/bin/hermes").exists() {
            "/usr/bin/hermes".to_string()
        } else if std::path::Path::new(&format!("{}/.local/bin/hermes", home.display())).exists() {
            format!("{}/.local/bin/hermes", home.display())
        } else if std::path::Path::new(&format!("{}/.cargo/bin/hermes", home.display())).exists() {
            format!("{}/.cargo/bin/hermes", home.display())
        } else {
            "hermes".to_string()
        };
        
        let hermes_cmd = if let Some(sid) = hermes_session_id {
            format!("{} chat -q \"{}\" --resume {} --source mission-control 2>&1", hermes_bin, escaped_msg, sid)
        } else {
            format!("{} chat -q \"{}\" --source mission-control 2>&1", hermes_bin, escaped_msg)
        };

        // Start hermes process
        let mut child = match Command::new("bash")
            .arg("-c")
            .arg(&hermes_cmd)
            .env("HOME", &home)
            .env("HERMES_HOME", profile_dir.display().to_string())
            .current_dir(&profile_dir)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
        {
            Ok(c) => c,
            Err(e) => {
                let _ = tx.send(Ok(Event::default()
                    .event("error")
                    .data(format!("Failed to start hermes: {}", e)))).await;
                return;
            }
        };

        // Stream stdout line by line
        let stdout = child.stdout.take().expect("stdout piped");
        let mut reader = BufReader::new(stdout).lines();
        let mut response_lines: Vec<String> = Vec::new();
        let mut session_id = String::new();
        let mut current_tool: Option<ToolCall> = None;
        let mut in_tool_output = false;

        while let Ok(Some(line)) = reader.next_line().await {
            let trimmed = line.trim();
            
            // Check for session_id - support both "session_id:" and "Session:" formats
            if trimmed.to_lowercase().starts_with("session_id:") {
                let colon_pos = trimmed.find(':').unwrap_or(0);
                session_id = trimmed[colon_pos+1..].trim().to_string();
                eprintln!("[DEBUG] Extracted session_id: {} from line: {}", session_id, line);
                continue;
            }

            // Detect tool calls - look for patterns like "> mcp_tool_name" or tool emojis
            let is_tool_line = trimmed.starts_with("> ") && (
                trimmed.contains("mcp_") || 
                trimmed.contains("search") || 
                trimmed.contains("read_") || 
                trimmed.contains("write_") ||
                trimmed.contains("execute_")
            );
            
            let has_tool_emoji = line.contains('🛠') || line.contains('🔧') || line.contains('⚡') || line.contains('📎');

            if is_tool_line || has_tool_emoji {
                // If we were collecting a previous tool, send it
                if let Some(tool) = current_tool.take() {
                    let _ = tx.send(Ok(Event::default()
                        .event("tool_call")
                        .data(serde_json::to_string(&tool).unwrap_or_default()))).await;
                }
                
                // Extract tool name
                let tool_name = if let Some(start) = trimmed.find("mcp_") {
                    let end = trimmed[start..].find(|c: char| c.is_whitespace() || c == '(' || c == ')')
                        .map(|i| start + i)
                        .unwrap_or(trimmed.len());
                    trimmed[start..end].to_string()
                } else if has_tool_emoji {
                    trimmed.split_whitespace().nth(1).unwrap_or("unknown").to_string()
                } else {
                    trimmed.trim_start_matches("> ").split_whitespace().next().unwrap_or("unknown").to_string()
                };

                current_tool = Some(ToolCall {
                    id: uuid::Uuid::new_v4().to_string(),
                    name: tool_name,
                    arguments: serde_json::json!({}),
                    result: None,
                });
                in_tool_output = true;
                continue;
            }

            // Check for tool results (lines after a tool call that look like output)
            if in_tool_output && !trimmed.is_empty() && current_tool.is_some() {
                // Check if this is end of tool output (new prompt, empty line, etc)
                if trimmed.starts_with("Human:") || trimmed.starts_with("Assistant:") || trimmed.starts_with('┌') {
                    in_tool_output = false;
                    if let Some(tool) = current_tool.take() {
                        let _ = tx.send(Ok(Event::default()
                            .event("tool_call")
                            .data(serde_json::to_string(&tool).unwrap_or_default()))).await;
                    }
                } else {
                    // Append to current tool result
                    if let Some(ref mut tool) = current_tool {
                        let result = tool.result.get_or_insert_with(String::new);
                        if !result.is_empty() {
                            result.push('\n');
                        }
                        result.push_str(trimmed);
                    }
                    continue;
                }
            }

            // Skip decoration lines
            if line.contains('⚕') || line.starts_with('╭') || line.starts_with('╰') || 
               line.starts_with('┊') || line.contains("────") || line.contains("0.0s") {
                continue;
            }

            // Regular response content
            if !trimmed.is_empty() && !trimmed.starts_with("preparing") && !trimmed.starts_with("memory") {
                response_lines.push(line.clone());
                // Send incremental content
                let _ = tx.send(Ok(Event::default()
                    .event("content")
                    .data(trimmed.to_string()))).await;
            }
        }

        // Send final tool if pending
        if let Some(tool) = current_tool.take() {
            let _ = tx.send(Ok(Event::default()
                .event("tool_call")
                .data(serde_json::to_string(&tool).unwrap_or_default()))).await;
        }

        // Wait for process and get final output
        let _ = child.wait().await;
        
        // Build final response
        let response_content = response_lines.join("\n").trim().to_string();
        let agent_message = Message {
            id: uuid::Uuid::new_v4().to_string(),
            session_id: payload_clone.session_id.clone(),
            role: MessageRole::Assistant,
            content: response_content.clone(),
            created_at: Utc::now(),
            tool_calls: None, // Tools were sent via events
        };

        // Save to store
        {
            let mut store = store_clone.write().await;
            store.messages.entry(payload_clone.session_id.clone())
                .or_default()
                .push(agent_message.clone());
            if let Some(session) = store.sessions.get_mut(&payload_clone.session_id) {
                session.message_count += 1;
                session.updated_at = Utc::now();
                if session.hermes_session_id.is_none() && !session_id.is_empty() {
                    session.hermes_session_id = Some(session_id.clone());
                    eprintln!("[DEBUG] send_message_stream: saved hermes_session_id='{}' for session='{}'", session_id, payload_clone.session_id);
                } else {
                    eprintln!("[DEBUG] send_message_stream: NOT saving hermes_session_id - is_none={}, is_empty={}", session.hermes_session_id.is_none(), session_id.is_empty());
                }
            }
            if let Some(agent) = store.agents.get_mut(&payload_clone.agent_id) {
                agent.message_count += 2;
            }
            let _ = persist_store_snapshot(&store);
        }

        // Send completion event with full message
        let _ = tx.send(Ok(Event::default()
            .event("complete")
            .data(serde_json::to_string(&agent_message).unwrap_or_default()))).await;
    });

    // Create stream from receiver
    let stream = async_stream::stream! {
        while let Some(item) = rx.recv().await {
            yield item;
        }
    };

    Sse::new(stream).keep_alive(axum::response::sse::KeepAlive::default())
}

async fn normalize_hermes_cron_jobs_file() -> Result<(), ApiError> {
    let home = crate::store::home_dir();
    let script = r#"
import json
from pathlib import Path
from cron.jobs import compute_next_run, save_jobs
p = Path.home() / '.hermes' / 'cron' / 'jobs.json'
if not p.exists():
    raise SystemExit(0)
data = json.loads(p.read_text())
jobs = data.get('jobs', [])
changed = False
for job in jobs:
    sched = job.get('schedule') or {}
    if not isinstance(sched, dict):
        expr = str(sched or '')
        sched = {'kind':'cron','expr':expr,'display':expr}
        job['schedule'] = sched
        changed = True
    elif 'kind' not in sched and 'expr' in sched:
        expr = sched['expr']
        job['schedule'] = {'kind':'cron','expr':expr,'display':sched.get('display') or expr}
        sched = job['schedule']
        changed = True
    if not job.get('schedule_display'):
        job['schedule_display'] = sched.get('display') or sched.get('expr') or ''
        changed = True
    if not isinstance(job.get('repeat'), dict):
        job['repeat'] = {'times': None, 'completed': 0}
        changed = True
    if 'state' not in job or isinstance(job.get('state'), dict):
        job['state'] = 'paused' if not job.get('enabled', True) else 'scheduled'
        changed = True
    if job.get('enabled', True) and job.get('state') != 'paused' and not job.get('next_run_at'):
        job['next_run_at'] = compute_next_run(job['schedule'], job.get('last_run_at'))
        changed = True
if changed:
    save_jobs(jobs)
"#;
    let output =
        tokio::process::Command::new(format!("{}/.hermes/hermes-agent/venv/bin/python", home.display()))
            .arg("-c")
            .arg(script)
            .env("HOME", &home)
            .output()
            .await
            .map_err(|e| {
                ApiError(
                    StatusCode::INTERNAL_SERVER_ERROR,
                    format!("Failed to normalize cron file: {e}"),
                )
            })?;
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(ApiError(
            StatusCode::INTERNAL_SERVER_ERROR,
            format!("Cron normalization failed: {stderr}"),
        ));
    }
    Ok(())
}

async fn persist_cron_jobs_to_file(store: &AppStore) -> Result<(), ApiError> {
    use serde_json::{json, Map, Value};
    use std::collections::HashMap;
    use std::path::Path;

    let home = crate::store::home_dir();
    let cron_dir = Path::new(&home).join(".hermes").join("cron");
    let cron_file = cron_dir.join("jobs.json");
    tokio::fs::create_dir_all(&cron_dir).await.map_err(|e| {
        ApiError(
            StatusCode::INTERNAL_SERVER_ERROR,
            format!("Failed to create cron dir: {e}"),
        )
    })?;

    let existing_value: Value = match tokio::fs::read_to_string(&cron_file).await {
        Ok(content) => serde_json::from_str(&content).unwrap_or_else(|_| json!({"jobs": []})),
        Err(_) => json!({"jobs": []}),
    };

    let mut existing_by_id: HashMap<String, Map<String, Value>> = HashMap::new();
    if let Some(existing_jobs) = existing_value.get("jobs").and_then(|v| v.as_array()) {
        for job in existing_jobs {
            if let Some(obj) = job.as_object() {
                if let Some(id) = obj.get("id").and_then(|v| v.as_str()) {
                    existing_by_id.insert(id.to_string(), obj.clone());
                }
            }
        }
    }

    let mut jobs: Vec<&CronJob> = store.cron_jobs.values().collect();
    jobs.sort_by(|a, b| a.name.cmp(&b.name));

    let mut serialized_jobs = Vec::new();
    for job in jobs {
        let mut obj = existing_by_id.remove(&job.id).unwrap_or_default();
        let existing_schedule = obj.get("schedule").and_then(|v| v.as_object()).cloned();
        let existing_repeat = obj.get("repeat").cloned();
        let existing_origin = obj.get("origin").cloned();
        let existing_skill = obj.get("skill").cloned();
        let existing_skills = obj.get("skills").cloned();
        let existing_base_url = obj.get("base_url").cloned();
        let existing_paused_at = obj.get("paused_at").cloned().unwrap_or(Value::Null);
        let existing_paused_reason = obj.get("paused_reason").cloned().unwrap_or(Value::Null);

        let schedule_value = if let Some(schedule) = existing_schedule {
            if schedule.contains_key("kind") {
                Value::Object(schedule)
            } else {
                json!({"kind": "cron", "expr": job.schedule, "display": job.schedule})
            }
        } else {
            json!({"kind": "cron", "expr": job.schedule, "display": job.schedule})
        };

        let next_run_value = job
            .next_run
            .map(|v| json!(v.to_rfc3339()))
            .unwrap_or_else(|| obj.get("next_run_at").cloned().unwrap_or(Value::Null));
        let last_run_value = job
            .last_run
            .map(|v| json!(v.to_rfc3339()))
            .unwrap_or(Value::Null);
        let status_value = if job.consecutive_errors > 0 || job.last_error.is_some() {
            json!("error")
        } else if last_run_value.is_null() {
            obj.get("last_status").cloned().unwrap_or(Value::Null)
        } else {
            json!("ok")
        };
        let state_value = if !job.enabled {
            json!("paused")
        } else {
            obj.get("state").cloned().unwrap_or(json!("scheduled"))
        };

        obj.insert("id".into(), json!(job.id));
        obj.insert("name".into(), json!(job.name));
        obj.insert("prompt".into(), json!(job.command));
        obj.insert(
            "agent_id".into(),
            job.agent_id
                .as_ref()
                .map(|v| json!(v))
                .unwrap_or(Value::Null),
        );
        obj.insert("enabled".into(), json!(job.enabled));
        obj.insert("schedule".into(), schedule_value);
        obj.insert("schedule_display".into(), json!(job.schedule));
        obj.insert(
            "repeat".into(),
            existing_repeat.unwrap_or(json!({"times": Value::Null, "completed": 0})),
        );
        obj.insert("state".into(), state_value);
        obj.insert("paused_at".into(), existing_paused_at);
        obj.insert("paused_reason".into(), existing_paused_reason);
        obj.insert(
            "model".into(),
            job.model.as_ref().map(|v| json!(v)).unwrap_or(Value::Null),
        );
        obj.insert(
            "provider".into(),
            job.provider
                .as_ref()
                .map(|v| json!(v))
                .unwrap_or(Value::Null),
        );
        obj.insert("base_url".into(), existing_base_url.unwrap_or(Value::Null));
        obj.insert(
            "deliver".into(),
            job.delivery_mode
                .as_ref()
                .map(|v| json!(v))
                .unwrap_or(json!("local")),
        );
        obj.insert("origin".into(), existing_origin.unwrap_or(Value::Null));
        obj.insert("skill".into(), existing_skill.unwrap_or(Value::Null));
        obj.insert("skills".into(), existing_skills.unwrap_or(json!([])));
        obj.insert("created_at".into(), json!(job.created_at.to_rfc3339()));
        obj.insert("updated_at".into(), json!(chrono::Utc::now().to_rfc3339()));
        obj.insert("next_run_at".into(), next_run_value);
        obj.insert("last_run_at".into(), last_run_value);
        obj.insert(
            "last_error".into(),
            job.last_error
                .as_ref()
                .map(|v| json!(v))
                .unwrap_or(Value::Null),
        );
        obj.insert("last_status".into(), status_value);
        serialized_jobs.push(Value::Object(obj));
    }

    let payload = json!({
        "jobs": serialized_jobs,
        "updated_at": chrono::Utc::now().to_rfc3339(),
    });

    tokio::fs::write(&cron_file, serde_json::to_string_pretty(&payload).unwrap())
        .await
        .map_err(|e| {
            ApiError(
                StatusCode::INTERNAL_SERVER_ERROR,
                format!("Failed to write cron jobs file: {e}"),
            )
        })?;

    Ok(())
}

async fn get_cron_jobs(State(store): State<SharedStore>) -> ApiResult<Vec<CronJob>> {
    let mut store = store.write().await;
    sync_cron_jobs_from_file(&mut store).await;
    let mut jobs: Vec<CronJob> = store.cron_jobs.values().cloned().collect();
    jobs.sort_by(|a, b| match (a.next_run, b.next_run) {
        (Some(a_time), Some(b_time)) => a_time.cmp(&b_time),
        (Some(_), None) => std::cmp::Ordering::Less,
        (None, Some(_)) => std::cmp::Ordering::Greater,
        (None, None) => std::cmp::Ordering::Equal,
    });
    Ok(Json(jobs))
}

async fn create_cron_job(
    State(store): State<SharedStore>,
    Json(payload): Json<CreateCronJobPayload>,
) -> ApiResult<CronJob> {
    let mut store = store.write().await;
    let mut job = CronJob::new(payload.name, payload.schedule, payload.command);
    job.agent_id = payload.agent_id;
    if let Some(v) = payload.session_target {
        job.session_target = v;
    }
    if let Some(v) = payload.enabled {
        job.enabled = v;
    }
    job.delivery_mode = payload.delivery_mode;
    job.delivery_target = payload.delivery_target;
    job.provider = payload.provider;
    job.model = payload.model;
    store.cron_jobs.insert(job.id.clone(), job.clone());
    persist_cron_jobs_to_file(&store).await?;
    normalize_hermes_cron_jobs_file().await?;
    Ok(Json(job))
}

async fn update_cron_job(
    State(store): State<SharedStore>,
    Path(job_id): Path<String>,
    Json(payload): Json<UpdateCronJobPayload>,
) -> ApiResult<CronJob> {
    let mut store = store.write().await;
    let job = store.cron_jobs.get_mut(&job_id).ok_or_else(|| {
        ApiError(
            StatusCode::NOT_FOUND,
            format!("Cron job not found: {job_id}"),
        )
    })?;

    if let Some(v) = payload.name {
        job.name = v;
    }
    if let Some(v) = payload.schedule {
        job.schedule = v;
    }
    if let Some(v) = payload.command {
        job.command = v;
    }
    if let Some(v) = payload.agent_id {
        job.agent_id = Some(v);
    }
    if let Some(v) = payload.session_target {
        job.session_target = v;
    }
    if let Some(v) = payload.enabled {
        job.enabled = v;
    }
    if let Some(v) = payload.delivery_mode {
        job.delivery_mode = Some(v);
    }
    if let Some(v) = payload.delivery_target {
        job.delivery_target = Some(v);
    }
    if let Some(v) = payload.provider {
        job.provider = Some(v);
    }
    if let Some(v) = payload.model {
        job.model = Some(v);
    }
    job.updated_at = Utc::now();
    let job_clone = job.clone();
    persist_cron_jobs_to_file(&store).await?;
    normalize_hermes_cron_jobs_file().await?;
    Ok(Json(job_clone))
}

async fn run_cron_job_now_api(Path(job_id): Path<String>) -> Result<StatusCode, ApiError> {
    use tokio::process::Command;

    let home = crate::store::home_dir();
    let hermes = format!("{}/.hermes/hermes-agent/venv/bin/python", home.display());

    let trigger = Command::new(&hermes)
        .args(["-m", "hermes_cli.main", "cron", "run", &job_id])
        .env("HOME", &home)
        .env("HERMES_HOME", format!("{}/.hermes", home.display()))
        .output()
        .await
        .map_err(|e| {
            ApiError(
                StatusCode::INTERNAL_SERVER_ERROR,
                format!("Failed to trigger cron job: {e}"),
            )
        })?;
    if !trigger.status.success() {
        let stderr = String::from_utf8_lossy(&trigger.stderr);
        let stdout = String::from_utf8_lossy(&trigger.stdout);
        return Err(ApiError(
            StatusCode::BAD_GATEWAY,
            format!("Cron trigger failed: {}{}", stdout, stderr),
        ));
    }

    let tick = Command::new(&hermes)
        .args(["-m", "hermes_cli.main", "cron", "tick"])
        .env("HOME", &home)
        .env("HERMES_HOME", format!("{}/.hermes", home.display()))
        .output()
        .await
        .map_err(|e| {
            ApiError(
                StatusCode::INTERNAL_SERVER_ERROR,
                format!("Failed to tick cron scheduler: {e}"),
            )
        })?;
    if !tick.status.success() {
        let stderr = String::from_utf8_lossy(&tick.stderr);
        let stdout = String::from_utf8_lossy(&tick.stdout);
        return Err(ApiError(
            StatusCode::BAD_GATEWAY,
            format!("Cron tick failed: {}{}", stdout, stderr),
        ));
    }

    Ok(StatusCode::ACCEPTED)
}

async fn delete_cron_job(
    State(store): State<SharedStore>,
    Path(job_id): Path<String>,
) -> Result<StatusCode, ApiError> {
    let mut store = store.write().await;
    store.cron_jobs.remove(&job_id).ok_or_else(|| {
        ApiError(
            StatusCode::NOT_FOUND,
            format!("Cron job not found: {job_id}"),
        )
    })?;
    store.cron_runs.retain(|run| run.job_id != job_id);
    persist_cron_jobs_to_file(&store).await?;
    normalize_hermes_cron_jobs_file().await?;
    Ok(StatusCode::NO_CONTENT)
}


#[derive(Debug, Serialize)]
struct SkillSummary {
    name: String,
    description: String,
    category: String,
    path: String,
    source: String,
    bundled: bool,
    can_uninstall: bool,
}

#[derive(Debug, Serialize)]
struct SkillFileEntry {
    name: String,
    relative_path: String,
    size: u64,
    mtime: String,
}

#[derive(Debug, Deserialize)]
struct SkillFileWriteRequest {
    content: String,
}

#[derive(Debug, Serialize)]
struct SkillActionResult {
    ok: bool,
    command: String,
    stdout: String,
    stderr: String,
}

#[derive(Debug, Deserialize)]
struct SkillHubSearchRequest {
    query: String,
}

#[derive(Debug, Deserialize)]
struct SkillHubBrowseRequest {
    page: Option<usize>,
    size: Option<usize>,
    source: Option<String>,
}

#[derive(Debug, Deserialize)]
struct SkillHubIdentifierRequest {
    identifier: String,
}

async fn run_skill_cli(args: &[&str]) -> Result<SkillActionResult, ApiError> {
    let output = tokio::process::Command::new("hermes")
        .args(args)
        .output()
        .await
        .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to run hermes skills command: {e}")))?;
    Ok(SkillActionResult {
        ok: output.status.success(),
        command: format!("hermes {}", args.join(" ")),
        stdout: String::from_utf8_lossy(&output.stdout).to_string(),
        stderr: String::from_utf8_lossy(&output.stderr).to_string(),
    })
}


fn skills_root() -> std::path::PathBuf {
    let home = crate::store::home_dir();
    std::path::PathBuf::from(home).join(".hermes").join("skills")
}

fn bundled_skill_names() -> std::collections::HashSet<String> {
    let manifest = skills_root().join(".bundled_manifest");
    let mut set = std::collections::HashSet::new();
    if let Ok(content) = std::fs::read_to_string(manifest) {
        for line in content.lines() {
            if let Some((name, _)) = line.split_once(':') {
                let trimmed = name.trim();
                if !trimmed.is_empty() {
                    set.insert(trimmed.to_string());
                }
            }
        }
    }
    set
}

fn collect_skill_dirs(root: &std::path::Path) -> Result<Vec<(String, std::path::PathBuf)>, ApiError> {
    let mut skills = Vec::new();
    if !root.exists() {
        return Ok(skills);
    }
    for category_entry in std::fs::read_dir(root).map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to read skills root: {e}")))? {
        let category_entry = category_entry.map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to read skills category: {e}")))?;
        let category_path = category_entry.path();
        let category_name = category_path.file_name().and_then(|n| n.to_str()).unwrap_or("").to_string();
        if category_name.starts_with('.') || !category_path.is_dir() { continue; }
        let category_skill = category_path.join("SKILL.md");
        if category_skill.exists() {
            skills.push((category_name.clone(), category_path.clone()));
        }
        for skill_entry in std::fs::read_dir(&category_path).map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to read category dir: {e}")))? {
            let skill_entry = skill_entry.map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to read skill dir: {e}")))?;
            let skill_path = skill_entry.path();
            if !skill_path.is_dir() { continue; }
            if skill_path.join("SKILL.md").exists() {
                skills.push((category_name.clone(), skill_path));
            }
        }
    }
    Ok(skills)
}

fn parse_skill_description(content: &str) -> String {
    let mut lines = content.lines();
    let mut in_frontmatter = false;
    let mut passed_frontmatter = false;
    let mut description = String::new();
    while let Some(line) = lines.next() {
        let trimmed = line.trim();
        if trimmed == "---" {
            if !in_frontmatter && !passed_frontmatter {
                in_frontmatter = true;
                continue;
            } else if in_frontmatter {
                in_frontmatter = false;
                passed_frontmatter = true;
                continue;
            }
        }
        if in_frontmatter {
            if let Some(rest) = trimmed.strip_prefix("description:") {
                description = rest.trim().trim_matches('"').to_string();
            }
            continue;
        }
        if passed_frontmatter && !trimmed.is_empty() && !trimmed.starts_with('#') {
            return if description.is_empty() { trimmed.to_string() } else { description };
        }
    }
    description
}

async fn get_skills() -> Result<Json<Vec<SkillSummary>>, ApiError> {
    let root = skills_root();
    let bundled = bundled_skill_names();
    let mut out = Vec::new();
    for (category, skill_path) in collect_skill_dirs(&root)? {
        let skill_md = skill_path.join("SKILL.md");
        let content = std::fs::read_to_string(&skill_md).unwrap_or_default();
        let name = skill_path.strip_prefix(&root).ok().and_then(|p| p.to_str()).unwrap_or_default().replace('\\', "/");
        let leaf = skill_path.file_name().and_then(|n| n.to_str()).unwrap_or("").to_string();
        let is_bundled = bundled.contains(&leaf);
        let source = if category == "openclaw-imports" { "openclaw-import".to_string() } else if is_bundled { "bundled".to_string() } else { "local".to_string() };
        out.push(SkillSummary {
            name,
            description: parse_skill_description(&content),
            category,
            path: skill_path.to_string_lossy().to_string(),
            source,
            bundled: is_bundled,
            can_uninstall: !is_bundled,
        });
    }
    out.sort_by(|a,b| a.name.cmp(&b.name));
    Ok(Json(out))
}

fn resolve_skill_path(skill_name: &str) -> Result<std::path::PathBuf, ApiError> {
    let root = skills_root();
    let path = root.join(skill_name);
    let canonical_root = std::fs::canonicalize(&root).map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to access skills root: {e}")))?;
    let canonical_path = std::fs::canonicalize(&path).map_err(|_| ApiError(StatusCode::NOT_FOUND, format!("Skill not found: {skill_name}")))?;
    if !canonical_path.starts_with(&canonical_root) {
        return Err(ApiError(StatusCode::FORBIDDEN, "Access denied".to_string()));
    }
    if !canonical_path.join("SKILL.md").exists() {
        return Err(ApiError(StatusCode::NOT_FOUND, format!("Skill not found: {skill_name}")));
    }
    Ok(canonical_path)
}

fn list_skill_files_recursive(base: &std::path::Path, dir: &std::path::Path, acc: &mut Vec<SkillFileEntry>) -> Result<(), ApiError> {
    for entry in std::fs::read_dir(dir).map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to read skill files: {e}")))? {
        let entry = entry.map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to read skill files: {e}")))?;
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().to_string();
        if path.is_dir() {
            list_skill_files_recursive(base, &path, acc)?;
        } else {
            let stat = entry.metadata().map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to stat skill file: {e}")))?;
            let rel = path.strip_prefix(base).ok().and_then(|p| p.to_str()).unwrap_or_default().replace('\\', "/");
            let mtime = stat.modified().ok().map(|t| chrono::DateTime::<chrono::Utc>::from(t).to_rfc3339()).unwrap_or_default();
            acc.push(SkillFileEntry { name, relative_path: rel, size: stat.len(), mtime });
        }
    }
    Ok(())
}

async fn get_skill_files(Path(skill_name): Path<String>) -> Result<Json<serde_json::Value>, ApiError> {
    let skill_path = resolve_skill_path(&skill_name)?;
    let mut files = Vec::new();
    list_skill_files_recursive(&skill_path, &skill_path, &mut files)?;
    Ok(Json(serde_json::json!({"path": skill_path.to_string_lossy(), "files": files})))
}

async fn get_skill_file(Path((skill_name, file_path)): Path<(String, String)>) -> Result<Json<serde_json::Value>, ApiError> {
    let skill_path = resolve_skill_path(&skill_name)?;
    let full_path = skill_path.join(&file_path);
    let canonical_skill = std::fs::canonicalize(&skill_path).map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to access skill path: {e}")))?;
    let canonical_full = std::fs::canonicalize(&full_path).map_err(|_| ApiError(StatusCode::NOT_FOUND, "File not found".to_string()))?;
    if !canonical_full.starts_with(&canonical_skill) {
        return Err(ApiError(StatusCode::FORBIDDEN, "Access denied".to_string()));
    }
    let content = tokio::fs::read_to_string(&canonical_full).await.map_err(|_| ApiError(StatusCode::NOT_FOUND, "File not found".to_string()))?;
    Ok(Json(serde_json::json!({"path": canonical_full.to_string_lossy(), "content": content})))
}

async fn put_skill_file(Path((skill_name, file_path)): Path<(String, String)>, Json(body): Json<SkillFileWriteRequest>) -> Result<Json<serde_json::Value>, ApiError> {
    let skill_path = resolve_skill_path(&skill_name)?;
    let full_path = skill_path.join(&file_path);
    let canonical_skill = std::fs::canonicalize(&skill_path).map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to access skill path: {e}")))?;
    let normalized_target = full_path.parent().unwrap_or(&full_path).to_path_buf();
    let canonical_parent = std::fs::canonicalize(&normalized_target).unwrap_or_else(|_| normalized_target.clone());
    if !canonical_parent.starts_with(&canonical_skill) {
        return Err(ApiError(StatusCode::FORBIDDEN, "Access denied".to_string()));
    }
    if let Some(parent) = full_path.parent() {
        tokio::fs::create_dir_all(parent).await.map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to create directory: {e}")))?;
    }
    tokio::fs::write(&full_path, body.content).await.map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to write skill file: {e}")))?;
    Ok(Json(serde_json::json!({"ok": true, "path": full_path.to_string_lossy()})))
}




async fn browse_skills_hub_structured(Json(body): Json<SkillHubBrowseRequest>) -> Result<Json<serde_json::Value>, ApiError> {
    let home = crate::store::home_dir();
    let python = format!("{}/.hermes/hermes-agent/venv/bin/python", home.display());
    let script = r#"
import json, sys
from tools.skills_hub import GitHubAuth, create_source_router
page = int(sys.argv[1])
size = int(sys.argv[2])
source = sys.argv[3]
sources = create_source_router(GitHubAuth())
_TRUST_RANK = {'builtin': 3, 'trusted': 2, 'community': 1}
_PER_SOURCE_LIMIT = {'official': 100, 'skills-sh': 100, 'well-known': 25, 'github': 100, 'clawhub': 50, 'claude-marketplace': 50, 'lobehub': 50}
all_results = []
for src in sources:
    sid = src.source_id()
    if source != 'all' and sid != source and sid != 'official':
        continue
    try:
        limit = _PER_SOURCE_LIMIT.get(sid, 50)
        results = src.search('', limit=limit)
        all_results.extend(results)
    except Exception:
        continue
seen = {}
for r in all_results:
    rank = _TRUST_RANK.get(r.trust_level, 0)
    if r.name not in seen or rank > _TRUST_RANK.get(seen[r.name].trust_level, 0):
        seen[r.name] = r
deduped = list(seen.values())
deduped.sort(key=lambda r: (-_TRUST_RANK.get(r.trust_level, 0), r.source != 'official', r.name.lower()))
total = len(deduped)
total_pages = max(1, (total + size - 1) // size)
page = max(1, min(page, total_pages))
start = (page - 1) * size
end = min(start + size, total)
items = deduped[start:end]
out = {
  'page': page,
  'size': size,
  'total': total,
  'total_pages': total_pages,
  'items': [{
    'name': r.name,
    'description': r.description,
    'source': r.source,
    'identifier': r.identifier,
    'trust_level': r.trust_level,
    'repo': r.repo,
    'path': r.path,
    'tags': r.tags,
    'extra': r.extra,
  } for r in items]
}
print(json.dumps(out))
"#;
    let page = body.page.unwrap_or(1).max(1).to_string();
    let size = body.size.unwrap_or(20).clamp(1, 100).to_string();
    let source = body.source.unwrap_or_else(|| "all".to_string());
    let output = tokio::process::Command::new(&python)
        .arg("-c")
        .arg(script)
        .arg(&page)
        .arg(&size)
        .arg(&source)
        .env("HOME", &home)
        .env("HERMES_HOME", format!("{}/.hermes", home.display()))
        .output()
        .await
        .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to browse skills hub: {e}")))?;
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(ApiError(StatusCode::BAD_GATEWAY, format!("Skills hub browse failed: {stderr}")));
    }
    let value: serde_json::Value = serde_json::from_slice(&output.stdout)
        .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to decode hub browse JSON: {e}")))?;
    Ok(Json(value))
}

async fn search_skills_hub_structured(Json(body): Json<SkillHubSearchRequest>) -> Result<Json<serde_json::Value>, ApiError> {
    let home = crate::store::home_dir();
    let python = format!("{}/.hermes/hermes-agent/venv/bin/python", home.display());
    let script = r#"
import json, sys
from tools.skills_hub import GitHubAuth, create_source_router, unified_search
query = sys.argv[1]
sources = create_source_router(GitHubAuth())
results = unified_search(query, sources, source_filter='all', limit=20)
out = []
for r in results:
    out.append({
        'name': r.name,
        'description': r.description,
        'source': r.source,
        'identifier': r.identifier,
        'trust_level': r.trust_level,
        'repo': r.repo,
        'path': r.path,
        'tags': r.tags,
        'extra': r.extra,
    })
print(json.dumps(out))
"#;
    let output = tokio::process::Command::new(&python)
        .arg("-c")
        .arg(script)
        .arg(&body.query)
        .env("HOME", &home)
        .env("HERMES_HOME", format!("{}/.hermes", home.display()))
        .output()
        .await
        .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to search skills hub: {e}")))?;
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(ApiError(StatusCode::BAD_GATEWAY, format!("Skills hub search failed: {stderr}")));
    }
    let value: serde_json::Value = serde_json::from_slice(&output.stdout)
        .map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to decode hub search JSON: {e}")))?;
    Ok(Json(value))
}

async fn search_skills_hub(Json(body): Json<SkillHubSearchRequest>) -> Result<Json<SkillActionResult>, ApiError> {
    let result = run_skill_cli(&["skills", "search", &body.query]).await?;
    Ok(Json(result))
}

async fn inspect_skill_hub(Json(body): Json<SkillHubIdentifierRequest>) -> Result<Json<SkillActionResult>, ApiError> {
    let result = run_skill_cli(&["skills", "inspect", &body.identifier]).await?;
    Ok(Json(result))
}

async fn install_skill_hub(Json(body): Json<SkillHubIdentifierRequest>) -> Result<Json<SkillActionResult>, ApiError> {
    let result = run_skill_cli(&["skills", "install", &body.identifier, "--yes"]).await?;
    Ok(Json(result))
}

async fn check_skill(Path(skill_name): Path<String>) -> Result<Json<SkillActionResult>, ApiError> {
    let result = run_skill_cli(&["skills", "check", &skill_name]).await?;
    Ok(Json(result))
}

async fn update_skill(Path(skill_name): Path<String>) -> Result<Json<SkillActionResult>, ApiError> {
    let result = run_skill_cli(&["skills", "update", &skill_name]).await?;
    Ok(Json(result))
}

async fn delete_skill(Path(skill_name): Path<String>) -> Result<Json<serde_json::Value>, ApiError> {
    let skill_path = resolve_skill_path(&skill_name)?;
    let root = skills_root();
    let bundled = bundled_skill_names();
    let leaf = skill_path.file_name().and_then(|n| n.to_str()).unwrap_or("").to_string();
    if bundled.contains(&leaf) {
        return Err(ApiError(StatusCode::FORBIDDEN, "Cannot uninstall bundled skills. Use hide instead.".to_string()));
    }
    if !skill_path.starts_with(&root) {
        return Err(ApiError(StatusCode::FORBIDDEN, "Access denied".to_string()));
    }
    tokio::fs::remove_dir_all(&skill_path).await.map_err(|e| ApiError(StatusCode::INTERNAL_SERVER_ERROR, format!("Failed to uninstall skill: {e}")))?;
    Ok(Json(serde_json::json!({"ok": true, "removed": skill_path.to_string_lossy()})))
}

async fn get_config(State(store): State<SharedStore>) -> ApiResult<AppConfig> {
    let store = store.read().await;
    Ok(Json(store.config.clone()))
}

async fn health() -> &'static str {
    "ok"
}

pub async fn start_api_server(store: SharedStore, port: u16) {
    let app = Router::new()
        .route("/health", get(health))
        .route("/api/config", get(get_config))
        .route("/api/agents", get(get_agents))
        .route("/api/agents/discover", post(discover_agents))
        .route("/api/agents/:agent_id", patch(update_agent_profile))
        .route("/api/agents/group", post(set_agent_group))
        .route("/api/groups", get(get_groups).post(create_group))
        .route(
            "/api/groups/:group_id",
            patch(update_group).delete(delete_group),
        )
        .route("/api/tasks", get(get_tasks).post(create_task))
        .route(
            "/api/tasks/:task_id",
            patch(update_task).delete(delete_task),
        )
        .route("/api/notes", get(get_notes).post(create_note))
        .route(
            "/api/notes/:note_id",
            patch(update_note).delete(delete_note),
        )
        .route("/api/rules", get(get_rules).post(create_rule))
        .route(
            "/api/rules/:rule_id",
            patch(update_rule).delete(delete_rule),
        )
        .route("/api/activity", get(get_activity))
        .route("/api/sessions", get(get_sessions).post(create_session))
        .route("/api/sessions/:session_id", patch(update_session).delete(delete_session))
        .route("/api/sessions/:session_id/messages", get(get_messages))
        .route("/api/messages", post(send_message))
        .route("/api/messages/stream", post(send_message_stream))
        .route("/api/cron-jobs", get(get_cron_jobs).post(create_cron_job))
        .route(
            "/api/cron-jobs/:job_id",
            patch(update_cron_job).delete(delete_cron_job),
        )
        .route("/api/cron-jobs/:job_id/runs", get(get_cron_job_runs))
        .route("/api/cron-jobs/:job_id/run", post(run_cron_job_now_api))
        .route("/api/skills", get(get_skills))
        .route("/api/skills/:skill_name", delete(delete_skill))
        .route("/api/skills/:skill_name/check", post(check_skill))
        .route("/api/skills/:skill_name/update", post(update_skill))
        .route("/api/skills/hub/search", post(search_skills_hub))
        .route("/api/skills/hub/search-structured", post(search_skills_hub_structured))
        .route("/api/skills/hub/browse-structured", post(browse_skills_hub_structured))
        .route("/api/skills/hub/inspect", post(inspect_skill_hub))
        .route("/api/skills/hub/install", post(install_skill_hub))
        .route("/api/skills/:skill_name/files", get(get_skill_files))
        .route("/api/skills/:skill_name/files/*file_path", get(get_skill_file).put(put_skill_file))
        .fallback_service({
            // Use absolute path to dist folder for reliable static file serving
            let dist_path = std::path::PathBuf::from(crate::store::home_dir())
                .join("repos")
                .join("hermes-mission-control")
                .join("dist");
            ServeDir::new(&dist_path)
                .not_found_service(ServeFile::new(dist_path.join("index.html")))
        })
        .layer(CorsLayer::permissive())
        .with_state(store);

    let addr = format!("0.0.0.0:{port}");
    let listener = tokio::net::TcpListener::bind(&addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
