# Hermes Mission Control

**Multi-platform agent dashboard built with Tauri v2 + React + Rust**

## Features

- 🤖 **Agent Status Panel** - Real-time monitoring of all connected agents
- 📋 **Kanban Board** - Drag-drop task management (Todo/In Progress/Done)
- 📝 **Notes** - Persistent notes with "seen by agent" indicators
- 📊 **Activity Log** - Timestamped history of all agent actions
- 💬 **Chat Interface** - Direct messaging with agents via sessions
- 🎨 **4 Theme System** - Dark/light variants of Hermes themes
- 📱 **Mobile Responsive** - Works on desktop, web, iOS, and Android

## Quick Start

### Prerequisites
- [Rust](https://rustup.rs/) 1.70+
- [Node.js](https://nodejs.org/) 18+

### Development

```bash
# Install dependencies
npm install

# Run development server (web + Tauri desktop)
npm run dev

# Build for production
npm run build
```

### Mobile Development

```bash
# iOS (requires macOS + Xcode)
npm run tauri ios dev

# Android (requires Android Studio)
npm run tauri android dev
```

### Web Deployment

The app can also be deployed as a standalone web app:

```bash
# Build web-only version
npm run build:web

# Preview production build
npm run preview
```

## Project Structure

```
hermes-mission-control/
├── src-tauri/           # Rust backend
│   ├── src/
│   │   ├── lib.rs      # Main Tauri entry
│   │   ├── commands.rs # IPC command handlers
│   │   ├── models.rs   # Data types
│   │   ├── store.rs    # In-memory state
│   │   ├── config.rs   # App configuration
│   │   ├── agent.rs    # Agent HTTP client
│   │   └── error.rs    # Error types
│   └── Cargo.toml
├── src-web/            # React frontend
│   └── src/
│       ├── components/ # UI components
│       ├── screens/    # Page components
│       ├── hooks/      # Custom hooks
│       ├── lib/        # Utilities
│       └── types/      # TypeScript types
├── package.json
├── vite.config.ts
└── tailwind.config.js
```

## Architecture

- **Frontend**: React 18 + TypeScript + Tailwind CSS + Zustand state
- **Backend**: Rust + Tauri v2 + Tokio async runtime
- **Communication**: Tauri IPC commands for desktop, HTTP API for web
- **State**: In-memory Rust store with sample data, ready for Hermes integration

## Technical Docs

- [`docs/PHASE1_TECHNICAL_DOCS.md`](docs/PHASE1_TECHNICAL_DOCS.md) — Phase 1 architecture backfill covering shared types, API routing, Zustand orchestration, Rust models/store, and native-shell runtime behavior
- [`ANDROID_WORKFLOW.md`](ANDROID_WORKFLOW.md) — Android packaging, remote API routing, and install/debug workflow

## Connecting to Hermes

1. Start your Hermes agent with WebAPI enabled:
   ```bash
   hermes webapi
   ```

2. In Mission Control Settings, enter your Hermes URL (default: `http://127.0.0.1:8642`)

3. Click "Connect" - the dashboard will sync with your agent

## License

MIT
