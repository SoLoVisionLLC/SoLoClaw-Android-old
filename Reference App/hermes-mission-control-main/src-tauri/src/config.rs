use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfig {
    pub theme: String,
    pub hermes_url: Option<String>,
    pub auto_connect: bool,
    pub notifications_enabled: bool,
    pub activity_log_max_entries: usize,
    pub default_agent_id: Option<String>,
    pub sidebar_collapsed: bool,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            theme: "hermes-official".to_string(),
            hermes_url: Some("http://127.0.0.1:8642".to_string()),
            auto_connect: true,
            notifications_enabled: true,
            activity_log_max_entries: 1000,
            default_agent_id: None,
            sidebar_collapsed: false,
        }
    }
}
