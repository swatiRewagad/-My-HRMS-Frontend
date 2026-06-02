import { Injectable } from '@angular/core';

interface CachedFileEntry {
  name: string;
  size: number;
  type: string;
  data: ArrayBuffer;
}

const DB_NAME = 'cms_file_cache';
const STORE_NAME = 'files';
const DB_VERSION = 1;

@Injectable({ providedIn: 'root' })
export class FileCacheService {
  private dbReady: Promise<IDBDatabase>;

  constructor() {
    this.dbReady = this.openDb();
  }

  private openDb(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          db.createObjectStore(STORE_NAME);
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async store(complaintId: string, files: File[]): Promise<void> {
    if (files.length === 0) return;

    const entries: CachedFileEntry[] = await Promise.all(
      files.map(async (f) => ({
        name: f.name,
        size: f.size,
        type: f.type,
        data: await f.arrayBuffer(),
      }))
    );

    const db = await this.dbReady;
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      tx.objectStore(STORE_NAME).put(entries, complaintId);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async get(complaintId: string): Promise<File[]> {
    try {
      const db = await this.dbReady;
      const entries: CachedFileEntry[] | undefined = await new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, 'readonly');
        const req = tx.objectStore(STORE_NAME).get(complaintId);
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      });

      if (!entries || entries.length === 0) return [];

      return entries.map(e => new File([e.data], e.name, { type: e.type }));
    } catch {
      return [];
    }
  }

  async has(complaintId: string): Promise<boolean> {
    const files = await this.get(complaintId);
    return files.length > 0;
  }

  async remove(complaintId: string): Promise<void> {
    const db = await this.dbReady;
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      tx.objectStore(STORE_NAME).delete(complaintId);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }
}
