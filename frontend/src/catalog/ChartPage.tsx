import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CalendarDays, Check, Disc3, ExternalLink, LoaderCircle, ThumbsUp } from 'lucide-react'
import { useState } from 'react'
import { NavLink, useSearchParams } from 'react-router'
import styles from '../App.module.css'
import {
  ApiError,
  createVote,
  fetchDailyChart,
  fetchGenres,
  type AccountResponse,
  type DailyChartResponse,
} from '../api/client'
import { accountQueryKey, useAccount } from '../auth/account'

function ChartArtwork({ track }: { track: DailyChartResponse['items'][number]['track'] }) {
  const [failed, setFailed] = useState(false)
  if (!track.albumCoverUrl || failed) {
    return <div className={styles.chartArtworkFallback}><Disc3 aria-hidden="true" size={22} /></div>
  }
  return (
    <img
      className={styles.chartArtwork}
      src={track.albumCoverUrl}
      alt=""
      loading="lazy"
      onError={() => setFailed(true)}
    />
  )
}

function chartErrorMessage(error: unknown) {
  return error instanceof ApiError
    ? error.message
    : '차트를 불러오지 못했습니다. 다시 시도해 주세요.'
}

function formatAsOf(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Seoul',
  }).format(new Date(value))
}

function koreanDateValue(date = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: 'Asia/Seoul',
  }).formatToParts(date)
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${value.year}-${value.month}-${value.day}`
}

function formatChartDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return `${year}년 ${month}월 ${day}일`
}

export function ChartPage() {
  const queryClient = useQueryClient()
  const { data: account } = useAccount()
  const [searchParams, setSearchParams] = useSearchParams()
  const today = koreanDateValue()
  const requestedDate = searchParams.get('date') ?? undefined
  const selectedDate = requestedDate ?? today
  const selectedGenre = searchParams.get('genre') || 'all'
  const [voteError, setVoteError] = useState<string | null>(null)
  const genres = useQuery({
    queryKey: ['genres'],
    queryFn: fetchGenres,
    staleTime: 30 * 60 * 1000,
  })
  const chart = useInfiniteQuery({
    queryKey: ['daily-chart', selectedDate, selectedGenre],
    queryFn: ({ pageParam }) => fetchDailyChart(selectedGenre, requestedDate, pageParam ?? undefined),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.page.nextCursor ?? undefined,
    staleTime: 15_000,
  })
  const vote = useMutation({
    mutationFn: createVote,
    onSuccess: (data) => {
      setVoteError(null)
      queryClient.setQueryData<AccountResponse>(accountQueryKey, (current) => current
        ? { ...current, quota: data.quota }
        : current)
      void queryClient.invalidateQueries({ queryKey: ['daily-chart'] })
      void queryClient.invalidateQueries({ queryKey: ['home'] })
      void queryClient.invalidateQueries({ queryKey: ['recent-tracks'] })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.details.quota) {
        const quota = error.details.quota as AccountResponse['quota']
        queryClient.setQueryData<AccountResponse>(accountQueryKey, (current) => current
          ? { ...current, quota }
          : current)
      }
      if (error instanceof ApiError && error.code === 'ALREADY_VOTED') {
        setVoteError(null)
        void queryClient.invalidateQueries({ queryKey: ['daily-chart'] })
        void queryClient.invalidateQueries({ queryKey: ['home'] })
        void queryClient.invalidateQueries({ queryKey: ['recent-tracks'] })
        return
      }
      setVoteError(chartErrorMessage(error))
    },
  })
  const items = chart.data?.pages.flatMap((page) => page.items) ?? []
  const firstPage = chart.data?.pages[0]
  const quotaEmpty = account?.quota.remaining === 0
  const isFinal = firstPage?.status === 'FINAL' || selectedDate < today
  const canVote = firstPage?.actions.canVote ?? !isFinal

  function selectDate(date: string) {
    const next = new URLSearchParams(searchParams)
    if (!date || date === today) next.delete('date')
    else next.set('date', date)
    setVoteError(null)
    setSearchParams(next)
  }

  function selectGenre(genre: string) {
    const next = new URLSearchParams(searchParams)
    if (genre === 'all') next.delete('genre')
    else next.set('genre', genre)
    setVoteError(null)
    setSearchParams(next)
  }

  return (
    <div className={styles.pageStack}>
      <header className={styles.pageHeader}>
        <p className={styles.eyebrow}>DAILY CHART</p>
        <div className={styles.chartTitleLine}>
          <h1>{isFinal ? `${formatChartDate(selectedDate)} 차트` : '오늘의 차트'}</h1>
          {!isFinal ? <p>{firstPage ? `LIVE · ${formatAsOf(firstPage.asOf)} 기준` : '실시간 추천 순위'}</p> : null}
        </div>
      </header>
      <div className={styles.chartFilters}>
        <label className={`${styles.chartDatePicker} ${isFinal ? styles.activeChartDatePicker : ''}`}>
          <CalendarDays aria-hidden="true" size={16} />
          <span>과거 차트 보기</span>
          <input
            aria-label="차트 날짜"
            type="date"
            max={today}
            value={selectedDate}
            onChange={(event) => selectDate(event.target.value)}
          />
        </label>
        {isFinal ? (
          <button className={styles.todayChartButton} type="button" onClick={() => selectDate(today)}>
            <ArrowLeft aria-hidden="true" size={15} /> 오늘 차트
          </button>
        ) : null}
      </div>
      <div className={styles.genreTabs} role="tablist" aria-label="장르">
        <button
          className={selectedGenre === 'all' ? styles.activeGenre : undefined}
          role="tab"
          aria-selected={selectedGenre === 'all'}
          onClick={() => selectGenre('all')}
        >
          전체
        </button>
        {genres.data?.items.map((genre) => (
          <button
            className={selectedGenre === genre.code ? styles.activeGenre : undefined}
            role="tab"
            aria-selected={selectedGenre === genre.code}
            key={genre.id}
            onClick={() => selectGenre(genre.code)}
          >
            {genre.displayName}
          </button>
        ))}
      </div>
      <section className={styles.chartPanel} aria-label={isFinal ? '과거 일일 순위' : '오늘의 순위'} aria-busy={chart.isPending}>
        <div className={styles.chartColumns} aria-hidden="true">
          <span>순위</span><span>곡</span><span>추천</span>
        </div>
        {chart.isPending ? (
          <div className={styles.chartMessage}><LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={22} /><span>차트를 불러오는 중...</span></div>
        ) : chart.isError ? (
          <div className={styles.chartMessage} role="alert">
            <strong>차트를 불러오지 못했습니다.</strong>
            <span>{chartErrorMessage(chart.error)}</span>
            <button type="button" onClick={() => chart.refetch()}>다시 시도</button>
          </div>
        ) : items.length === 0 ? (
          <div className={styles.chartMessage}>
            <Disc3 aria-hidden="true" size={28} strokeWidth={1.5} />
            <span>{isFinal ? '이 날짜에는 등록된 곡이 없어요.' : '이 장르의 첫 추천을 기다리고 있어요.'}</span>
          </div>
        ) : (
          <ol className={styles.chartList}>
            {items.map((item) => {
              const isVoting = vote.isPending && vote.variables === item.track.id
              return (
                <li className={styles.chartRow} key={item.track.id}>
                  <strong className={styles.chartRank}>{item.rank}</strong>
                  <ChartArtwork track={item.track} />
                  <div className={styles.chartTrackInfo}>
                    <div className={styles.trackTitleLine}>
                      <strong><NavLink className={styles.trackDetailLink} to={`/tracks/${item.track.id}`}>{item.track.title}</NavLink></strong>
                      {item.track.explicit ? <span className={styles.explicitBadge}>Explicit</span> : null}
                    </div>
                    <span>{item.track.artistName}</span>
                    <small>{item.track.primaryGenre.displayName}</small>
                    {item.track.comment ? (
                      <p>“{item.track.comment}” <span>· {item.track.recommenderNickname}</span></p>
                    ) : null}
                    {item.track.preview.url ? (
                      <audio
                        className={styles.chartPreview}
                        controls
                        controlsList="nodownload noplaybackrate noremoteplayback"
                        preload="none"
                        src={item.track.preview.url}
                        aria-label={`${item.track.title} 30초 미리듣기`}
                      />
                    ) : null}
                    {item.track.externalUrl ? (
                      <a href={item.track.externalUrl} target="_blank" rel="noreferrer">
                        Apple Music <ExternalLink aria-hidden="true" size={12} />
                      </a>
                    ) : null}
                  </div>
                  <div className={styles.chartVoteCell}>
                    <strong>{item.voteCount}표</strong>
                    {account && canVote ? (
                      <button
                        className={item.hasVotedToday ? styles.votedButton : undefined}
                        type="button"
                        disabled={item.hasVotedToday || quotaEmpty || vote.isPending}
                        onClick={() => vote.mutate(item.track.id)}
                      >
                        {isVoting ? <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={14} />
                          : item.hasVotedToday ? <Check aria-hidden="true" size={14} />
                            : <ThumbsUp aria-hidden="true" size={14} />}
                        {item.hasVotedToday ? '추천 완료' : quotaEmpty ? '추천권 없음' : '추천'}
                      </button>
                    ) : null}
                  </div>
                </li>
              )
            })}
          </ol>
        )}
        {voteError ? <p className={styles.chartVoteError} role="alert">{voteError}</p> : null}
        {chart.hasNextPage ? (
          <button
            className={styles.loadMoreButton}
            type="button"
            disabled={chart.isFetchingNextPage}
            onClick={() => chart.fetchNextPage()}
          >
            {chart.isFetchingNextPage ? '불러오는 중...' : `더보기`}
          </button>
        ) : null}
      </section>
    </div>
  )
}
