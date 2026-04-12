use crate::config::AppConfig;
use crate::models::*;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;

/// Returns the user's home directory, falling back to "/home/solo".
pub fn home_dir() -> PathBuf {
    std::env::var("HOME")
        .ok()
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("/home/solo"))
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppStore {
    pub config: AppConfig,
    pub agents: HashMap<String, AgentStatus>,
    pub sessions: HashMap<String, Session>,
    pub messages: HashMap<String, Vec<Message>>, // session_id -> messages
    pub tasks: HashMap<String, Task>,
    pub activities: Vec<Activity>,
    pub notes: HashMap<String, Note>,
    pub instances: HashMap<String, HermesInstance>,
    pub groups: HashMap<String, AgentGroup>,
    pub cron_jobs: HashMap<String, CronJob>,
    pub cron_runs: Vec<CronJobRun>,
    pub rules: HashMap<String, Rule>,
}

impl Default for AppStore {
    fn default() -> Self {
        Self {
            config: AppConfig::default(),
            agents: HashMap::new(),
            sessions: HashMap::new(),
            messages: HashMap::new(),
            tasks: HashMap::new(),
            activities: Vec::new(),
            notes: HashMap::new(),
            instances: HashMap::new(),
            groups: HashMap::new(),
            cron_jobs: HashMap::new(),
            cron_runs: Vec::new(),
            rules: HashMap::new(),
        }
    }
}

impl AppStore {
    pub fn persistence_path() -> PathBuf {
        home_dir()
            .join(".hermes")
            .join("mission-control")
            .join("store.json")
    }

    pub fn load_or_default() -> Self {
        let path = Self::persistence_path();
        match fs::read_to_string(&path) {
            Ok(content) => serde_json::from_str(&content).unwrap_or_else(|_| Self::default()),
            Err(_) => Self::default(),
        }
    }

    pub fn persist(&self) -> Result<(), String> {
        let path = Self::persistence_path();
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|e| format!("Failed to create store dir: {e}"))?;
        }
        let payload = serde_json::to_string_pretty(self)
            .map_err(|e| format!("Failed to serialize store: {e}"))?;
        fs::write(&path, payload).map_err(|e| format!("Failed to write store: {e}"))?;
        Ok(())
    }

    pub fn add_activity(&mut self, activity: Activity) {
        self.activities.push(activity);

        // Trim old activities if needed
        let max_entries = self.config.activity_log_max_entries;
        if self.activities.len() > max_entries {
            let excess = self.activities.len() - max_entries;
            self.activities.drain(0..excess);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;
    use std::time::{SystemTime, UNIX_EPOCH};

    #[test]
    fn it_works() {
        assert_eq!(2 + 2, 4);
    }
}
