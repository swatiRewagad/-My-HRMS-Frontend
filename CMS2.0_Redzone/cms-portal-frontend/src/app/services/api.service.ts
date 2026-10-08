// api.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiOptions } from '../models/api-options.interface';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private readonly http = inject(HttpClient);

  // Replace this with your environment variable in a real app
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1`;

  /**
   * Helper to construct the full URL dynamically
   */
  private buildUrl(endpoint: string): string {
    return endpoint.startsWith('http') ? endpoint : `${this.baseUrl}/${endpoint.replace(/^\//, '')}`;
  }

  /**
   * GET Request
   * @template T The expected response type
   */
  get<T>(endpoint: string, options?: ApiOptions): Observable<T> {
    return this.http.get<T>(this.buildUrl(endpoint), options);
  }

  /**
   * POST Request
   * @template T The expected response type
   * @template R The request body type (defaults to any)
   */
  post<T, R = any>(endpoint: string, body: R | null, options?: ApiOptions): Observable<T> {
    return this.http.post<T>(this.buildUrl(endpoint), body, options);
  }

  /**
   * PUT Request
   * @template T The expected response type
   * @template R The request body type (defaults to any)
   */
  put<T, R = any>(endpoint: string, body: R | null, options?: ApiOptions): Observable<T> {
    return this.http.put<T>(this.buildUrl(endpoint), body, options);
  }

  /**
   * PATCH Request
   * @template T The expected response type
   * @template R The request body type (defaults to any)
   */
  patch<T, R = any>(endpoint: string, body: R | null, options?: ApiOptions): Observable<T> {
    return this.http.patch<T>(this.buildUrl(endpoint), body, options);
  }

  /**
   * DELETE Request
   * @template T The expected response type
   */
  delete<T>(endpoint: string, options?: ApiOptions): Observable<T> {
    return this.http.delete<T>(this.buildUrl(endpoint), options);
  }
}
