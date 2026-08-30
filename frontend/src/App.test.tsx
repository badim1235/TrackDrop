import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TrackDrop app shell', () => {
  afterEach(() => vi.restoreAllMocks())

  it('shows the home discovery sections and login action', () => {
    renderApp()

    expect(screen.getByRole('heading', { name: /오늘 발견한 음악/ })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '오늘의 추천' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
  })

  it('moves from home to the chart', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'))
    const user = userEvent.setup()
    renderApp()

    await user.click(screen.getAllByRole('link', { name: /차트/ })[0])
    expect(screen.getByRole('heading', { name: '오늘의 차트' })).toBeInTheDocument()
  })
})
