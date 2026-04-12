import React from 'react'
import ReactDOM from 'react-dom/client'
import 'highlight.js/styles/github-dark.css'
import App from './App'
import './styles/index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)

// Remove splash screen
window.addEventListener('load', () => {
  const splash = document.getElementById('splash-screen')
  if (splash) {
    setTimeout(() => {
      splash.style.opacity = '0'
      setTimeout(() => splash.remove(), 500)
    }, 500)
  }
})
