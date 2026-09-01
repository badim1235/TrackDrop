import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Check,
  ChevronRight,
  Clock3,
  Disc3,
  ExternalLink,
  LoaderCircle,
  Plus,
  ThumbsUp,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink } from 'react-router'
import styles from '../App.module.css'
import {
  ApiError,
  createVote,
  fetchHome,
  fetchRecentTracks,
  type AccountResponse,
  type HomeTrackCard,
} from '../api/client'
import { accountQueryKey, useAccount } from '../auth/account'

type DiscoveryTrackProps = {
  item: HomeTrackCard
  showRegisteredAt?: boolean
  authenticated: boolean
  quotaEmpty: boolean
  votePending: boolean
  votingTrackId?: string
  onVote: (trackId: string) => void
}

function feedErrorMessage(error: unknown) {
  return error instanceof ApiError
    ? error.message
    : '곡을 불러오지 못했습니다. 다시 시도해 주세요.'
}

function formatRegisteredAt(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Seoul',
  }).format(new Date(value))
}

function DiscoveryArtwork({ item }: { item: HomeTrackCard }) {
  const [failed, setFailed] = useState(false)
  if (!item.albumCoverUrl || failed) {
    return <div className={styles.homeArtworkFallback}><Disc3 aria-hidden="true" size={24} /></div>
  }
  return (
    <img
      className={styles.homeArtwork}
      src={item.albumCoverUrl}
      alt=""
      loading="lazy"
      onError={() => setFailed(true)}
    />
  )
}

function DiscoveryTrack({
  item,
  showRegisteredAt = false,
  authenticated,
  quotaEmpty,
  votePending,
  votingTrackId,
  onVote,
}: DiscoveryTrackProps) {
  const hasVoted = item.viewer?.hasVotedToday ?? false
  const externalLink = item.externalLinks[0]
  const isVoting = votePending && votingTrackId === item.id

  return (
    <li className={styles.homeTrackItem}>
      <DiscoveryArtwork item={item} />
      <div className={styles.homeTrackBody}>
        <div className={styles.trackTitleLine}>
          <strong>{item.title}</strong>
          {item.explicit ? <span className={styles.explicitBadge}>Explicit</span> : null}
        </div>
        <span className={styles.homeArtist}>{item.artistName}</span>
        <small>{item.primaryGenre.displayName}</small>
        {item.recommendation.commentAvailable && item.recommendation.comment ? (
          <p>“{item.recommendation.comment}” <span>· {item.recommendation.recommenderNickname}</span></p>
        ) : null}
        {showRegisteredAt ? (
          <time dateTime={item.recommendation.createdAt}>
            <Clock3 aria-hidden="true" size={12} /> {formatRegisteredAt(item.recommendation.createdAt)} 등록
          </time>
        ) : null}
        {item.preview.url ? (
          <audio
            className={styles.homePreview}
            controls
            controlsList="nodownload noplaybackrate noremoteplayback"
            preload="none"
            src={item.preview.url}
            aria-label={`${item.title} 30초 미리듣기`}
          />
        ) : null}
        <div className={styles.homeTrackActions}>
          <span><ThumbsUp aria-hidden="true" size={13} /> 오늘 {item.todayVoteCount}표</span>
          {externalLink ? (
            <a href={externalLink.url} target="_blank" rel="noreferrer">
              Apple Music <ExternalLink aria-hidden="true" size={12} />
            </a>
          ) : null}
          {authenticated ? (
            <button
              className={hasVoted ? styles.votedButton : undefined}
              type="button"
              disabled={hasVoted || quotaEmpty || votePending}
              onClick={() => onVote(item.id)}
            >
              {isVoting ? <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={14} />
                : hasVoted ? <Check aria-hidden="true" size={14} />
                  : <ThumbsUp aria-hidden="true" size={14} />}
              {hasVoted ? '추천 완료' : quotaEmpty ? '추천권 없음' : '추천'}
            </button>
          ) : null}
        </div>
      </div>
    </li>
  )
}

function useDiscoveryVote() {
  const queryClient = useQueryClient()
  const { data: account } = useAccount()
  const [voteError, setVoteError] = useState<string | null>(null)
  const vote = useMutation({
    mutationFn: createVote,
    onSuccess: (data) => {
      setVoteError(null)
      queryClient.setQueryData<AccountResponse>(accountQueryKey, (current) => current
        ? { ...current, quota: data.quota }
        : current)
      void queryClient.invalidateQueries({ queryKey: ['home'] })
      void queryClient.invalidateQueries({ queryKey: ['recent-tracks'] })
      void queryClient.invalidateQueries({ queryKey: ['daily-chart'] })
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
        void queryClient.invalidateQueries({ queryKey: ['home'] })
        void queryClient.invalidateQueries({ queryKey: ['recent-tracks'] })
        return
      }
      setVoteError(feedErrorMessage(error))
    },
  })
  return { account, vote, voteError }
}

function FeedMessage({
  state,
  message,
  onRetry,
}: {
  state: 'loading' | 'error' | 'empty'
  message: string
  onRetry?: () => void
}) {
  return (
    <div className={styles.feedMessage} role={state === 'error' ? 'alert' : undefined}>
      {state === 'loading'
        ? <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={22} />
        : <Disc3 aria-hidden="true" size={26} strokeWidth={1.5} />}
      <span>{message}</span>
      {onRetry ? <button type="button" onClick={onRetry}>다시 시도</button> : null}
    </div>
  )
}

export function HomePage() {
  const home = useQuery({
    queryKey: ['home'],
    queryFn: fetchHome,
    staleTime: 15_000,
  })
  const { account, vote, voteError } = useDiscoveryVote()
  const quotaEmpty = account?.quota.remaining === 0

  const renderSectionItems = (items: HomeTrackCard[], emptyMessage: string, showRegisteredAt = false) => {
    if (home.isPending) return <FeedMessage state="loading" message="곡을 불러오는 중..." />
    if (home.isError) {
      return <FeedMessage state="error" message={feedErrorMessage(home.error)} onRetry={() => home.refetch()} />
    }
    if (items.length === 0) return <FeedMessage state="empty" message={emptyMessage} />
    return (
      <ul className={styles.homeTrackList}>
        {items.map((item) => (
          <DiscoveryTrack
            item={item}
            key={item.id}
            showRegisteredAt={showRegisteredAt}
            authenticated={Boolean(account)}
            quotaEmpty={quotaEmpty}
            votePending={vote.isPending}
            votingTrackId={vote.variables}
            onVote={vote.mutate}
          />
        ))}
      </ul>
    )
  }

  return (
    <div className={styles.pageStack}>
      <section className={styles.intro}>
        <div>
          <p className={styles.eyebrow}>TODAY&apos;S DROPS</p>
          <h1>오늘 발견한 음악을<br />함께 차트에 <span className={styles.mobileBreak}>올려보세요.</span></h1>
        </div>
        <NavLink className={styles.primaryAction} to="/recommend">
          <Plus aria-hidden="true" size={19} />
          곡 추천하기
        </NavLink>
      </section>

      <section className={styles.contentSection} aria-labelledby="popular-heading" aria-busy={home.isPending}>
        <div className={styles.sectionHeader}>
          <div>
            <p className={styles.sectionLabel}>지금 가장 뜨거운</p>
            <h2 id="popular-heading">오늘의 추천</h2>
          </div>
          <NavLink className={styles.textLink} to={home.data?.trending.viewAllPath ?? '/chart'}>
            차트 보기 <ChevronRight aria-hidden="true" size={17} />
          </NavLink>
        </div>
        {renderSectionItems(home.data?.trending.items ?? [], '오늘의 첫 추천을 기다리고 있어요.')}
      </section>

      <section className={styles.contentSection} aria-labelledby="recent-heading" aria-busy={home.isPending}>
        <div className={styles.sectionHeader}>
          <div>
            <p className={styles.sectionLabel}>새롭게 도착한</p>
            <h2 id="recent-heading">최근 등록된 곡</h2>
          </div>
          <NavLink className={styles.textLink} to={home.data?.recent.viewAllPath ?? '/recent'}>
            전체 보기 <ChevronRight aria-hidden="true" size={17} />
          </NavLink>
        </div>
        {renderSectionItems(home.data?.recent.items ?? [], '아직 등록된 곡이 없어요.', true)}
      </section>
      {voteError ? <p className={styles.feedVoteError} role="alert">{voteError}</p> : null}
    </div>
  )
}

export function RecentPage() {
  const recent = useInfiniteQuery({
    queryKey: ['recent-tracks'],
    queryFn: ({ pageParam }) => fetchRecentTracks(pageParam ?? undefined),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.page.nextCursor ?? undefined,
    staleTime: 15_000,
  })
  const { account, vote, voteError } = useDiscoveryVote()
  const items = recent.data?.pages.flatMap((page) => page.items) ?? []
  const quotaEmpty = account?.quota.remaining === 0

  return (
    <div className={styles.pageStack}>
      <header className={styles.pageHeader}>
        <p className={styles.eyebrow}>RECENT DROPS</p>
        <h1>최근 등록된 곡</h1>
        <p>TrackDrop에 새롭게 소개된 음악</p>
      </header>
      <section className={styles.recentFeed} aria-label="최근 등록 목록" aria-busy={recent.isPending}>
        {recent.isPending ? (
          <FeedMessage state="loading" message="최근 등록된 곡을 불러오는 중..." />
        ) : recent.isError ? (
          <FeedMessage state="error" message={feedErrorMessage(recent.error)} onRetry={() => recent.refetch()} />
        ) : items.length === 0 ? (
          <FeedMessage state="empty" message="아직 등록된 곡이 없어요." />
        ) : (
          <ul className={styles.homeTrackList}>
            {items.map((item) => (
              <DiscoveryTrack
                item={item}
                key={item.id}
                showRegisteredAt
                authenticated={Boolean(account)}
                quotaEmpty={quotaEmpty}
                votePending={vote.isPending}
                votingTrackId={vote.variables}
                onVote={vote.mutate}
              />
            ))}
          </ul>
        )}
        {voteError ? <p className={styles.feedVoteError} role="alert">{voteError}</p> : null}
        {recent.hasNextPage ? (
          <button
            className={styles.loadMoreButton}
            type="button"
            disabled={recent.isFetchingNextPage}
            onClick={() => recent.fetchNextPage()}
          >
            {recent.isFetchingNextPage ? '불러오는 중...' : '더보기 (20곡)'}
          </button>
        ) : null}
      </section>
    </div>
  )
}
