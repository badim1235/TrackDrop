import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

function renderApp(initialEntry = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TrackDrop app shell', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('shows the home discovery sections and login action', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    renderApp()

    expect(screen.getByRole('heading', { name: /오늘 발견한 음악/ })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '오늘의 추천' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument()
  })

  it('renders trending and recent tracks on the home feed', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === '/api/v1/home') {
        const track = (id: string, title: string, voteCount: number, createdAt: string) => ({
          id,
          title,
          artistName: 'TrackDrop Artist',
          albumName: 'Discovery Album',
          albumCoverUrl: 'https://example.com/cover.jpg',
          releaseYear: 2026,
          explicit: false,
          primaryGenre: { id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 },
          recommendation: {
            id: `30000000-0000-0000-0000-${id.slice(-12)}`,
            comment: '홈에서 발견한 곡이에요.',
            commentAvailable: true,
            recommenderNickname: '새벽리듬4881',
            createdAt,
          },
          todayVoteCount: voteCount,
          viewer: null,
          preview: {
            available: true,
            provider: 'APPLE_MUSIC',
            kind: 'OFFICIAL_30_SECOND_CLIP',
            startPosition: 'PROVIDER_SELECTED',
            url: `https://example.com/${id}.m4a`,
          },
          externalLinks: [{ provider: 'APPLE_MUSIC', url: `https://music.apple.com/kr/song/${id}` }],
        })
        return Response.json({
          asOf: '2026-09-01T06:00:00Z',
          quota: null,
          trending: {
            title: '오늘의 추천',
            items: [track('20000000-0000-0000-0000-000000000001', '인기곡', 4, '2026-09-01T05:00:00Z')],
            viewAllPath: '/chart',
          },
          recent: {
            title: '최근 등록된 곡',
            items: [track('20000000-0000-0000-0000-000000000002', '최신곡', 1, '2026-09-01T05:30:00Z')],
            viewAllPath: '/recent',
          },
        })
      }
      return new Response('', { status: 404 })
    })
    renderApp()

    expect(await screen.findByText('인기곡')).toBeInTheDocument()
    expect(screen.getByText('최신곡')).toBeInTheDocument()
    expect(screen.getByText('오늘 4표')).toBeInTheDocument()
    expect(screen.getByLabelText('인기곡 30초 미리듣기')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /전체 보기/ })).toHaveAttribute('href', '/recent')
  })

  it('renders the recent tracks page', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === '/api/v1/tracks/recent') {
        return Response.json({
          asOf: '2026-09-01T06:00:00Z',
          items: [{
            id: '20000000-0000-0000-0000-000000000003',
            title: '새로 온 노래',
            artistName: 'Recent Artist',
            albumName: 'Recent Album',
            albumCoverUrl: null,
            releaseYear: 2026,
            explicit: false,
            primaryGenre: { id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 },
            recommendation: {
              id: '30000000-0000-0000-0000-000000000003',
              comment: '새로운 발견이에요.',
              commentAvailable: true,
              recommenderNickname: '푸른멜로디1934',
              createdAt: '2026-09-01T05:30:00Z',
            },
            todayVoteCount: 1,
            viewer: null,
            preview: {
              available: false,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: null,
            },
            externalLinks: [],
          }],
          page: { size: 20, hasMore: false, nextCursor: null },
          quota: null,
        })
      }
      return new Response('', { status: 404 })
    })
    renderApp('/recent')

    expect(await screen.findByText('새로 온 노래')).toBeInTheDocument()
    expect(screen.getByText('Recent Artist')).toBeInTheDocument()
    expect(screen.getByText(/9월 1일 14:30/)).toBeInTheDocument()
  })

  it('moves from home to the chart', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    const user = userEvent.setup()
    renderApp()

    await user.click(screen.getAllByRole('link', { name: /차트/ })[0])
    expect(screen.getByRole('heading', { name: '오늘의 차트' })).toBeInTheDocument()
  })

  it('renders registered tracks from the live daily chart', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === '/api/v1/genres') {
        return Response.json({
          items: [{ id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 }],
        })
      }
      if (url === '/api/v1/charts/daily?genre=all') {
        return Response.json({
          date: '2026-09-01',
          status: 'LIVE',
          scope: { type: 'ALL', genre: null },
          asOf: '2026-09-01T05:00:00Z',
          items: [{
            rank: 1,
            voteCount: 1,
            hasVotedToday: false,
            track: {
              id: '20000000-0000-0000-0000-000000000001',
              title: '0+0',
              artistName: '한로로',
              albumName: '이상비행',
              albumCoverUrl: 'https://example.com/cover.jpg',
              releaseYear: 2025,
              explicit: false,
              primaryGenre: { id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 },
              comment: '오늘 계속 듣고 싶은 곡이에요.',
              recommenderNickname: '새벽리듬4881',
              preview: { available: true, provider: 'APPLE_MUSIC', url: 'https://example.com/preview.m4a' },
              externalUrl: 'https://music.apple.com/kr/song/0-0',
            },
          }],
          page: { size: 20, hasMore: false, nextCursor: null },
          quota: null,
          actions: { canVote: true },
        })
      }
      return new Response('', { status: 404 })
    })
    renderApp('/chart')

    expect(await screen.findByText('0+0')).toBeInTheDocument()
    expect(screen.getByText('한로로')).toBeInTheDocument()
    expect(screen.getByText('1표')).toBeInTheDocument()
    expect(screen.getByText(/오늘 계속 듣고 싶은 곡이에요/)).toBeInTheDocument()
    expect(screen.getByLabelText('0+0 30초 미리듣기')).toHaveAttribute(
      'src',
      'https://example.com/preview.m4a',
    )
  })

  it('shows the login form with a persistent-login choice', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    renderApp('/login')

    expect(screen.getByRole('heading', { name: '다시 음악을 발견해요.' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: '로그인 상태 유지' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '비밀번호 찾기' })).toHaveAttribute('href', '/recover/password')
    expect(screen.getByRole('link', { name: '익명 계정 만들기' })).toBeInTheDocument()
  })

  it('opens the account recovery entry pages', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    renderApp('/recover/password')

    expect(screen.getByRole('heading', { name: '비밀번호를 다시 설정해요.' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '로그인으로 돌아가기' })).toHaveAttribute('href', '/login')
  })

  it('validates the confirmed signup policy before submission', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    const user = userEvent.setup()
    renderApp('/join')

    await user.type(screen.getByLabelText('이메일'), 'invalid')
    await user.type(screen.getByLabelText('비밀번호', { selector: 'input' }), 'lettersOnly')
    await user.type(screen.getByLabelText('비밀번호 확인', { selector: 'input' }), 'different1')
    await user.click(screen.getByRole('button', { name: '익명 계정 만들기' }))

    expect(screen.getByText('8~16자의 영문자와 숫자를 포함하고 공백 없이 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument()
    expect(screen.getByText('올바른 이메일 형식이 아닙니다.')).toBeInTheDocument()
  })

  it('clears invalid email guidance when the format becomes valid', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    const user = userEvent.setup()
    renderApp('/join')

    const emailInput = screen.getByLabelText('이메일')
    await user.type(emailInput, 'invalid')
    expect(screen.getByText('올바른 이메일 형식이 아닙니다.')).toBeInTheDocument()

    await user.clear(emailInput)
    await user.type(emailInput, 'listener@example.com')
    expect(screen.queryByText('올바른 이메일 형식이 아닙니다.')).not.toBeInTheDocument()
  })

  it('shows an existing email error below the sign-up field', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      if (url === '/api/v1/auth/sign-up') {
        return Response.json({
          error: {
            code: 'EMAIL_TAKEN',
            message: '이미 존재하는 이메일입니다.',
            details: {},
          },
        }, { status: 409 })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/join')

    await user.type(screen.getByLabelText('이메일'), 'listener@example.com')
    await user.type(screen.getByLabelText('비밀번호', { selector: 'input' }), 'chatgpt5555')
    await user.type(screen.getByLabelText('비밀번호 확인', { selector: 'input' }), 'chatgpt5555')
    await user.click(screen.getByRole('button', { name: '익명 계정 만들기' }))

    expect(await screen.findByText('이미 존재하는 이메일입니다.')).toBeInTheDocument()
  })

  it('shows an invalid password message while typing', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    const user = userEvent.setup()
    renderApp('/join')

    await user.type(screen.getByLabelText('비밀번호', { selector: 'input' }), 'letters only')

    expect(screen.getByText('8~16자의 영문자와 숫자를 포함하고 공백 없이 입력해 주세요.')).toBeInTheDocument()
  })

  it('exposes the password guidelines on sign-up', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    renderApp('/join')

    expect(screen.getByLabelText('비밀번호 입력 규칙')).toBeInTheDocument()
    expect(screen.getByRole('tooltip')).toHaveTextContent('영문자(대·소문자 허용)와 숫자를 각각 1개 이상 포함한 8~16자')
  })

  it('searches the KR catalog and marks explicit tracks', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'music_user@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-09-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-01',
            limit: 4,
            used: 0,
            remaining: 4,
            resetAt: '2026-09-01T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/music/search?query=Radiohead') {
        return Response.json({
          provider: 'APPLE_MUSIC',
          storefront: 'KR',
          attribution: 'Music preview provided courtesy of iTunes',
          items: [{
            provider: 'APPLE_MUSIC',
            externalTrackId: '1234',
            title: 'Creep',
            artistName: 'Radiohead',
            albumName: 'Pablo Honey',
            albumCoverUrl: 'https://example.com/cover.jpg',
            releaseYear: 1993,
            isrc: null,
            explicit: true,
            primaryGenreName: 'Alternative',
            preview: {
              available: true,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: 'https://example.com/preview.m4a',
            },
            externalUrl: 'https://music.apple.com/kr/album/creep/1234',
            existingTrack: { registered: false, trackId: null, hasVotedToday: false },
          }],
        })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/recommend')

    const input = await screen.findByRole('textbox', { name: '곡 또는 아티스트 검색' })
    await waitFor(() => expect(input).toBeEnabled())
    await user.type(input, 'Radiohead')
    await user.click(screen.getByRole('button', { name: '검색' }))

    expect(await screen.findByRole('heading', { name: '검색 결과 1곡' })).toBeInTheDocument()
    expect(screen.getByText('Creep')).toBeInTheDocument()
    expect(screen.getByText('Explicit')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Apple Music/ })).toHaveAttribute(
      'href',
      'https://music.apple.com/kr/album/creep/1234',
    )
  })

  it('shows search results ten at a time', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'music_user@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-09-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-01', limit: 4, used: 0, remaining: 4,
            resetAt: '2026-09-01T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/music/search?query=Radiohead') {
        return Response.json({
          provider: 'APPLE_MUSIC',
          storefront: 'KR',
          attribution: 'Music preview provided courtesy of iTunes',
          items: Array.from({ length: 20 }, (_, index) => ({
            provider: 'APPLE_MUSIC',
            externalTrackId: String(index + 1),
            title: `Search Track ${index + 1}`,
            artistName: 'Radiohead',
            albumName: 'Search Album',
            albumCoverUrl: 'https://example.com/cover.jpg',
            releaseYear: 2026,
            isrc: null,
            explicit: false,
            primaryGenreName: 'Alternative',
            preview: {
              available: true,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: `https://example.com/preview-${index + 1}.m4a`,
            },
            externalUrl: `https://music.apple.com/kr/song/${index + 1}`,
            existingTrack: { registered: false, trackId: null, hasVotedToday: false },
          })),
        })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/recommend')

    const input = await screen.findByRole('textbox', { name: '곡 또는 아티스트 검색' })
    await waitFor(() => expect(input).toBeEnabled())
    await user.type(input, 'Radiohead')
    await user.click(screen.getByRole('button', { name: '검색' }))

    expect(await screen.findByText('Search Track 10')).toBeInTheDocument()
    expect(screen.queryByText('Search Track 11')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '더보기 (10곡)' }))

    expect(await screen.findByText('Search Track 20')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /더보기/ })).not.toBeInTheDocument()
  })

  it('votes for an existing track and updates the daily quota', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'music_user@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-09-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-01', limit: 4, used: 0, remaining: 4,
            resetAt: '2026-09-01T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/music/search?query=Existing') {
        return Response.json({
          provider: 'APPLE_MUSIC',
          storefront: 'KR',
          attribution: 'Music preview provided courtesy of iTunes',
          items: [{
            provider: 'APPLE_MUSIC',
            externalTrackId: '1234',
            title: 'Existing Track',
            artistName: 'TrackDrop Artist',
            albumName: 'Existing Album',
            albumCoverUrl: 'https://example.com/cover.jpg',
            releaseYear: 2026,
            isrc: null,
            explicit: false,
            primaryGenreName: 'Rock',
            preview: {
              available: true,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: 'https://example.com/preview.m4a',
            },
            externalUrl: 'https://music.apple.com/kr/song/1234',
            existingTrack: {
              registered: true,
              trackId: '20000000-0000-0000-0000-000000000001',
              hasVotedToday: false,
            },
          }],
        })
      }
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      if (url === '/api/v1/tracks/20000000-0000-0000-0000-000000000001/votes' && init?.method === 'POST') {
        return Response.json({
          vote: {
            trackId: '20000000-0000-0000-0000-000000000001',
            votedOn: '2026-09-01',
            createdAt: '2026-09-01T04:00:00Z',
          },
          todayVoteCount: 2,
          quota: {
            date: '2026-09-01', limit: 4, used: 1, remaining: 3,
            resetAt: '2026-09-01T15:00:00Z',
          },
        }, { status: 201 })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/recommend')

    const input = await screen.findByRole('textbox', { name: '곡 또는 아티스트 검색' })
    await waitFor(() => expect(input).toBeEnabled())
    await user.type(input, 'Existing')
    await user.click(screen.getByRole('button', { name: '검색' }))
    await user.click(await screen.findByRole('button', { name: '추천' }))

    expect(await screen.findByRole('button', { name: '추천 완료' })).toBeDisabled()
    expect(screen.getByText('오늘의 추천 1/4')).toBeInTheDocument()
  })

  it('registers a selected track with an Apple genre and one-line review', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'music_user@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-09-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-01', limit: 4, used: 0, remaining: 4,
            resetAt: '2026-09-01T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/genres') {
        return Response.json({
          items: [
            { id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 },
            { id: '10000000-0000-0000-0000-000000000024', code: 'other', displayName: 'Other', sortOrder: 240 },
          ],
        })
      }
      if (url === '/api/v1/music/search?query=0%2B0') {
        return Response.json({
          provider: 'APPLE_MUSIC',
          storefront: 'KR',
          attribution: 'Music preview provided courtesy of iTunes',
          items: [{
            provider: 'APPLE_MUSIC',
            externalTrackId: '1828393595',
            title: '0+0',
            artistName: '한로로',
            albumName: '이상비행',
            albumCoverUrl: 'https://example.com/cover.jpg',
            releaseYear: 2025,
            isrc: null,
            explicit: false,
            primaryGenreName: 'Rock',
            preview: {
              available: true,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: 'https://example.com/preview.m4a',
            },
            externalUrl: 'https://music.apple.com/kr/song/1828393595',
            existingTrack: { registered: false, trackId: null, hasVotedToday: false },
          }],
        })
      }
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      if (url === '/api/v1/recommendations' && init?.method === 'POST') {
        return Response.json({
          track: {
            id: '20000000-0000-0000-0000-000000000001',
            title: '0+0', artistName: '한로로', albumName: '이상비행',
            albumCoverUrl: 'https://example.com/cover.jpg', releaseYear: 2025,
            explicit: false, primaryGenreName: 'Rock',
            preview: { available: true, provider: 'APPLE_MUSIC', url: 'https://example.com/preview.m4a' },
            externalUrl: 'https://music.apple.com/kr/song/1828393595',
          },
          recommendation: {
            id: '30000000-0000-0000-0000-000000000001',
            primaryGenre: {
              id: '10000000-0000-0000-0000-000000000020',
              code: 'rock', displayName: 'Rock', sortOrder: 200,
            },
            comment: '잔잔하게 번지는 기타가 좋아요.',
            createdAt: '2026-09-01T03:00:00Z',
          },
          vote: { created: true, votedOn: '2026-09-01' },
          quota: {
            date: '2026-09-01', limit: 4, used: 1, remaining: 3,
            resetAt: '2026-09-01T15:00:00Z',
          },
        }, { status: 201 })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/recommend')

    const searchInput = await screen.findByRole('textbox', { name: '곡 또는 아티스트 검색' })
    await waitFor(() => expect(searchInput).toBeEnabled())
    await user.type(searchInput, '0+0')
    await user.click(screen.getByRole('button', { name: '검색' }))
    await user.click(await screen.findByRole('button', { name: '선택' }))

    const genreSelect = screen.getByRole('combobox', { name: /대표 장르/ })
    await waitFor(() => expect(genreSelect).toHaveValue('10000000-0000-0000-0000-000000000020'))
    await user.type(screen.getByRole('textbox', { name: '한줄평' }), '잔잔하게 번지는 기타가 좋아요.')
    await user.click(screen.getByRole('button', { name: '추천 등록' }))

    expect(await screen.findByRole('heading', { name: '추천을 등록했어요.' })).toBeInTheDocument()
    expect(screen.getByText('오늘의 추천 1/4 · 3회 남음')).toBeInTheDocument()
  })
})
