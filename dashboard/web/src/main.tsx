import React from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
// Order matters: the shell's Tailwind layer first, the app's own stylesheet after it, so the
// existing screens' rules outrank Tailwind's preflight where they collide.
import './shell.css'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
