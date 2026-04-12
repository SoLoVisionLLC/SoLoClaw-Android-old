// Standalone API server for web deployment
use std::sync::Arc;
use hermes_mission_control::store::AppStore;
use tokio::sync::RwLock;

#[tokio::main]
async fn main() {
    let store = Arc::new(RwLock::new(AppStore::load_or_default()));
    
    let port: u16 = std::env::var("MISSION_CONTROL_API_PORT")
        .ok()
        .and_then(|v| v.parse::<u16>().ok())
        .unwrap_or(8080);
    
    println!("Starting Hermes Mission Control API server on port {}", port);
    
    hermes_mission_control::api::start_api_server(store, port).await;
}
