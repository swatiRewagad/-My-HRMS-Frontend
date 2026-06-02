export class HttpClient {}
export class HttpParams {
  private params: Record<string, string> = {};
  set(key: string, value: string): HttpParams {
    const p = new HttpParams();
    p.params = { ...this.params, [key]: value };
    return p;
  }
  get(key: string): string | null { return this.params[key] || null; }
}
export class HttpRequest {
  constructor(public method: string, public url: string, public body?: any, public options?: any) {}
}
export class HttpHeaders {
  private headers: Record<string, string> = {};
  get(key: string): string | null { return this.headers[key] || null; }
}
export const HttpEventType = { UploadProgress: 1, Response: 4 };
export class HttpErrorResponse {
  constructor(public status: number, public error?: any) {}
}
export type HttpInterceptorFn = (req: any, next: any) => any;
export type HttpHandlerFn = (req: any) => any;
