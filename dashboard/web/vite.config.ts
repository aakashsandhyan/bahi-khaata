import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev server runs on 5173, which is the origin the backend's CORS config allows. Change
// both together or the browser will block every call.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
})
