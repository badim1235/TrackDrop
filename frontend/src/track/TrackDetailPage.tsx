import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  Check,
  Disc3,
  ExternalLink,
  LoaderCircle,
  LogIn,
  ThumbsUp,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, useParams } from 'react-router'
import styles from '../App.module.css'
import {
  ApiError,
  createVote,
  fetchTrackDetail,
  type AccountResponse,
} from '../api/client'
import { accountQueryKey, useAccount } from '../auth/account'

function detailErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.code === 'TRACK_NOT_FOUND') {
    return '등록된 곡을 찾을 수 없습니다.'
  }
  return error instanceof ApiError
    ? error.message
    : '곡 정보를 불러오지 못했습니다. 다시 시도해 주세요.'
}

function formatRegisteredAt(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Seoul',
  }).format(new Date(value))
}

function DetailArtwork({ src }: { src: string | null }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) {
    return <div className={styles.detailArtworkFallback}><Disc3 aria-hidden="true" size={44} /></div>
  }
  return <img className={styles.detailArtwork} src={src} alt="" onError={() => setFailed(true)} />
}

export function TrackDetailPage() {
  const { trackId = '' } = useParams()
  const queryClient = useQueryClient()
  const { data: account } = useAccount()
  const [voteError, setVoteError] = useState<string | null>(null)
  const detail = useQuery({
    queryKey: ['track-detail', trackId],
    queryFn: () => fetchTrackDetail(trackId),
    enabled: Boolean(trackId),
    staleTime: 15_000,
  })
  const vote = useMutation({
    mutationFn: () => createVote(trackId),
    onSuccess: (data) => {
      setVoteError(null)
      queryClient.setQueryData<AccountResponse>(accountQueryKey, (current) => current
        ? { ...current, quota: data.quota }
        : current)
      void queryClient.invalidateQueries({ queryKey: ['track-detail', trackId] })
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
        void queryClient.invalidateQueries({ queryKey: ['track-detail', trackId] })
        return
      }
      setVoteError(detailErrorMessage(error))
    },
  })

  if (detail.isPending) {
    return (
      <div className={styles.detailMessage} aria-busy="true">
        <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={24} />
        <span>곡 정보를 불러오는 중...</span>
      </div>
    )
  }

  if (detail.isError) {
    return (
      <div className={styles.detailMessage} role="alert">
        <Disc3 aria-hidden="true" size={30} strokeWidth={1.5} />
        <strong>곡을 표시할 수 없습니다.</strong>
        <span>{detailErrorMessage(detail.error)}</span>
        <button type="button" onClick={() => detail.refetch()}>다시 시도</button>
      </div>
    )
  }

  const { track, today, actions } = detail.data
  const provider = track.providerReferences[0]
  const hasVoted = track.viewer?.hasVotedToday ?? actions.reason === 'ALREADY_VOTED'
  const metadata = [track.albumName, track.releaseYear, track.providerGenreName]
    .filter((value): value is string | number => value !== null)

  return (
    <article className={styles.trackDetailPage}>
      <NavLink className={styles.detailBackLink} to="/chart">
        <ArrowLeft aria-hidden="true" size={16} /> 차트로 돌아가기
      </NavLink>

      <header className={styles.detailHero}>
        <DetailArtwork src={track.albumCoverUrl} />
        <div className={styles.detailIdentity}>
          <p className={styles.eyebrow}>TRACK DETAIL</p>
          <div className={styles.detailTitleLine}>
            <h1>{track.title}</h1>
            {track.explicit ? <span className={styles.explicitBadge}>Explicit</span> : null}
          </div>
          <p className={styles.detailArtist}>{track.artistName}</p>
          {metadata.length ? <p className={styles.detailMetadata}>{metadata.join(' · ')}</p> : null}
          <span className={styles.detailGenre}>{track.primaryGenre.displayName}</span>

          {track.preview.url ? (
            <audio
              className={styles.detailPreview}
              controls
              controlsList="nodownload noplaybackrate noremoteplayback"
              preload="none"
              src={track.preview.url}
              aria-label={`${track.title} 30초 미리듣기`}
            />
          ) : <p className={styles.detailPreviewUnavailable}>미리듣기를 제공하지 않는 곡입니다.</p>}

          {provider?.externalUrl ? (
            <a className={styles.detailExternalLink} href={provider.externalUrl} target="_blank" rel="noreferrer">
              Apple Music에서 듣기 <ExternalLink aria-hidden="true" size={14} />
            </a>
          ) : null}
        </div>
      </header>

      <section className={styles.detailStats} aria-label="오늘의 추천 현황">
        <div><span>오늘 추천</span><strong>{today.voteCount}표</strong></div>
        <div><span>전체 순위</span><strong>{today.overallRank ? `${today.overallRank}위` : '-'}</strong></div>
        <div><span>{track.primaryGenre.displayName} 순위</span><strong>{today.genreRank ? `${today.genreRank}위` : '-'}</strong></div>
      </section>

      <section className={styles.detailRecommendation} aria-labelledby="detail-comment-heading">
        <p className={styles.sectionLabel}>FIRST PICK</p>
        <h2 id="detail-comment-heading">처음 이 곡을 추천한 한줄평</h2>
        {track.recommendation.commentAvailable && track.recommendation.comment ? (
          <blockquote>“{track.recommendation.comment}”</blockquote>
        ) : <p className={styles.detailHiddenComment}>현재 볼 수 없는 한줄평입니다.</p>}
        <div className={styles.detailRecommender}>
          {track.recommendation.recommenderNickname ? <strong>{track.recommendation.recommenderNickname}</strong> : null}
          <time dateTime={track.recommendation.createdAt}>{formatRegisteredAt(track.recommendation.createdAt)} 등록</time>
        </div>
      </section>

      <section className={styles.detailVoteBand} aria-label="곡 추천하기">
        <div>
          <strong>이 곡이 마음에 드시나요?</strong>
          <span>추천하면 오늘의 추천권 1회를 사용합니다.</span>
        </div>
        {!account ? (
          <NavLink to={`/login?returnTo=${encodeURIComponent(`/tracks/${trackId}`)}`}>
            <LogIn aria-hidden="true" size={16} /> 로그인하고 추천
          </NavLink>
        ) : (
          <button
            className={hasVoted ? styles.votedButton : undefined}
            type="button"
            disabled={!actions.canVote || vote.isPending}
            onClick={() => vote.mutate()}
          >
            {vote.isPending ? <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={16} />
              : hasVoted ? <Check aria-hidden="true" size={16} />
                : <ThumbsUp aria-hidden="true" size={16} />}
            {hasVoted ? '추천 완료' : actions.reason === 'DAILY_LIMIT_EXCEEDED' ? '추천권 없음' : '추천'}
          </button>
        )}
      </section>
      {voteError ? <p className={styles.detailVoteError} role="alert">{voteError}</p> : null}
    </article>
  )
}
