import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'TRACKDROP_')

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': env.TRACKDROP_API_PROXY_TARGET || 'http://localhost:8080',
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html'],
      },
    },
  }
})
