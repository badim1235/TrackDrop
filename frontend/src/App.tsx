import {
  BarChart3,
  ChevronRight,
  Disc3,
  Headphones,
  Home,
  LogIn,
  Music2,
  Plus,
  Search,
  Sparkles,
} from 'lucide-react'
import { NavLink, Route, Routes } from 'react-router'
import styles from './App.module.css'

const genres = ['전체', 'Hip-Hop', 'R&B', 'Ballad', 'Pop', 'Rock', 'Indie', 'Electronic', 'Jazz', 'Classical']

function EmptyTrackList({ kind }: { kind: 'popular' | 'recent' }) {
  return (
    <div className={styles.emptyState}>
      <Disc3 aria-hidden="true" size={30} strokeWidth={1.5} />
      <p>{kind === 'popular' ? '오늘의 첫 추천을 기다리고 있어요.' : '아직 등록된 곡이 없어요.'}</p>
    </div>
  )
}

function HomePage() {
  return (
    <div className={styles.pageStack}>
      <section className={styles.intro}>
        <div>
          <p className={styles.eyebrow}>TODAY'S DROPS</p>
          <h1>오늘 발견한 음악을<br />함께 차트에 <span className={styles.mobileBreak}>올려보세요.</span></h1>
        </div>
        <NavLink className={styles.primaryAction} to="/recommend">
          <Plus aria-hidden="true" size={19} />
          곡 추천하기
        </NavLink>
      </section>

      <section className={styles.contentSection} aria-labelledby="popular-heading">
        <div className={styles.sectionHeader}>
          <div>
            <p className={styles.sectionLabel}>지금 가장 뜨거운</p>
            <h2 id="popular-heading">오늘의 추천</h2>
          </div>
          <NavLink className={styles.textLink} to="/chart">
            차트 보기 <ChevronRight aria-hidden="true" size={17} />
          </NavLink>
        </div>
        <EmptyTrackList kind="popular" />
      </section>

      <section className={styles.contentSection} aria-labelledby="recent-heading">
        <div className={styles.sectionHeader}>
          <div>
            <p className={styles.sectionLabel}>새롭게 도착한</p>
            <h2 id="recent-heading">최근 등록된 곡</h2>
          </div>
        </div>
        <EmptyTrackList kind="recent" />
      </section>
    </div>
  )
}

function ChartPage() {
  return (
    <div className={styles.pageStack}>
      <header className={styles.pageHeader}>
        <p className={styles.eyebrow}>DAILY CHART</p>
        <h1>오늘의 차트</h1>
        <p>실시간 추천 순위</p>
      </header>
      <div className={styles.genreTabs} role="tablist" aria-label="장르">
        {genres.map((genre, index) => (
          <button className={index === 0 ? styles.activeGenre : undefined} role="tab" aria-selected={index === 0} key={genre}>
            {genre}
          </button>
        ))}
      </div>
      <section className={styles.chartPanel} aria-label="오늘의 순위">
        <div className={styles.chartColumns} aria-hidden="true">
          <span>순위</span><span>곡</span><span>추천</span>
        </div>
        <EmptyTrackList kind="popular" />
      </section>
    </div>
  )
}

function RecommendPage() {
  return (
    <div className={styles.recommendPage}>
      <div className={styles.recommendIcon}><Headphones aria-hidden="true" size={28} /></div>
      <p className={styles.eyebrow}>DROP A TRACK</p>
      <h1>어떤 곡을 추천할까요?</h1>
      <p className={styles.recommendCopy}>곡이나 아티스트를 검색하고 대표 장르와 한줄평을 남겨보세요.</p>
      <div className={styles.searchShell}>
        <Search aria-hidden="true" size={20} />
        <input aria-label="곡 또는 아티스트 검색" placeholder="곡 또는 아티스트 검색" />
        <button type="button">검색</button>
      </div>
      <p className={styles.loginNotice}><LogIn aria-hidden="true" size={16} /> 추천을 등록하려면 로그인이 필요합니다.</p>
    </div>
  )
}

function Navigation() {
  const links = [
    { to: '/', label: '홈', icon: Home, end: true },
    { to: '/chart', label: '차트', icon: BarChart3 },
    { to: '/recommend', label: '추천하기', icon: Sparkles },
  ]

  return (
    <nav className={styles.navigation} aria-label="주요 메뉴">
      {links.map(({ to, label, icon: Icon, end }) => (
        <NavLink key={to} to={to} end={end} className={({ isActive }) => isActive ? styles.activeNav : undefined}>
          <Icon aria-hidden="true" size={18} />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

function App() {
  return (
    <div className={styles.app}>
      <header className={styles.topBar}>
        <NavLink className={styles.brand} to="/" aria-label="TrackDrop 홈">
          <span className={styles.brandMark}><Music2 aria-hidden="true" size={20} /></span>
          <span>TrackDrop</span>
        </NavLink>
        <Navigation />
        <div className={styles.accountArea}>
          <button className={styles.loginButton} type="button"><LogIn aria-hidden="true" size={17} /><span>로그인</span></button>
        </div>
      </header>

      <main className={styles.main}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/chart" element={<ChartPage />} />
          <Route path="/recommend" element={<RecommendPage />} />
        </Routes>
      </main>

      <footer className={styles.footer}>TrackDrop · 매일 00:00 KST 차트 갱신</footer>
      <div className={styles.mobileNav}><Navigation /></div>
    </div>
  )
}

export default App
