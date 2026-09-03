import { describe, expect, it } from 'vitest'
import { detailArtworkUrl } from './artwork'

describe('detailArtworkUrl', () => {
  it('requests a 600 pixel Apple artwork variant for the detail page', () => {
    expect(detailArtworkUrl(
      'https://is1-ssl.mzstatic.com/image/thumb/Music211/example/100x100bb.jpg',
    )).toBe('https://is1-ssl.mzstatic.com/image/thumb/Music211/example/600x600bb.jpg')
  })

  it('leaves non-Apple artwork URLs unchanged', () => {
    expect(detailArtworkUrl('https://example.com/100x100bb.jpg'))
      .toBe('https://example.com/100x100bb.jpg')
  })
})
