import { test, expect, beforeEach } from 'vitest'
import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import entrySchema from '../../shared/schema/entry.schema.json'
import manifestSchema from '../../shared/schema/manifest.schema.json'
import { Store, SyncEngine, HttpError, VerifyError, md5, newEntry, entryPath, type DriveApi, type DriveFile, type EntryDoc } from '../src/sync'

const ajv = addFormats(new Ajv2020({ strict: false }))
const validEntry = ajv.compile(entrySchema)
const validManifest = ajv.compile(manifestSchema)

/** In-memory Drive appDataFolder with a change log (page token = index into the log). */
class FakeDrive implements DriveApi {
  files = new Map<string, { id: string; name: string; props: Record<string, string>; content: string }>()
  log: string[] = []
  writes = 0
  corrupt = false
  private n = 0
  private meta(f: { id: string; name: string; props: Record<string, string>; content: string }): DriveFile {
    return { id: f.id, name: f.name, appProperties: f.props, md5Checksum: this.corrupt ? 'bad' : md5(f.content) }
  }
  put(name: string, props: Record<string, string>, content: string) { // "another device" writes
    const existing = [...this.files.values()].find(f => f.props.entityId === props.entityId)
    const f = existing ? Object.assign(existing, { name, content }) : { id: 'f' + ++this.n, name, props, content }
    this.files.set(f.id, f); this.log.push(f.id); return f
  }
  async findByEntityId(id: string) { const f = [...this.files.values()].find(f => f.props.entityId === id); return f ? this.meta(f) : null }
  async listAll() { return { files: [...this.files.values()].map(f => this.meta(f)) } }
  async create(name: string, props: Record<string, string>, json: string) { this.writes++; return this.meta(this.put(name, props, json)) }
  async update(id: string, name: string, json: string) {
    const f = this.files.get(id); if (!f) throw new HttpError(404, 'gone')
    this.writes++; Object.assign(f, { name, content: json }); this.log.push(id); return this.meta(f)
  }
  async metadata(id: string) { return this.meta(this.files.get(id)!) }
  async download(id: string) { return this.files.get(id)!.content }
  async startPageToken() { return String(this.log.length) }
  async changes(t: string) {
    const ids = this.log.slice(Number(t))
    return { changes: ids.map(id => ({ file: this.meta(this.files.get(id)!) })), newStartPageToken: String(this.log.length) }
  }
  doc(entityId: string): EntryDoc { return JSON.parse([...this.files.values()].find(f => f.props.entityId === entityId)!.content) }
}

const ANDROID = {
  id: '6f1c2b1e-1111-4a2b-9c3d-000000000001', date: '2026-11-20', time: '21:40', timeUnknown: false, createdAt: 1000, updatedAt: 1000, deleted: false,
  title: '한강', body: '산책했다', template: 'free', source: 'voice', weather: 'clear',
  emotions: [{ key: 'proud', intensity: 4 }], tags: [{ kind: 'place', value: '한강', auto: true }],
  photos: [{ id: '6f1c2b1e-1111-4a2b-9c3d-0000000000aa', w: 1600, h: 1200 }],
  reply: { text: '좋았겠다', source: 'llm', templateId: null, createdAt: 1001, reported: false },
  chat: [{ role: 'oni', text: '오늘 어땠어?', at: 999 }],
  futureField: { keep: 'me' },
}

let drive: FakeDrive, store: Store, clock: number, engine: SyncEngine
beforeEach(() => {
  drive = new FakeDrive(); store = new Store(); clock = 5000
  engine = new SyncEngine(drive, store, () => clock)
  drive.put(entryPath(ANDROID as EntryDoc), { entityId: ANDROID.id, kind: 'entry' }, JSON.stringify(ANDROID))
  drive.put('manifest.json', { entityId: 'manifest', kind: 'manifest' }, JSON.stringify({ schema: 1, deviceIds: ['android-1'], updatedAt: 1 }))
})

test('Android entry round-trips through a web edit: schema-valid, nothing lost', async () => {
  expect(validEntry(ANDROID)).toBe(true)
  await engine.sync()
  const local = store.entries.get(ANDROID.id)!.doc
  store.save({ ...local, body: '산책했다. 바람 좋았다', emotions: [...local.emotions, { key: 'comfy', intensity: 3 }] }, clock)
  await engine.sync()
  const out = drive.doc(ANDROID.id)
  expect(validEntry(out), JSON.stringify(validEntry.errors)).toBe(true)
  expect(out.body).toBe('산책했다. 바람 좋았다')
  expect(out.updatedAt).toBe(5000)
  for (const k of ['photos', 'reply', 'chat', 'futureField', 'weather', 'source', 'tags', 'createdAt'] as const) expect(out[k]).toEqual(ANDROID[k])
  const manifest = JSON.parse([...drive.files.values()].find(f => f.name === 'manifest.json')!.content)
  expect(validManifest(manifest)).toBe(true)
  expect(manifest.deviceIds).toContain('android-1')
  expect(manifest.deviceIds.length).toBe(2)
})

test('new web entry and tombstone are schema-valid; date change renames the file', async () => {
  const e = store.save({ ...newEntry('2026-11-21', '08:15', clock), body: '아침', emotions: [{ key: 'joy', intensity: 3 }] }, clock)
  await engine.sync()
  expect(validEntry(drive.doc(e.id))).toBe(true)
  store.save({ ...store.entries.get(e.id)!.doc, date: '2026-12-01' }, ++clock)
  await engine.sync()
  expect([...drive.files.values()].find(f => f.props.entityId === e.id)!.name).toBe(`entries/2026/12/${e.id}.json`)
  store.remove(e.id, ++clock)
  await engine.sync()
  const t = drive.doc(e.id)
  expect(t.deleted).toBe(true); expect(t.body).toBe(''); expect(validEntry(t)).toBe(true)
  expect(store.live().find(d => d.id === e.id)).toBeUndefined()
})

test('conflict: newer remote wins, local kept 30 days', async () => {
  await engine.sync()
  store.save({ ...store.entries.get(ANDROID.id)!.doc, body: 'web' }, 2000)
  drive.put(entryPath(ANDROID as EntryDoc), { entityId: ANDROID.id, kind: 'entry' }, JSON.stringify({ ...ANDROID, body: 'android', updatedAt: 3000 }))
  await engine.sync()
  expect(store.entries.get(ANDROID.id)!.doc.body).toBe('android')
  expect(drive.doc(ANDROID.id).body).toBe('android')
  expect(store.get<{ doc: EntryDoc }[]>('conflicts')![0].doc.body).toBe('web')
})

test('conflict: newer local wins and overwrites Drive', async () => {
  await engine.sync()
  store.save({ ...store.entries.get(ANDROID.id)!.doc, body: 'web' }, 9000)
  drive.put(entryPath(ANDROID as EntryDoc), { entityId: ANDROID.id, kind: 'entry' }, JSON.stringify({ ...ANDROID, body: 'android', updatedAt: 3000 }))
  await engine.sync()
  expect(drive.doc(ANDROID.id).body).toBe('web')
  expect(store.get<{ doc: EntryDoc }[]>('conflicts')![0].doc.body).toBe('android')
})

test('remote-only change is taken even with an older clock', async () => {
  await engine.sync()
  drive.put(entryPath(ANDROID as EntryDoc), { entityId: ANDROID.id, kind: 'entry' }, JSON.stringify({ ...ANDROID, body: 'fixed', updatedAt: 500 }))
  await engine.sync()
  expect(store.entries.get(ANDROID.id)!.doc.body).toBe('fixed')
  expect(store.pending()).toEqual([])
})

test('newer schema on Drive: web never writes', async () => {
  drive.put('manifest.json', { entityId: 'manifest', kind: 'manifest' }, JSON.stringify({ schema: 2, deviceIds: [], updatedAt: 1 }))
  store.save(newEntry('2026-11-21', '10:00', clock), clock)
  await engine.sync()
  expect(drive.writes).toBe(0)
  expect(store.pending().length).toBe(1)
})

test('upload verification failure keeps the entry pending', async () => {
  store.save(newEntry('2026-11-21', '10:00', clock), clock)
  drive.corrupt = true
  await expect(engine.sync()).rejects.toBeInstanceOf(VerifyError)
  expect(store.pending().length).toBe(1)
})

test('unparseable remote file is skipped, settings/entitlement absorbed by updatedAt', async () => {
  drive.put('entries/2026/11/x.json', { entityId: 'x', kind: 'entry' }, '{oops')
  drive.put('entitlement.json', { entityId: 'entitlement', kind: 'entitlement' }, JSON.stringify({ premium: true, updatedAt: 10 }))
  await engine.sync()
  expect(store.get<{ premium: boolean }>('entitlement')!.premium).toBe(true)
  expect(store.entries.size).toBe(1)
})
