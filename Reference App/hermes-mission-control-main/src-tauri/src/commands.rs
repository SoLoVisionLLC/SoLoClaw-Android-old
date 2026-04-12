use crate::error::{HermesError, Result};
use crate::models::*;
use crate::store::AppStore;
use chrono::Utc;
use std::fs;
use std::path::Path;
use std::process::Stdio;
use std::sync::Arc;
use tauri::State;
use tokio::sync::RwLock;

/// Hermes cron job structure from jobs.json
#[derive(Debug, serde::Deserialize)]
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

#[derive(Debug, serde::Deserialize)]
struct HermesSchedule {
    expr: String,
}

#[derive(Debug, serde::Deserialize)]
struct HermesCronFile {
    jobs: Vec<HermesCronJob>,
}

/// Sync cron jobs from Hermes jobs.json file to store
#[tauri::command]
pub async fn sync_cron_jobs_from_file(
    state: State<'_, Arc<RwLock<AppStore>>>,
) -> Result<Vec<CronJob>> {
    let home = crate::store::home_dir();
    let cron_file = Path::new(&home)
        .join(".hermes")
        .join("cron")
        .join("jobs.json");

    if !cron_file.exists() {
        return Ok(vec![]);
    }

    let content = tokio::fs::read_to_string(&cron_file)
        .await
        .map_err(|e| HermesError::Store(format!("Failed to read cron jobs file: {}", e)))?;

    let cron_file_data: HermesCronFile = serde_json::from_str(&content)
        .map_err(|e| HermesError::Store(format!("Failed to parse cron jobs file: {}", e)))?;

    let mut store = state.write().await;
    let mut jobs = vec![];

    for hermes_job in cron_file_data.jobs {
        // Convert HermesCronJob to CronJob
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

        // Determine consecutive errors from last_status
        let consecutive_errors = match hermes_job.last_status.as_deref() {
            Some("error") => 1,
            _ => 0,
        };

        let job = CronJob {
            id: hermes_job.id.clone(),
            name: hermes_job.name,
            schedule: hermes_job.schedule.expr,
            command: hermes_job.prompt,
            agent_id: hermes_job.agent_id,
            session_target: "main".to_string(), // Default to main session
            enabled: hermes_job.enabled,
            delivery_mode: hermes_job.deliver.clone(),
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

        store.cron_jobs.insert(hermes_job.id, job.clone());
        jobs.push(job);
    }

    Ok(jobs)
}

async fn normalize_hermes_cron_jobs_file_from_store() -> Result<()> {
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
            .map_err(|e| HermesError::Store(format!("Failed to normalize cron file: {}", e)))?;
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(HermesError::Store(format!(
            "Cron normalization failed: {}",
            stderr
        )));
    }
    Ok(())
}

async fn persist_cron_jobs_to_file_from_store(store: &AppStore) -> Result<()> {
    use serde_json::{json, Map, Value};
    use std::collections::HashMap;

    let home = crate::store::home_dir();
    let cron_dir = Path::new(&home).join(".hermes").join("cron");
    let cron_file = cron_dir.join("jobs.json");
    tokio::fs::create_dir_all(&cron_dir)
        .await
        .map_err(|e| HermesError::Store(format!("Failed to create cron dir: {}", e)))?;

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
        .map_err(|e| HermesError::Store(format!("Failed to write cron jobs file: {}", e)))?;

    Ok(())
}

/// Invoke Hermes CLI to send a chat message.
/// Returns (cleaned_response, hermes_session_id_used).
/// Pass `hermes_session_id = None` for a fresh session, or `Some(id)` to resume.
async fn invoke_hermes_chat(
    agent_id: &str,
    message: &str,
    hermes_session_id: Option<&str>,
) -> Result<(String, String)> {
    use tokio::process::Command;

    let home = crate::store::home_dir();
    let profile_dir = Path::new(&home)
        .join(".hermes")
        .join("profiles")
        .join(agent_id);

    // Build the command
    let mut cmd = Command::new("bash");
    let escaped_msg = message.replace('"', "\\\"");
    let hermes_cmd = if let Some(sid) = hermes_session_id {
        format!("hermes chat -q \"{}\" -Q --resume {} --source mission-control", escaped_msg, sid)
    } else {
        format!("hermes chat -q \"{}\" -Q --source mission-control", escaped_msg)
    };
    cmd.arg("-c").arg(&hermes_cmd);

    // Set environment to use the agent's profile directory as HERMES_HOME
    cmd.env(
        "HOME",
        crate::store::home_dir().to_string_lossy().to_string(),
    );
    cmd.env("HERMES_HOME", profile_dir.display().to_string());
    cmd.current_dir(&profile_dir);

    eprintln!(
        "[DEBUG] Running from {} with HERMES_HOME={} (session={})",
        profile_dir.display(),
        profile_dir.display(),
        hermes_session_id.unwrap_or("NEW")
    );

    // Execute command
    let output = cmd
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .output()
        .await
        .map_err(|e| HermesError::Unknown(format!("Failed to execute hermes chat: {}", e)))?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    let combined_output = format!("{}\n{}", stdout, stderr);

    if !output.status.success() && !combined_output.contains("session_id:") {
        return Err(HermesError::Unknown(format!(
            "Hermes CLI error: {}",
            stderr
        )));
    }

    let (cleaned, session_id) = clean_hermes_output_with_session(&combined_output);
    Ok((cleaned, session_id))
}

/// Clean Hermes CLI output by removing banner, session info, and internal thoughts.
/// Also extracts and returns the Hermes session_id so callers can persist it.
fn clean_hermes_output_with_session(output: &str) -> (String, String) {
    let mut lines: Vec<&str> = output.lines().collect();
    let mut session_id = String::new();

    // Remove banner lines, internal thoughts, and decorators
    lines.retain(|line| {
        !line.contains('⚕') &&
        !line.starts_with('╭') &&
        !line.starts_with('╰') &&
        !line.starts_with('┊') &&  // Remove progress/decorator lines
        !line.contains("────") &&
        !line.contains("preparing") &&
        !line.contains("mcp_memory") &&
        !line.contains("memory +user") &&
        !line.contains("0.0s") // Remove timing markers
    });

    // Join remaining lines
    let mut result = lines.join("\n");

    // Extract and remove session_id from end (format: "session_id: <id>")
    if let Some(pos) = result.rfind("session_id:") {
        let after = result[pos..].trim();
        // session_id: 20260404_081440_fb0744
        if after.starts_with("session_id:") {
            session_id = after["session_id:".len()..].trim().to_string();
        }
        result = result[..pos].trim().to_string();
    }

    (result, session_id)
}

#[tauri::command]
pub async fn get_agent_status(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: Option<String>,
) -> Result<Vec<AgentStatus>> {
    use std::fs;

    let mut store = state.write().await;
    tracing::info!(
        "get_agent_status called, store has {} agents",
        store.agents.len()
    );

    // If specific agent requested, return just that one
    if let Some(id) = agent_id {
        if let Some(agent) = store.agents.get(&id) {
            return Ok(vec![agent.clone()]);
        }
        return Err(HermesError::AgentNotFound(id));
    }

    // If store is empty, auto-discover from filesystem
    if store.agents.is_empty() {
        let profiles_dir = crate::store::home_dir().join(".hermes").join("profiles");

        if let Ok(entries) = fs::read_dir(profiles_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_dir() {
                    let agent_id = path
                        .file_name()
                        .and_then(|n| n.to_str())
                        .unwrap_or("unknown")
                        .to_string();

                    // Read SOUL.md for role/description
                    let soul_path = path.join("SOUL.md");
                    let (role, bio) = if let Ok(content) = fs::read_to_string(&soul_path) {
                        parse_soul_md(&content)
                    } else {
                        (Some(agent_id.clone()), Some("Hermes Agent".to_string()))
                    };

                    // Read config.yaml for model info
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
                        message_count: 0,
                        tool_calls_count: 0,
                        avatar: None,
                        bio,
                        role,
                        capabilities,
                        group_id: None,
                        metadata: None,
                    };

                    store.agents.insert(agent_id.clone(), agent.clone());

                    // Add activity log
                    let activity = Activity::new(
                        agent_id.clone(),
                        ActivityType::AgentConnected,
                        format!("Loaded Hermes agent: {}", agent_id),
                    );
                    store.add_activity(activity);
                }
            }
        }
    }

    Ok(store.agents.values().cloned().collect())
}

#[tauri::command]
pub async fn send_message(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: String,
    session_id: String,
    content: String,
) -> Result<(Message, Message)> {
    // Fetch hermes_session_id from the session before taking the lock
    let hermes_session_id = {
        let store = state.read().await;
        store.sessions.get(&session_id)
            .and_then(|s| s.hermes_session_id.clone())
    };

    // Create and store user message
    let user_message = Message {
        id: uuid::Uuid::new_v4().to_string(),
        session_id: session_id.clone(),
        role: MessageRole::User,
        content: content.clone(),
        created_at: Utc::now(),
        tool_calls: None,
    };
    {
        let mut store = state.write().await;
        store.messages.entry(session_id.clone())
            .or_insert_with(Vec::new)
            .push(user_message.clone());
        if let Some(session) = store.sessions.get_mut(&session_id) {
            session.message_count += 1;
            session.updated_at = Utc::now();
        }
    }

    // Get agent info for fallback message
    let agent = {
        let store = state.read().await;
        store.agents.get(&agent_id).cloned()
    };

    // Call Hermes CLI — pass hermes_session_id for context continuity
    eprintln!("[DEBUG] Sending message to agent: {}", agent_id);
    eprintln!("[DEBUG] Message content: {}", content);
    eprintln!("[DEBUG] Hermes session: {:?}", hermes_session_id);

    let (response_content, new_hermes_session_id) = match invoke_hermes_chat(
        &agent_id,
        &content,
        hermes_session_id.as_deref(),
    ).await {
        Ok((response, sid)) => {
            eprintln!("[DEBUG] Hermes CLI response received (hermes_session_id={})", sid);
            (response, sid)
        }
        Err(e) => {
            eprintln!("[DEBUG] Hermes CLI failed: {}", e);
            let agent_name = agent.map(|a| a.name).unwrap_or_else(|| "Agent".to_string());
            (
                format!(
                    "Hello! I'm {}. You said: \"{}\"\n\n\
                     (Note: Hermes CLI error: {}. Make sure 'hermes' is installed and configured.)",
                    agent_name,
                    content.chars().take(50).collect::<String>(),
                    e
                ),
                String::new(),
            )
        }
    };

    // Store agent response and persist hermes_session_id
    let agent_message = Message {
        id: uuid::Uuid::new_v4().to_string(),
        session_id: session_id.clone(),
        role: MessageRole::Assistant,
        content: response_content,
        created_at: Utc::now(),
        tool_calls: None,
    };
    {
        let mut store = state.write().await;
        store.messages.entry(session_id.clone())
            .or_insert_with(Vec::new)
            .push(agent_message.clone());
        if let Some(session) = store.sessions.get_mut(&session_id) {
            // Persist hermes_session_id on first message (when it's still empty)
            if session.hermes_session_id.is_none() && !new_hermes_session_id.is_empty() {
                session.hermes_session_id = Some(new_hermes_session_id.clone());
                eprintln!("[DEBUG] Stored hermes_session_id: {}", new_hermes_session_id);
            }
        }
        if let Some(agent) = store.agents.get_mut(&agent_id) {
            agent.message_count += 2;
        }
        let activity = Activity::new(
            agent_id.clone(),
            ActivityType::Message,
            format!("Chat message in session {}", session_id),
        );
        store.add_activity(activity);
    }

    Ok((user_message, agent_message))
}

#[tauri::command]
pub async fn get_messages(
    state: State<'_, Arc<RwLock<AppStore>>>,
    session_id: String,
) -> Result<Vec<Message>> {
    let store = state.read().await;

    let messages = store.messages.get(&session_id).cloned().unwrap_or_default();

    Ok(messages)
}

#[tauri::command]
pub async fn get_sessions(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: Option<String>,
) -> Result<Vec<Session>> {
    let store = state.read().await;

    let sessions: Vec<Session> = if let Some(id) = agent_id {
        store
            .sessions
            .values()
            .filter(|s| s.agent_id == id)
            .cloned()
            .collect()
    } else {
        store.sessions.values().cloned().collect()
    };

    Ok(sessions)
}

#[tauri::command]
pub async fn get_recent_sessions(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: Option<String>,
    limit: Option<usize>,
) -> Result<Vec<Session>> {
    let store = state.read().await;
    let limit = limit.unwrap_or(20);

    let mut sessions: Vec<Session> = if let Some(id) = agent_id {
        store
            .sessions
            .values()
            .filter(|s| s.agent_id == id)
            .cloned()
            .collect()
    } else {
        store.sessions.values().cloned().collect()
    };

    // Sort by updated_at (most recent first)
    sessions.sort_by(|a, b| b.updated_at.cmp(&a.updated_at));

    // Limit results
    sessions.truncate(limit);

    Ok(sessions)
}

#[tauri::command]
pub async fn create_session(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: String,
    name: String,
) -> Result<Session> {
    let store = state.write().await;

    let session = Session {
        id: uuid::Uuid::new_v4().to_string(),
        name: name.clone(),
        agent_id: agent_id.clone(),
        created_at: Utc::now(),
        updated_at: Utc::now(),
        message_count: 0,
        archived: false,
        hermes_session_id: None,
    };

    // Also create session in webapi
    let hermes_url = store
        .config
        .hermes_url
        .clone()
        .unwrap_or_else(|| "http://127.0.0.1:8642".to_string());
    let session_id = session.id.clone();
    drop(store); // Release lock before HTTP call

    let client = reqwest::Client::new();
    let create_url = format!("{}/api/sessions", hermes_url.trim_end_matches('/'));

    match client
        .post(&create_url)
        .json(&serde_json::json!({
            "id": session_id,
            "title": name,
            "agent_id": agent_id,
        }))
        .send()
        .await
    {
        Ok(response) => {
            if response.status().is_success() {
                eprintln!("[DEBUG] Session created in webapi: {}", session_id);
            } else {
                eprintln!(
                    "[DEBUG] Failed to create session in webapi: {}",
                    response.status()
                );
            }
        }
        Err(e) => {
            eprintln!("[DEBUG] Error creating session in webapi: {}", e);
        }
    }

    // Store locally
    let mut store = state.write().await;
    store.sessions.insert(session.id.clone(), session.clone());

    let activity = Activity::new(
        agent_id,
        ActivityType::Info,
        format!("Created new session: {}", session.name),
    );
    store.add_activity(activity);

    // Persist so the new session survives app restarts
    let _ = store.persist();

    Ok(session)
}

#[tauri::command]
pub async fn update_session_name(
    state: State<'_, Arc<RwLock<AppStore>>>,
    session_id: String,
    name: String,
) -> Result<Session> {
    let mut store = state.write().await;

    let session = store
        .sessions
        .get_mut(&session_id)
        .ok_or_else(|| HermesError::SessionNotFound(session_id.clone()))?;

    session.name = name;
    session.updated_at = Utc::now();

    Ok(session.clone())
}

#[tauri::command]
pub async fn get_tasks(
    state: State<'_, Arc<RwLock<AppStore>>>,
    status: Option<TaskStatus>,
    agent_id: Option<String>,
) -> Result<Vec<Task>> {
    let store = state.read().await;

    let mut tasks: Vec<Task> = if let Some(aid) = agent_id {
        store
            .tasks
            .values()
            .filter(|t| t.agent_id.as_ref() == Some(&aid))
            .cloned()
            .collect()
    } else {
        store.tasks.values().cloned().collect()
    };

    if let Some(s) = status {
        tasks.retain(|t| t.status == s);
    }

    tasks.sort_by(|a, b| b.updated_at.cmp(&a.updated_at));
    Ok(tasks)
}

#[tauri::command]
pub async fn create_task(
    state: State<'_, Arc<RwLock<AppStore>>>,
    title: String,
    description: Option<String>,
    priority: Option<TaskPriority>,
    agent_id: Option<String>,
) -> Result<Task> {
    let mut store = state.write().await;

    let mut task = Task::new(title);
    task.description = description;
    if let Some(p) = priority {
        task.priority = p;
    }
    task.agent_id = agent_id.clone();

    store.tasks.insert(task.id.clone(), task.clone());

    let activity = Activity::new(
        agent_id.unwrap_or_else(|| "system".to_string()),
        ActivityType::TaskCreated,
        format!("Created task: {}", task.title),
    );
    store.add_activity(activity);

    Ok(task)
}

#[tauri::command]
pub async fn update_task(
    state: State<'_, Arc<RwLock<AppStore>>>,
    task_id: String,
    title: Option<String>,
    description: Option<String>,
    status: Option<TaskStatus>,
    priority: Option<TaskPriority>,
) -> Result<Task> {
    let mut store = state.write().await;

    let task = store
        .tasks
        .get_mut(&task_id)
        .ok_or_else(|| HermesError::TaskNotFound(task_id.clone()))?;

    if let Some(t) = title {
        task.title = t;
    }
    if let Some(d) = description {
        task.description = Some(d);
    }
    if let Some(s) = status {
        task.status = s;
        if s == TaskStatus::Done {
            task.completed_at = Some(Utc::now());
        }
    }
    if let Some(p) = priority {
        task.priority = p;
    }
    task.updated_at = Utc::now();

    let task_clone = task.clone();

    let activity = Activity::new(
        task.agent_id
            .clone()
            .unwrap_or_else(|| "system".to_string()),
        ActivityType::TaskUpdated,
        format!("Updated task: {}", task_clone.title),
    );
    store.add_activity(activity);

    Ok(task_clone)
}

#[tauri::command]
pub async fn delete_task(state: State<'_, Arc<RwLock<AppStore>>>, task_id: String) -> Result<()> {
    let mut store = state.write().await;

    store
        .tasks
        .remove(&task_id)
        .ok_or_else(|| HermesError::TaskNotFound(task_id))?;

    Ok(())
}

#[tauri::command]
pub async fn get_activity_log(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: Option<String>,
    limit: Option<usize>,
) -> Result<Vec<Activity>> {
    let store = state.read().await;

    let mut activities: Vec<Activity> = if let Some(aid) = agent_id {
        store
            .activities
            .iter()
            .filter(|a| a.agent_id == aid)
            .cloned()
            .collect()
    } else {
        store.activities.clone()
    };

    activities.sort_by(|a, b| b.timestamp.cmp(&a.timestamp));

    if let Some(l) = limit {
        activities.truncate(l);
    }

    Ok(activities)
}

#[tauri::command]
pub async fn add_activity(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: String,
    activity_type: ActivityType,
    message: String,
) -> Result<Activity> {
    let mut store = state.write().await;

    let activity = Activity::new(agent_id, activity_type, message);
    store.add_activity(activity.clone());

    Ok(activity)
}

#[tauri::command]
pub async fn get_notes(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: Option<String>,
) -> Result<Vec<Note>> {
    let store = state.read().await;

    let mut notes: Vec<Note> = if let Some(aid) = agent_id {
        store
            .notes
            .values()
            .filter(|n| n.agent_id.as_ref() == Some(&aid))
            .cloned()
            .collect()
    } else {
        store.notes.values().cloned().collect()
    };

    notes.sort_by(|a, b| {
        let pin_cmp = b.pinned.cmp(&a.pinned);
        if pin_cmp != std::cmp::Ordering::Equal {
            return pin_cmp;
        }
        b.updated_at.cmp(&a.updated_at)
    });

    Ok(notes)
}

#[tauri::command]
pub async fn create_note(
    state: State<'_, Arc<RwLock<AppStore>>>,
    title: String,
    content: String,
    agent_id: Option<String>,
) -> Result<Note> {
    let mut store = state.write().await;

    let mut note = Note::new(title, content);
    note.agent_id = agent_id;

    store.notes.insert(note.id.clone(), note.clone());

    Ok(note)
}

#[tauri::command]
pub async fn update_note(
    state: State<'_, Arc<RwLock<AppStore>>>,
    note_id: String,
    title: Option<String>,
    content: Option<String>,
    seen_by_agent: Option<bool>,
    pinned: Option<bool>,
) -> Result<Note> {
    let mut store = state.write().await;

    let note = store
        .notes
        .get_mut(&note_id)
        .ok_or_else(|| HermesError::NoteNotFound(note_id.clone()))?;

    if let Some(t) = title {
        note.title = t;
    }
    if let Some(c) = content {
        note.content = c;
    }
    if let Some(s) = seen_by_agent {
        note.seen_by_agent = s;
    }
    if let Some(p) = pinned {
        note.pinned = p;
    }
    note.updated_at = Utc::now();

    Ok(note.clone())
}

#[tauri::command]
pub async fn delete_note(state: State<'_, Arc<RwLock<AppStore>>>, note_id: String) -> Result<()> {
    let mut store = state.write().await;

    store
        .notes
        .remove(&note_id)
        .ok_or_else(|| HermesError::NoteNotFound(note_id))?;

    Ok(())
}

#[tauri::command]
pub async fn get_config(
    state: State<'_, Arc<RwLock<AppStore>>>,
) -> Result<crate::config::AppConfig> {
    let store = state.read().await;
    Ok(store.config.clone())
}

#[tauri::command]
pub async fn update_config(
    state: State<'_, Arc<RwLock<AppStore>>>,
    config: crate::config::AppConfig,
) -> Result<crate::config::AppConfig> {
    let mut store = state.write().await;
    store.config = config.clone();
    Ok(config)
}

#[tauri::command]
pub async fn discover_hermes_instances() -> Result<Vec<HermesInstance>> {
    // TODO: Implement mDNS or network discovery
    // For now, return empty list
    Ok(vec![])
}

#[tauri::command]
pub async fn connect_to_hermes(
    state: State<'_, Arc<RwLock<AppStore>>>,
    url: String,
) -> Result<HermesInstance> {
    // Test connection to Hermes instance
    let client = reqwest::Client::new();

    let health_url = format!("{}/health", url.trim_end_matches('/'));
    let response = client
        .get(&health_url)
        .timeout(std::time::Duration::from_secs(5))
        .send()
        .await
        .map_err(|e| HermesError::Http(e.to_string()))?;

    if !response.status().is_success() {
        return Err(HermesError::Http(format!(
            "Hermes instance returned status: {}",
            response.status()
        )));
    }

    let instance = HermesInstance {
        id: uuid::Uuid::new_v4().to_string(),
        name: "Connected Hermes".to_string(),
        url: url.clone(),
        version: None,
        status: InstanceStatus::Online,
        last_seen: Utc::now(),
    };

    let mut store = state.write().await;
    store
        .instances
        .insert(instance.id.clone(), instance.clone());

    Ok(instance)
}

#[tauri::command]
pub async fn discover_agents(state: State<'_, Arc<RwLock<AppStore>>>) -> Result<Vec<AgentStatus>> {
    use std::fs;

    let mut store = state.write().await;
    let mut discovered = vec![];
    tracing::info!("discover_agents called");
    let profiles_dir = crate::store::home_dir().join(".hermes").join("profiles");

    if let Ok(entries) = fs::read_dir(profiles_dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                let agent_id = path
                    .file_name()
                    .and_then(|n| n.to_str())
                    .unwrap_or("unknown")
                    .to_string();

                // Read SOUL.md for role/description
                let soul_path = path.join("SOUL.md");
                let (role, bio) = if let Ok(content) = fs::read_to_string(&soul_path) {
                    parse_soul_md(&content)
                } else {
                    (Some(agent_id.clone()), Some("Hermes Agent".to_string()))
                };

                // Read config.yaml for model info
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
                    message_count: 0,
                    tool_calls_count: 0,
                    avatar: None,
                    bio,
                    role,
                    capabilities,
                    group_id: None,
                    metadata: None,
                };

                store.agents.insert(agent_id.clone(), agent.clone());
                discovered.push(agent);

                // Add activity log
                let activity = Activity::new(
                    agent_id.clone(),
                    ActivityType::AgentConnected,
                    format!("Loaded Hermes agent: {}", agent_id),
                );
                store.add_activity(activity);
            }
        }
    }

    Ok(discovered)
}

fn parse_soul_md(content: &str) -> (Option<String>, Option<String>) {
    let mut role = None;
    let mut bio = None;

    for line in content.lines() {
        if line.starts_with("You are **") && line.contains("**") {
            // Extract role from "You are **Name**, Role."
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
        if line.starts_with("## Identity") {
            // Next non-empty line is the bio
            continue;
        }
        if bio.is_none()
            && role.is_some()
            && !line.trim().is_empty()
            && !line.starts_with("#")
            && !line.starts_with("-")
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
            && !trimmed.starts_with("-")
            && !trimmed.is_empty()
            && !trimmed.starts_with("#")
        {
            if trimmed.starts_with("agent:") || trimmed.starts_with("terminal:") {
                in_toolsets = false;
            }
        }
    }

    (provider, model, capabilities)
}

#[tauri::command]
pub async fn update_agent_profile(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: String,
    name: Option<String>,
    bio: Option<String>,
    role: Option<String>,
    avatar: Option<String>,
    capabilities: Option<Vec<String>>,
) -> Result<AgentStatus> {
    let mut store = state.write().await;

    let agent = store
        .agents
        .get_mut(&agent_id)
        .ok_or_else(|| HermesError::AgentNotFound(agent_id.clone()))?;

    if let Some(n) = name {
        agent.name = n;
    }
    if let Some(b) = bio {
        agent.bio = Some(b);
    }
    if let Some(r) = role {
        agent.role = Some(r);
    }
    if let Some(a) = avatar {
        agent.avatar = Some(a);
    }
    if let Some(c) = capabilities {
        agent.capabilities = c;
    }

    // Persist avatar to agent_avatars.json so it survives backend restarts
    if let Some(ref av) = agent.avatar {
        let avatars_path = crate::store::home_dir().join(".hermes").join("agent_avatars.json");
        let avatars: serde_json::Map<String, serde_json::Value> =
            fs::read_to_string(&avatars_path)
                .ok()
                .and_then(|c| serde_json::from_str(&c).ok())
                .unwrap_or_default();
        let mut updated = avatars;
        updated.insert(agent_id.clone(), serde_json::Value::String(av.clone()));
        if let Some(parent) = avatars_path.parent() {
            let _ = fs::create_dir_all(parent);
        }
        if let Err(e) = fs::write(&avatars_path, serde_json::to_string_pretty(&updated).unwrap()) {
            eprintln!("Failed to persist agent_avatars.json: {e}");
        }
    }

    Ok(agent.clone())
}

#[tauri::command]
pub async fn set_agent_group(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: String,
    group_id: Option<String>,
) -> Result<AgentStatus> {
    let mut store = state.write().await;

    // Get the old group id first
    let old_group_id = store
        .agents
        .get(&agent_id)
        .map(|a| a.group_id.clone())
        .flatten();

    // Remove from old group if present
    if let Some(ref old_gid) = old_group_id {
        if let Some(group) = store.groups.get_mut(old_gid) {
            group.agent_ids.retain(|id| id != &agent_id);
        }
    }

    // Add to new group
    if let Some(ref gid) = group_id {
        if let Some(group) = store.groups.get_mut(gid) {
            if !group.agent_ids.contains(&agent_id) {
                group.agent_ids.push(agent_id.clone());
            }
        }
    }

    // Update agent's group_id
    let agent = store
        .agents
        .get_mut(&agent_id)
        .ok_or_else(|| HermesError::AgentNotFound(agent_id.clone()))?;

    agent.group_id = group_id;
    Ok(agent.clone())
}

#[tauri::command]
pub async fn get_groups(state: State<'_, Arc<RwLock<AppStore>>>) -> Result<Vec<AgentGroup>> {
    let store = state.read().await;
    Ok(store.groups.values().cloned().collect())
}

#[tauri::command]
pub async fn create_group(
    state: State<'_, Arc<RwLock<AppStore>>>,
    name: String,
    description: Option<String>,
    color: Option<String>,
) -> Result<AgentGroup> {
    let mut store = state.write().await;

    let group = AgentGroup {
        id: uuid::Uuid::new_v4().to_string(),
        name,
        description,
        icon: None,
        color,
        agent_ids: vec![],
        created_at: Utc::now(),
        updated_at: Utc::now(),
    };

    store.groups.insert(group.id.clone(), group.clone());
    Ok(group)
}

#[tauri::command]
pub async fn update_group(
    state: State<'_, Arc<RwLock<AppStore>>>,
    group_id: String,
    name: Option<String>,
    description: Option<String>,
    color: Option<String>,
) -> Result<AgentGroup> {
    let mut store = state.write().await;

    let group = store
        .groups
        .get_mut(&group_id)
        .ok_or_else(|| HermesError::Store(format!("Group not found: {}", group_id)))?;

    if let Some(n) = name {
        group.name = n;
    }
    if let Some(d) = description {
        group.description = Some(d);
    }
    if let Some(c) = color {
        group.color = Some(c);
    }
    group.updated_at = Utc::now();

    Ok(group.clone())
}

#[tauri::command]
pub async fn delete_group(state: State<'_, Arc<RwLock<AppStore>>>, group_id: String) -> Result<()> {
    let mut store = state.write().await;

    // Remove group reference from all agents
    for agent in store.agents.values_mut() {
        if agent.group_id == Some(group_id.clone()) {
            agent.group_id = None;
        }
    }

    store
        .groups
        .remove(&group_id)
        .ok_or_else(|| HermesError::Store(format!("Group not found: {}", group_id)))?;

    Ok(())
}

#[tauri::command]
pub async fn add_agent_to_group(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: String,
    group_id: String,
) -> Result<()> {
    let mut store = state.write().await;

    let group = store
        .groups
        .get_mut(&group_id)
        .ok_or_else(|| HermesError::Store(format!("Group not found: {}", group_id)))?;

    if !group.agent_ids.contains(&agent_id) {
        group.agent_ids.push(agent_id.clone());
    }

    if let Some(agent) = store.agents.get_mut(&agent_id) {
        agent.group_id = Some(group_id);
    }

    Ok(())
}

#[tauri::command]
pub async fn remove_agent_from_group(
    state: State<'_, Arc<RwLock<AppStore>>>,
    agent_id: String,
    group_id: String,
) -> Result<()> {
    let mut store = state.write().await;

    if let Some(group) = store.groups.get_mut(&group_id) {
        group.agent_ids.retain(|id| id != &agent_id);
    }

    if let Some(agent) = store.agents.get_mut(&agent_id) {
        if agent.group_id == Some(group_id) {
            agent.group_id = None;
        }
    }

    Ok(())
}

// ==================== CRON JOBS ====================

#[tauri::command]
pub async fn get_cron_jobs(
    state: State<'_, Arc<RwLock<AppStore>>>,
    filter: Option<String>,
    agent_id: Option<String>,
) -> Result<Vec<CronJob>> {
    let store = state.read().await;

    let mut jobs: Vec<CronJob> = store.cron_jobs.values().cloned().collect();

    // Filter by agent if specified
    if let Some(aid) = agent_id {
        jobs.retain(|j| j.agent_id.as_ref() == Some(&aid));
    }

    // Apply status filter
    if let Some(f) = filter {
        match f.as_str() {
            "enabled" => jobs.retain(|j| j.enabled),
            "disabled" => jobs.retain(|j| !j.enabled),
            "failing" => jobs.retain(|j| j.consecutive_errors > 0),
            "healthy" => jobs.retain(|j| j.consecutive_errors == 0 && j.enabled),
            _ => {}
        }
    }

    // Sort by next run time
    jobs.sort_by(|a, b| match (a.next_run, b.next_run) {
        (Some(a_time), Some(b_time)) => a_time.cmp(&b_time),
        (Some(_), None) => std::cmp::Ordering::Less,
        (None, Some(_)) => std::cmp::Ordering::Greater,
        (None, None) => std::cmp::Ordering::Equal,
    });

    Ok(jobs)
}

#[tauri::command]
pub async fn create_cron_job(
    state: State<'_, Arc<RwLock<AppStore>>>,
    name: String,
    schedule: String,
    command: String,
    agent_id: Option<String>,
    session_target: Option<String>,
    enabled: Option<bool>,
) -> Result<CronJob> {
    let mut store = state.write().await;

    let mut job = CronJob::new(name, schedule, command);
    job.agent_id = agent_id;
    if let Some(st) = session_target {
        job.session_target = st;
    }
    if let Some(e) = enabled {
        job.enabled = e;
    }

    store.cron_jobs.insert(job.id.clone(), job.clone());
    persist_cron_jobs_to_file_from_store(&store).await?;
    normalize_hermes_cron_jobs_file_from_store().await?;

    let activity = Activity::new(
        job.agent_id.clone().unwrap_or_else(|| "system".to_string()),
        ActivityType::Info,
        format!("Created cron job: {}", job.name),
    );
    store.add_activity(activity);

    Ok(job)
}

#[tauri::command]
pub async fn update_cron_job(
    state: State<'_, Arc<RwLock<AppStore>>>,
    job_id: String,
    name: Option<String>,
    schedule: Option<String>,
    command: Option<String>,
    agent_id: Option<String>,
    session_target: Option<String>,
    enabled: Option<bool>,
    delivery_mode: Option<String>,
    delivery_target: Option<String>,
    provider: Option<String>,
    model: Option<String>,
) -> Result<CronJob> {
    let mut store = state.write().await;

    let job = store
        .cron_jobs
        .get_mut(&job_id)
        .ok_or_else(|| HermesError::Store(format!("Cron job not found: {}", job_id)))?;

    if let Some(n) = name {
        job.name = n;
    }
    if let Some(s) = schedule {
        job.schedule = s;
    }
    if let Some(c) = command {
        job.command = c;
    }
    if let Some(a) = agent_id {
        job.agent_id = Some(a);
    }
    if let Some(st) = session_target {
        job.session_target = st;
    }
    if let Some(e) = enabled {
        job.enabled = e;
    }
    if let Some(dm) = delivery_mode {
        job.delivery_mode = Some(dm);
    }
    if let Some(dt) = delivery_target {
        job.delivery_target = Some(dt);
    }
    if let Some(p) = provider {
        job.provider = Some(p);
    }
    if let Some(m) = model {
        job.model = Some(m);
    }

    job.updated_at = Utc::now();
    let job_clone = job.clone();
    persist_cron_jobs_to_file_from_store(&store).await?;
    normalize_hermes_cron_jobs_file_from_store().await?;

    Ok(job_clone)
}

#[tauri::command]
pub async fn delete_cron_job(
    state: State<'_, Arc<RwLock<AppStore>>>,
    job_id: String,
) -> Result<()> {
    let mut store = state.write().await;

    store
        .cron_jobs
        .remove(&job_id)
        .ok_or_else(|| HermesError::Store(format!("Cron job not found: {}", job_id)))?;

    // Remove associated runs
    store.cron_runs.retain(|r| r.job_id != job_id);
    persist_cron_jobs_to_file_from_store(&store).await?;
    normalize_hermes_cron_jobs_file_from_store().await?;

    Ok(())
}

#[tauri::command]
pub async fn run_cron_job_now(
    state: State<'_, Arc<RwLock<AppStore>>>,
    job_id: String,
) -> Result<String> {
    let store = state.read().await;

    let job = store
        .cron_jobs
        .get(&job_id)
        .ok_or_else(|| HermesError::Store(format!("Cron job not found: {}", job_id)))?;

    // Return command to execute (frontend will handle actual execution)
    Ok(job.command.clone())
}

#[tauri::command]
pub async fn get_cron_job_runs(
    state: State<'_, Arc<RwLock<AppStore>>>,
    job_id: String,
    limit: Option<usize>,
) -> Result<Vec<CronJobRun>> {
    let store = state.read().await;

    let mut runs: Vec<CronJobRun> = store
        .cron_runs
        .iter()
        .filter(|r| r.job_id == job_id)
        .cloned()
        .collect();

    // Sort by started_at desc
    runs.sort_by(|a, b| b.started_at.cmp(&a.started_at));

    let limit = limit.unwrap_or(20);
    runs.truncate(limit);

    Ok(runs)
}
