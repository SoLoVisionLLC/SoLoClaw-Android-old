use thiserror::Error;

#[derive(Error, Debug)]
pub enum HermesError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),

    #[error("JSON error: {0}")]
    Json(#[from] serde_json::Error),

    #[error("HTTP error: {0}")]
    Http(String),

    #[error("Agent not found: {0}")]
    AgentNotFound(String),

    #[error("Session not found: {0}")]
    SessionNotFound(String),

    #[error("Task not found: {0}")]
    TaskNotFound(String),

    #[error("Note not found: {0}")]
    NoteNotFound(String),

    #[error("Configuration error: {0}")]
    Config(String),

    #[error("Store error: {0}")]
    Store(String),

    #[error("Unknown error: {0}")]
    Unknown(String),
}

impl serde::Serialize for HermesError {
    fn serialize<S>(&self, serializer: S) -> std::result::Result<S::Ok, S::Error>
    where
        S: serde::ser::Serializer,
    {
        serializer.serialize_str(self.to_string().as_ref())
    }
}

pub type Result<T> = std::result::Result<T, HermesError>;
