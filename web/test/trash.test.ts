import { test, expect } from 'vitest'
import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import entrySchema from '../../shared/schema/entry.schema.json'
import trashedFixture from '../../shared/fixtures/entry-trashed.json'
import tombFixture from '../../shared/fixtures/entry-tombstone.json'
import { Store, SyncEngine, HttpError, md5, newEntry, entryPath, TRASH_KEEP_MS, type DriveApi, type DriveFile, type EntryDoc } from '../src/sync'
import { zip, crc32, exportZip, diaryHtml } from '../src/archive'

const validEntry = addFormats(new Ajv2020({ strict: false })).compile(entrySchema)

class FakeDrive implements DriveApi {
  files = new Map<string, { id: string; name: string; props: Record<string, string>; content: string }>()
  log: string[] = []
  private n = 0
  private meta(f: { id: string; name: string; props: Record<string, string>; content: string }): DriveFile { return { id: f.id, name: f.name, appProperties: f.props, md5Checksum: md5(f.content) } }
  put(name: string, props: Record<string, string>, content: string) {
    const existing = [...this.files.values()].find(f => f.props.entityId === props.entityId)
    const f = existing ? Object.assign(existing, { name, content }) : { id: 'f' + ++this.n, name, props, content }
    this.files.set(f.id, f); this.log.push(f.id); return f
  }
  async findByEntityId(id: string) { const f = [...this.files.values()].find(f => f.props.entityId === id); return f ? this.meta(f) : null }
  async listAll() { return { files: [...this.files.values()].map(f => this.meta(f)) } }
  async create(name: string, props: Record<string, string>, json: string) { return this.meta(this.put(name, props, json)) }
  async update(id: string, name: string, json: string) { const f = this.files.get(id); if (!f) throw new HttpError(404, 'gone'); Object.assign(f, { name, content: json }); this.log.push(id); return this.meta(f) }
  async metadata(id: string) { return this.meta(this.files.get(id)!) }
  async download(id: string) { return this.files.get(id)!.content }
  async startPageToken() { return String(this.log.length) }
  async changes(t: string) { return { changes: this.log.slice(Number(t)).map(id => ({ file: this.meta(this.files.get(id)!) })), newStartPageToken: String(this.log.length) } }
  doc(id: string): EntryDoc { return JSON.parse([...this.files.values()].find(f => f.props.entityId === id)!.content) }
  putDoc(d: Record<string, unknown> & { id: string }) { this.put(entryPath(d as EntryDoc), { entityId: d.id, kind: 'entry' }, JSON.stringify(d)) }
}

const device = (drive: FakeDrive, clock: { t: number }) => { const store = new Store(); return { store, engine: new SyncEngine(drive, store, () => clock.t) } }

test('shared fixtures are schema-valid and read as trash / tombstone', async () => {
  expect(validEntry(trashedFixture), JSON.stringify(validEntry.errors)).toBe(true)
  expect(validEntry(tombFixture)).toBe(true)
  const drive = new FakeDrive(); drive.putDoc(trashedFixture); drive.putDoc(tombFixture)
  const { store, engine } = device(drive, { t: 1 })
  await engine.sync()
  expect(store.live()).toEqual([])
  expect(store.trashed().map(d => d.id)).toEqual([trashedFixture.id])
  expect(store.trashed()[0].body).toBe(trashedFixture.body)
})

test('delete on the web → trash on Drive (content kept) → restore on Android comes back here', async () => {
  const drive = new FakeDrive(), clock = { t: 1000 }
  const { store, engine } = device(drive, clock)
  const e = store.save({ ...newEntry('2026-11-21', '08:15', clock.t), body: '아침', emotions: [{ key: 'joy', intensity: 3 }] }, clock.t)
  await engine.sync()
  store.trash(e.id, ++clock.t)
  await engine.sync()
  const onDrive = drive.doc(e.id)
  expect(validEntry(onDrive)).toBe(true)
  expect(onDrive).toMatchObject({ deleted: true, trashedAt: clock.t, body: '아침' })
  // Android restores it: deleted=false, no trashedAt, newer updatedAt.
  const { trashedAt: _, ...rest } = onDrive
  drive.putDoc({ ...rest, deleted: false, updatedAt: 5000 })
  await engine.sync()
  expect(store.live().map(d => d.body)).toEqual(['아침'])
  expect(store.trashed()).toEqual([])
})

test('restore on the web drops trashedAt from the file; undo after delete', async () => {
  const drive = new FakeDrive(), clock = { t: 1000 }
  const { store, engine } = device(drive, clock)
  drive.putDoc(trashedFixture)
  await engine.sync()
  store.restore(trashedFixture.id, ++clock.t)
  await engine.sync()
  const out = drive.doc(trashedFixture.id)
  expect(out.deleted).toBe(false); expect('trashedAt' in out).toBe(false); expect(out.reply?.text).toBe('오늘도 애썼어')
  expect(validEntry(out)).toBe(true)
})

test('trash expires after 30 days into the same tombstone on every device; permanent delete empties it', async () => {
  const drive = new FakeDrive(), clock = { t: 1000 }
  const a = device(drive, clock), b = device(drive, clock)
  const e = a.store.save({ ...newEntry('2026-11-21', '08:15', clock.t), body: '비밀', tags: [{ kind: 'free', value: 'x', auto: false }] }, clock.t)
  a.store.trash(e.id, 2000)
  await a.engine.sync(); await b.engine.sync()
  a.store.purgeTrash(2000 + TRASH_KEEP_MS - 1)
  expect(a.store.trashed().length).toBe(1)
  a.store.purgeTrash(2000 + TRASH_KEEP_MS); b.store.purgeTrash(2000 + TRASH_KEEP_MS)
  const t = a.store.entries.get(e.id)!.doc
  expect(t).toMatchObject({ deleted: true, body: '', title: '', tags: [], updatedAt: 2000 + TRASH_KEEP_MS })
  expect(t.trashedAt).toBeUndefined()
  await a.engine.sync(); await b.engine.sync()
  expect(b.store.get('conflicts')).toBeUndefined()
  expect(validEntry(drive.doc(e.id))).toBe(true)

  const f = a.store.save({ ...newEntry('2026-11-22', '09:00', clock.t), body: '또' }, 3000)
  a.store.trash(f.id, 3001); a.store.emptyTrash(3002)
  expect(a.store.entries.get(f.id)!.doc).toMatchObject({ deleted: true, body: '', updatedAt: 3002 })
})

/** Reads a stored zip back through its central directory, checking every CRC (crc32 itself is checked on the standard vector). */
function unzip(b: Uint8Array) {
  const v = new DataView(b.buffer, b.byteOffset), dec = new TextDecoder()
  const end = b.length - 22
  expect(v.getUint32(end, true)).toBe(0x06054b50)
  const files: Record<string, string> = {}
  let p = v.getUint32(end + 16, true)
  for (let i = 0; i < v.getUint16(end + 10, true); i++) {
    const nameLen = v.getUint16(p + 28, true), size = v.getUint32(p + 24, true), off = v.getUint32(p + 42, true), crc = v.getUint32(p + 16, true)
    const name = dec.decode(b.subarray(p + 46, p + 46 + nameLen))
    const data = b.subarray(off + 30 + v.getUint16(off + 26, true), off + 30 + v.getUint16(off + 26, true) + size)
    expect(v.getUint32(off, true)).toBe(0x04034b50); expect(crc32(data)).toBe(crc)
    files[name] = dec.decode(data); p += 46 + nameLen
  }
  return files
}

test('zip writer: valid archive, same entries.json as Android', () => {
  expect(crc32(new TextEncoder().encode('123456789'))).toBe(0xcbf43926)
  const docs = [trashedFixture as EntryDoc, { ...newEntry('2026-11-21', '08:15', 1), body: '<b>안녕</b>', emotions: [{ key: 'joy', intensity: 2 }] }]
  const files = unzip(exportZip(docs, { joy: '기쁨', tired: '지침' }, 42))
  expect(Object.keys(files)).toEqual(['entries.json', 'diary.html', 'README.txt'])
  const json = JSON.parse(files['entries.json'])
  expect(json).toMatchObject({ format: 'oni-diary-export', version: 1, exportedAt: 42 })
  expect(json.entries).toEqual(docs)
  for (const d of json.entries) expect(validEntry(d)).toBe(true)
  expect(files['diary.html']).toContain('2026년 11월 21일'); expect(files['diary.html']).toContain('&lt;b&gt;안녕')
  expect(files['diary.html']).toContain('기쁨')
  expect(unzip(zip([]))).toEqual({})
  expect(diaryHtml([], {})).toContain('0개의 기록')
})
