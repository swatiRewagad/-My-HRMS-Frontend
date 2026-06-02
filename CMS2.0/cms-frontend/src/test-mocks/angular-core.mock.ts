export function Injectable(_opts?: any): ClassDecorator {
  return (target: any) => target;
}

export function inject(_token: any): any {
  return undefined;
}

export function signal<T>(initial: T) {
  let value = initial;
  const fn: any = () => value;
  fn.set = (v: T) => { value = v; };
  fn.update = (updater: (v: T) => T) => { value = updater(value); };
  fn.asReadonly = () => fn;
  return fn;
}

export function computed<T>(factory: () => T) {
  const fn: any = () => factory();
  return fn;
}

export function Component(_opts?: any): ClassDecorator {
  return (target: any) => target;
}

export function Pipe(_opts?: any): ClassDecorator {
  return (target: any) => target;
}

export function OnInit() {}
export function OnDestroy() {}
export function NgZone() {}
