import { describe, expect, it } from 'vitest'
import { shortRepoName } from './ReviewHistory'

describe('shortRepoName', () => {
  it('extracts owner/repo from a plain repo URL', () => {
    expect(shortRepoName('https://github.com/owner/repo')).toBe('owner/repo')
  })

  it('extracts owner/repo from a repo URL with trailing slash', () => {
    expect(shortRepoName('https://github.com/owner/repo/')).toBe('owner/repo')
  })

  it('strips a trailing .git suffix', () => {
    expect(shortRepoName('https://github.com/owner/repo.git')).toBe('owner/repo')
  })

  it('extracts owner/repo from a pull request URL', () => {
    expect(shortRepoName('https://github.com/owner/repo/pull/123')).toBe('owner/repo')
  })

  it('falls back to the raw URL when it does not match a github.com pattern', () => {
    expect(shortRepoName('not-a-url')).toBe('not-a-url')
  })
})
