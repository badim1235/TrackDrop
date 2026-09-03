const APPLE_ARTWORK_HOST = /(^|\.)mzstatic\.com$/i
const ARTWORK_SIZE_SEGMENT = /\/\d+x\d+bb(?=\.[a-z]+(?:\?|$))/i

export function detailArtworkUrl(source: string) {
  try {
    const url = new URL(source)
    if (!APPLE_ARTWORK_HOST.test(url.hostname)) return source
    return source.replace(ARTWORK_SIZE_SEGMENT, '/600x600bb')
  } catch {
    return source
  }
}
