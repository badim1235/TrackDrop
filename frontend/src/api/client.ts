import type { components } from './generated'

export type SystemHealth = components['schemas']['SystemHealthResponse']

export async function fetchSystemHealth(): Promise<SystemHealth> {
  const response = await fetch('/api/v1/system/health', {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })

  if (!response.ok) {
    throw new Error(`Health check failed with ${response.status}`)
  }

  return response.json() as Promise<SystemHealth>
}
