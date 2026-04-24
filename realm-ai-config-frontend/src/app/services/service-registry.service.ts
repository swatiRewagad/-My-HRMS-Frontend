import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface RegisteredServiceDto {
  id: number;
  name: string;
  slug: string;
  baseUrl: string;
  version: string;
  description: string;
  category: string;
  authType: string;
  healthCheckEndpoint: string;
  ownerName: string;
  ownerEmail: string;
  tags: string;
  status: string;
  registeredAt: string;
}

export interface ServiceRegistryStats {
  totalServices: number;
  activeServices: number;
  inactiveServices: number;
  deprecatedServices: number;
  services: RegisteredServiceDto[];
}

export interface RegisterServicePayload {
  name: string;
  baseUrl: string;
  slug?: string;
  category?: string;
  version?: string;
  description?: string;
  healthCheckEndpoint?: string;
  authType?: string;
  ownerName?: string;
  ownerEmail?: string;
  tags?: string;
  status?: string;
}

@Injectable({ providedIn: 'root' })
export class ServiceRegistryService {
  private apiUrl = `${environment.apiUrl}/service-registry`;

  constructor(private http: HttpClient) {}

  getRegistry(search?: string, category?: string, status?: string): Observable<ServiceRegistryStats> {
    let params = new HttpParams();
    if (search) params = params.set('search', search);
    if (category) params = params.set('category', category);
    if (status) params = params.set('status', status);
    return this.http.get<ServiceRegistryStats>(this.apiUrl, { params });
  }

  register(payload: RegisterServicePayload): Observable<RegisteredServiceDto> {
    return this.http.post<RegisteredServiceDto>(this.apiUrl, payload);
  }

  update(id: number, payload: RegisterServicePayload): Observable<RegisteredServiceDto> {
    return this.http.put<RegisteredServiceDto>(`${this.apiUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
