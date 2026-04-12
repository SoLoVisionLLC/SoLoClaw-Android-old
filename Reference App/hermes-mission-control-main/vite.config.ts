import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src-web/src'),
    },
  },
  server: {
    port: 1420,
    strictPort: true,
    host: true,
    allowedHosts: [
      'localhost',
      'hermes.solobot.cloud',
      'missioncontrol.solobot.cloud',
      '.solobot.cloud',
    ],
    hmr: {
      protocol: 'ws',
      host: 'localhost',
      port: 1420,
    },
    // Proxy API calls to the Rust HTTP server during development
    proxy: {
      '/api': {
        target: 'http://localhost:1421',
        changeOrigin: true,
        rewrite: (path) => path,
      },
      '/health': {
        target: 'http://localhost:1421',
        changeOrigin: true,
        rewrite: (path) => path,
      },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: true,
  },
})
