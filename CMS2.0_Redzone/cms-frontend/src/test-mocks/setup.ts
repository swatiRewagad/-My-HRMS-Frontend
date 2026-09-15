// Polyfill structuredClone for jsdom (needed by fake-indexeddb)
if (typeof globalThis.structuredClone === 'undefined') {
  (globalThis as any).structuredClone = (obj: any) => {
    if (obj instanceof ArrayBuffer) {
      return obj.slice(0);
    }
    if (Array.isArray(obj)) {
      return obj.map((item: any) => (globalThis as any).structuredClone(item));
    }
    if (obj && typeof obj === 'object') {
      const result: any = {};
      for (const key of Object.keys(obj)) {
        if (obj[key] instanceof ArrayBuffer) {
          result[key] = obj[key].slice(0);
        } else {
          result[key] = obj[key];
        }
      }
      return result;
    }
    return obj;
  };
}

import 'fake-indexeddb/auto';

// Polyfill File.arrayBuffer() for jsdom
if (!File.prototype.arrayBuffer) {
  File.prototype.arrayBuffer = function () {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as ArrayBuffer);
      reader.onerror = () => reject(reader.error);
      reader.readAsArrayBuffer(this);
    });
  };
}

// Polyfill File.text() for jsdom
if (!File.prototype.text) {
  File.prototype.text = function () {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = () => reject(reader.error);
      reader.readAsText(this);
    });
  };
}
