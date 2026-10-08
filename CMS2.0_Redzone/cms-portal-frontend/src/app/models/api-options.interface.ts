// api-options.interface.ts
import { HttpContext, HttpHeaders, HttpParams } from '@angular/common/http';

export interface ApiOptions {
  headers?: HttpHeaders | { [header: string]: string | string[] };
  params?: HttpParams | { [param: string]: string | number | boolean | ReadonlyArray<string | number | boolean> };
  context?: HttpContext;
  reportProgress?: boolean;
  withCredentials?: boolean;
}
