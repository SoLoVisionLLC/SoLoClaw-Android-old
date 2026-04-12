use crate::error::{HermesError, Result};
use crate::models::{AgentStatus, Message, Session};
use reqwest::Client;
use std::time::Duration;

pub struct AgentClient {
    client: Client,
    base_url: String,
}

impl AgentClient {
    pub fn new(base_url: impl Into<String>) -> Self {
        let client = Client::builder()
            .timeout(Duration::from_secs(30))
            .build()
            .unwrap_or_default();

        Self {
            client,
            base_url: base_url.into(),
        }
    }

    pub async fn check_health(&self) -> Result<bool> {
        let url = format!("{}/health", self.base_url.trim_end_matches('/'));

        match self.client.get(&url).send().await {
            Ok(response) => Ok(response.status().is_success()),
            Err(_) => Ok(false),
        }
    }

    pub async fn get_status(&self) -> Result<AgentStatus> {
        let url = format!("{}/api/status", self.base_url.trim_end_matches('/'));

        let response = self
            .client
            .get(&url)
            .send()
            .await
            .map_err(|e| HermesError::Http(e.to_string()))?;

        if !response.status().is_success() {
            return Err(HermesError::Http(format!(
                "Agent returned status: {}",
                response.status()
            )));
        }

        let status: AgentStatus = response
            .json()
            .await
            .map_err(|e| HermesError::Http(e.to_string()))?;

        Ok(status)
    }

    pub async fn send_message(&self, session_id: &str, content: &str) -> Result<Message> {
        let url = format!("{}/api/sessions/send", self.base_url.trim_end_matches('/'));

        let body = serde_json::json!({
            "session_id": session_id,
            "content": content,
        });

        let response = self
            .client
            .post(&url)
            .json(&body)
            .send()
            .await
            .map_err(|e| HermesError::Http(e.to_string()))?;

        if !response.status().is_success() {
            return Err(HermesError::Http(format!(
                "Agent returned status: {}",
                response.status()
            )));
        }

        let message: Message = response
            .json()
            .await
            .map_err(|e| HermesError::Http(e.to_string()))?;

        Ok(message)
    }

    pub async fn get_sessions(&self) -> Result<Vec<Session>> {
        let url = format!("{}/api/sessions", self.base_url.trim_end_matches('/'));

        let response = self
            .client
            .get(&url)
            .send()
            .await
            .map_err(|e| HermesError::Http(e.to_string()))?;

        if !response.status().is_success() {
            return Err(HermesError::Http(format!(
                "Agent returned status: {}",
                response.status()
            )));
        }

        let sessions: Vec<Session> = response
            .json()
            .await
            .map_err(|e| HermesError::Http(e.to_string()))?;

        Ok(sessions)
    }

    pub async fn create_session(&self, name: &str) -> Result<Session> {
        let url = format!("{}/api/sessions", self.base_url.trim_end_matches('/'));

        let body = serde_json::json!({
            "name": name,
        });

        let response = self
            .client
            .post(&url)
            .json(&body)
            .send()
            .await
            .map_err(|e| HermesError::Http(e.to_string()))?;

        if !response.status().is_success() {
            return Err(HermesError::Http(format!(
                "Agent returned status: {}",
                response.status()
            )));
        }

        let session: Session = response
            .json()
            .await
            .map_err(|e| HermesError::Http(e.to_string()))?;

        Ok(session)
    }
}
