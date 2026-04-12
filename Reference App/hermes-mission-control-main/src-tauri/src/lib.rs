pub mod agent;
pub mod api;
pub mod commands;
pub mod config;
pub mod error;
pub mod models;
pub mod store;

use std::sync::Arc;
use store::AppStore;
use tokio::sync::RwLock;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let store = Arc::new(RwLock::new(AppStore::load_or_default()));

    tauri::Builder::default()
        .manage(store.clone())
        .plugin(tauri_plugin_http::init())
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_store::Builder::new().build())
        .plugin(tauri_plugin_os::init())
        .plugin(tauri_plugin_process::init())
        .invoke_handler(tauri::generate_handler![
            commands::get_agent_status,
            commands::send_message,
            commands::get_messages,
            commands::get_sessions,
            commands::get_recent_sessions,
            commands::create_session,
            commands::update_session_name,
            commands::get_tasks,
            commands::create_task,
            commands::update_task,
            commands::delete_task,
            commands::get_activity_log,
            commands::add_activity,
            commands::get_notes,
            commands::create_note,
            commands::update_note,
            commands::delete_note,
            commands::get_config,
            commands::update_config,
            commands::discover_hermes_instances,
            commands::connect_to_hermes,
            // Agent profile & discovery
            commands::discover_agents,
            commands::update_agent_profile,
            commands::set_agent_group,
            // Group management
            commands::get_groups,
            commands::create_group,
            commands::update_group,
            commands::delete_group,
            commands::add_agent_to_group,
            commands::remove_agent_from_group,
            // Cron jobs
            commands::get_cron_jobs,
            commands::create_cron_job,
            commands::update_cron_job,
            commands::delete_cron_job,
            commands::run_cron_job_now,
            commands::get_cron_job_runs,
            commands::sync_cron_jobs_from_file,
        ])
        .setup(move |_app| {
            let store_for_thread = store.clone();
            let api_port: u16 = std::env::var("MISSION_CONTROL_API_PORT")
                .ok()
                .and_then(|v| v.parse::<u16>().ok())
                .unwrap_or(1421);

            std::thread::spawn(move || {
                let rt = tokio::runtime::Runtime::new()
                    .expect("failed to create tokio runtime for API server");
                rt.block_on(async {
                    api::start_api_server(store_for_thread, api_port).await;
                });
            });

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
