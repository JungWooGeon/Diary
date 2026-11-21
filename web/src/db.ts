// IndexedDB mirror of Store (offline cache): two object stores, loaded whole at startup.
import { Store, type Rec } from './sync'

const open = () => new Promise<IDBDatabase>((ok, fail) => {
  const r = indexedDB.open('oni-diary', 1)
  r.onupgradeneeded = () => { r.result.createObjectStore('entry'); r.result.createObjectStore('meta') }
  r.onsuccess = () => ok(r.result); r.onerror = () => fail(r.error)
})

const all = (db: IDBDatabase, name: string) => new Promise<[string, unknown][]>((ok, fail) => {
  const os = db.transaction(name).objectStore(name), keys = os.getAllKeys(), vals = os.getAll()
  vals.onsuccess = () => ok((keys.result as string[]).map((k, i) => [k, vals.result[i]]))
  vals.onerror = () => fail(vals.error)
})

export async function loadStore(): Promise<Store> {
  let db: IDBDatabase | null = null
  // Some browsers/modes never answer indexedDB.open (no event at all): don't hang the app on it, run memory-only.
  try { db = await Promise.race([open(), new Promise<never>((_, fail) => setTimeout(() => fail(new Error('idb timeout')), 2500))]) } catch { /* memory only */ }
  const store = new Store((kind, key, value) => {
    if (!db) return
    const os = db.transaction(kind, 'readwrite').objectStore(kind)
    if (value === undefined) os.delete(key); else os.put(value, key)
  })
  if (db) {
    for (const [k, v] of await all(db, 'entry')) store.entries.set(k, v as Rec)
    for (const [k, v] of await all(db, 'meta')) store.meta.set(k, v)
  }
  return store
}

export async function clearDb() {
  try {
    const db = await open()
    const tx = db.transaction(['entry', 'meta'], 'readwrite')
    tx.objectStore('entry').clear(); tx.objectStore('meta').clear()
  } catch { /* nothing cached */ }
}
