// Drive appDataFolder sync, same rules as Android core/sync SyncEngine.kt:
// pull (changes since page token; full listing when there is none) then push (pending entries, each verified by md5).
// Conflicts: highest updatedAt wins, the loser is kept 30 days. Unknown JSON fields are preserved on every write.
import SparkMD5 from 'spark-md5'
import type { Emo } from './light'
import type { Tag } from './ai'

export interface EntryDoc {
  id: string; date: string; time: string; timeUnknown?: boolean
  createdAt: number; updatedAt: number; deleted: boolean
  /** 휴지통: with deleted=true the content is kept until trashedAt + 30 days. */
  trashedAt?: number | null
  title: string; body: string; template: string; source: string; weather?: string | null
  emotions: Emo[]; tags: Tag[]; photos: { id: string; w: number; h: number }[]
  reply?: { text: string; source: 'template' | 'llm'; templateId?: string | null; createdAt: number; reported?: boolean } | null
  chat: { role: string; text: string; at: number }[]
  [unknown: string]: unknown
}
export interface Rec { doc: EntryDoc; fileId?: string; synced?: number }
export interface DriveFile { id: string; name: string; md5Checksum?: string; appProperties?: Record<string, string> }
export interface DriveApi {
  findByEntityId(entityId: string): Promise<DriveFile | null>
  listAll(pageToken?: string): Promise<{ files: DriveFile[]; nextPageToken?: string }>
  create(name: string, props: Record<string, string>, json: string): Promise<DriveFile>
  /** 404 → throws HttpError(404) */
  update(fileId: string, name: string, json: string): Promise<DriveFile>
  metadata(fileId: string): Promise<DriveFile>
  download(fileId: string): Promise<string>
  startPageToken(): Promise<string>
  changes(pageToken: string): Promise<{ changes: { removed?: boolean; file?: DriveFile }[]; nextPageToken?: string; newStartPageToken?: string }>
}
export class HttpError extends Error { constructor(public code: number, msg: string) { super(`Drive HTTP ${code}: ${msg}`) } }
export class VerifyError extends Error {}

export const LOSER_KEEP_MS = 30 * 24 * 3600 * 1000
export const TRASH_KEEP_MS = 30 * 24 * 3600 * 1000
export const entryPath = (d: EntryDoc) => `entries/${d.date.slice(0, 4)}/${d.date.slice(5, 7)}/${d.id}.json`
export const md5 = (s: string) => SparkMD5.hash(unescape(encodeURIComponent(s))) // md5 of the UTF-8 bytes

type Persist = (kind: 'entry' | 'meta', key: string, value: unknown) => void

/** All local state in memory; [persist] mirrors writes to IndexedDB (none in tests / demo). */
export class Store {
  entries = new Map<string, Rec>()
  meta = new Map<string, unknown>()
  constructor(private persist: Persist = () => {}) {}
  put(rec: Rec) { this.entries.set(rec.doc.id, rec); this.persist('entry', rec.doc.id, rec) }
  get<T>(k: string) { return this.meta.get(k) as T | undefined }
  set(k: string, v: unknown) { if (v === undefined) this.meta.delete(k); else this.meta.set(k, v); this.persist('meta', k, v) }
  pending() { return [...this.entries.values()].filter(r => r.doc.updatedAt !== r.synced) }
  /** Local edit: keeps every field we don't know about, bumps updatedAt. */
  save(doc: EntryDoc, now = Date.now()) {
    const old = this.entries.get(doc.id)
    const updatedAt = Math.max(now, (old?.doc.updatedAt ?? 0) + 1)
    this.put({ ...old, doc: { ...old?.doc, ...doc, updatedAt } })
    return this.entries.get(doc.id)!.doc
  }
  /** Permanent: a content-less tombstone (휴지통 › 영구 삭제, quick-mood undo). */
  remove(id: string, now = Date.now()) {
    const old = this.entries.get(id); if (!old) return
    this.save({ ...old.doc, deleted: true, trashedAt: undefined, title: '', body: '', weather: null, emotions: [], tags: [], photos: [], reply: null, chat: [] }, now)
  }
  /** 지우기 → 휴지통: hidden everywhere, content kept 30 days. Same file format as Android. */
  trash(id: string, now = Date.now()) {
    const old = this.entries.get(id); if (!old || old.doc.deleted) return
    this.save({ ...old.doc, deleted: true, trashedAt: now }, now)
  }
  restore(id: string, now = Date.now()) {
    const old = this.entries.get(id); if (!old?.doc.deleted || old.doc.trashedAt == null) return
    this.save({ ...old.doc, deleted: false, trashedAt: undefined }, now)
  }
  trashed() { return [...this.entries.values()].map(r => r.doc).filter(d => d.deleted && d.trashedAt != null).sort((a, b) => b.trashedAt! - a.trashedAt!) }
  /**
   * Trash older than 30 days becomes a tombstone with updatedAt = trashedAt + 30 days (like Android's EntryDao), so
   * every device expiring it writes the same file instead of a conflict.
   */
  purgeTrash(now = Date.now()) {
    for (const d of this.trashed()) if (d.trashedAt! + TRASH_KEEP_MS <= now) this.remove(d.id, d.trashedAt! + TRASH_KEEP_MS)
  }
  emptyTrash(now = Date.now()) { for (const d of this.trashed()) this.remove(d.id, now) }
  live() { return [...this.entries.values()].map(r => r.doc).filter(d => !d.deleted) }
}

export function newEntry(date: string, time: string, now = Date.now()): EntryDoc {
  return { id: crypto.randomUUID(), date, time, createdAt: now, updatedAt: now, deleted: false, title: '', body: '', template: 'free', source: 'text', emotions: [], tags: [], photos: [], reply: null, chat: [] }
}

/** Minimal shape check; anything else is ignored rather than blocking sync (like Android). */
function parseEntry(json: string): EntryDoc | null {
  try {
    const d = JSON.parse(json)
    if (typeof d?.id !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(d.date) || typeof d.updatedAt !== 'number') return null
    return { title: '', body: '', template: 'free', source: 'text', emotions: [], tags: [], photos: [], chat: [], deleted: false, ...d }
  } catch { return null }
}

const same = (a: EntryDoc, b: EntryDoc) => JSON.stringify(a) === JSON.stringify(b)
const tomb = (d: EntryDoc) => d.deleted && d.trashedAt == null

export class SyncEngine {
  private running: Promise<void> | null = null
  constructor(private api: DriveApi, private store: Store, private now = () => Date.now()) {}

  sync(): Promise<void> {
    if (!this.running) this.running = (async () => { try { await this.pull(); await this.push() } finally { this.running = null } })()
    return this.running
  }

  private async pull(): Promise<void> {
    const s = this.store
    let token = s.get<string>('pageToken')
    if (!token) {
      const start = await this.api.startPageToken() // before listing, so nothing written meanwhile is missed
      let page: string | undefined
      do { const p = await this.api.listAll(page); for (const f of p.files) await this.absorb(f); page = p.nextPageToken } while (page)
      s.set('pageToken', start)
      return
    }
    for (;;) {
      let p
      try { p = await this.api.changes(token) } catch (e) {
        if (e instanceof HttpError && [400, 404, 410].includes(e.code)) { s.set('pageToken', undefined); return this.pull() }
        throw e
      }
      for (const c of p.changes) if (!c.removed && c.file) await this.absorb(c.file)
      if (p.newStartPageToken) { s.set('pageToken', p.newStartPageToken); return }
      if (!p.nextPageToken) return
      token = p.nextPageToken; s.set('pageToken', token)
    }
  }

  private async absorb(f: DriveFile) {
    if (f.name.startsWith('entries/')) {
      const remote = parseEntry(await this.api.download(f.id))
      if (remote) this.mergeEntry(f, remote)
    } else if (f.name === 'settings.json' || f.name === 'entitlement.json') {
      try {
        const json = JSON.parse(await this.api.download(f.id))
        const key = f.name.replace('.json', '')
        const local = this.store.get<{ updatedAt: number }>(key)
        if (!local || (json.updatedAt ?? 0) > local.updatedAt) this.store.set(key, json)
      } catch { /* unreadable: skip */ }
    } else if (f.name === 'manifest.json') {
      try { if ((JSON.parse(await this.api.download(f.id)).schema ?? 1) > 1) this.store.set('readOnly', true) } catch { /* skip */ }
    }
    // photos/*: fetched on demand; anything else is left alone.
  }

  private mergeEntry(f: DriveFile, remote: EntryDoc) {
    const s = this.store
    const rec = s.entries.get(remote.id)
    const apply = () => s.put({ doc: remote, fileId: f.id, synced: remote.updatedAt })
    if (!rec) return apply()
    if (remote.updatedAt === rec.synced) return // our own upload echoing back
    if (rec.doc.updatedAt === rec.synced) return apply() // only remote changed: take it even if its clock is behind
    if (tomb(rec.doc) && tomb(remote)) return apply() // the same deletion from two devices: nothing to keep
    if (same(rec.doc, remote)) return s.put({ ...rec, fileId: f.id, synced: remote.updatedAt })
    const keepUntil = this.now() + LOSER_KEEP_MS
    if (remote.updatedAt > rec.doc.updatedAt) { this.keepLoser(rec.doc, keepUntil); apply() }
    else { this.keepLoser(remote, keepUntil); s.put({ ...rec, fileId: f.id }) } // local stays pending, overwrites in push
  }

  private keepLoser(doc: EntryDoc, keepUntil: number) {
    const now = this.now()
    const list = (this.store.get<{ doc: EntryDoc; keepUntil: number }[]>('conflicts') ?? []).filter(c => c.keepUntil > now)
    this.store.set('conflicts', [...list, { doc, keepUntil }])
  }

  private async push() {
    const s = this.store
    if (s.get('readOnly')) return // a newer schema is on Drive: never write
    for (const rec of s.pending()) {
      const json = JSON.stringify(rec.doc)
      const name = entryPath(rec.doc)
      const known = rec.fileId ?? (await this.api.findByEntityId(rec.doc.id))?.id
      const props = { entityId: rec.doc.id, kind: 'entry' }
      let file: DriveFile
      if (!known) file = await this.api.create(name, props, json)
      else try { file = await this.api.update(known, name, json) } catch (e) {
        if (!(e instanceof HttpError && e.code === 404)) throw e
        file = await this.api.create(name, props, json)
      }
      const sum = md5(json)
      if (file.md5Checksum !== sum || (await this.api.metadata(file.id)).md5Checksum !== sum) throw new VerifyError(`md5 mismatch for ${name}`)
      // The doc may have been edited while uploading: record the version we actually sent.
      const cur = s.entries.get(rec.doc.id)!
      s.put({ ...cur, fileId: file.id, synced: rec.doc.updatedAt })
    }
    await this.writeManifest()
    s.set('lastBackupAt', this.now())
  }

  private async writeManifest() {
    const s = this.store
    if (s.get('manifestOk')) return
    let device = s.get<string>('deviceId')
    if (!device) { device = 'web-' + crypto.randomUUID(); s.set('deviceId', device) }
    const existing = await this.api.findByEntityId('manifest')
    const old = existing ? JSON.parse(await this.api.download(existing.id)) : null
    if (old && (old.schema ?? 1) > 1) { s.set('readOnly', true); return }
    if (!old || !old.deviceIds?.includes(device)) {
      const json = JSON.stringify({ ...old, schema: 1, deviceIds: [...new Set([...(old?.deviceIds ?? []), device])], updatedAt: this.now() })
      if (existing) await this.api.update(existing.id, 'manifest.json', json)
      else await this.api.create('manifest.json', { entityId: 'manifest', kind: 'manifest' }, json)
    }
    s.set('manifestOk', true)
  }
}
