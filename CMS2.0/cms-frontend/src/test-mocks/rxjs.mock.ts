export class BehaviorSubject<T> {
  private _value: T;
  constructor(initial: T) { this._value = initial; }
  get value(): T { return this._value; }
  next(val: T) { this._value = val; }
  asObservable() { return this; }
}

export class Subject<T> {
  private subscribers: any[] = [];
  next(val: T) { this.subscribers.forEach(s => s.next?.(val)); }
  complete() { this.subscribers.forEach(s => s.complete?.()); }
  error(err: any) { this.subscribers.forEach(s => s.error?.(err)); }
  subscribe(obs: any) { this.subscribers.push(obs); return { unsubscribe: () => {} }; }
  asObservable() { return this; }
  pipe(...ops: any[]) { return this; }
}

export class Observable<T> {
  subscribe(_: any) { return { unsubscribe: () => {} }; }
}

export function of(...args: any[]) {
  const observable: any = {
    subscribe: (obs: any) => {
      if (typeof obs === 'function') { obs(args[0]); return { unsubscribe: () => {} }; }
      if (obs.next) obs.next(args[0]);
      if (obs.complete) obs.complete();
      return { unsubscribe: () => {} };
    },
    pipe: (...operators: any[]) => {
      let result = observable;
      for (const op of operators) {
        if (typeof op === 'function') result = op(result) || result;
      }
      return result;
    },
  };
  return observable;
}

export function from(_arr: any[]) {
  return { pipe: () => ({ subscribe: () => {} }) };
}

export function forkJoin(_arr: any[]) {
  return { subscribe: () => {} };
}

export function throwError(errorFactory: () => any) {
  return { subscribe: (obs: any) => { if (obs.error) obs.error(errorFactory()); }, pipe: (...ops: any[]) => { const err = errorFactory(); return { subscribe: (o: any) => { if (o.error) o.error(err); } }; } };
}

export function delay(_ms: number) { return (source: any) => source; }
export function concatMap(_fn: any) { return (source: any) => source; }
export function tap(_fn: any) { return (source: any) => source; }
export function last() { return (source: any) => source; }
export function map(_fn: any) { return (source: any) => source; }
