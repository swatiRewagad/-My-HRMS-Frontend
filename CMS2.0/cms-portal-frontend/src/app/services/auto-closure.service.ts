import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface AutoClosureQuestion {
  id: number;
  questionNumber: number;
  questionText: string;
  helpText: string;
  clauseReference: string;
  defaultAnswer: string;
  outcomeOnYes: string;
  outcomeOnNo: string;
}

export interface QuestionResponse {
  questionNumber: number;
  answer: string;
  outcome?: string;
  clauseReference?: string;
}

export interface AutoClosureResult {
  outcome: string;
  clauseReference: string;
  subJudice: boolean;
  generateComplaintNumber: boolean;
  createReRecord: boolean;
}

@Injectable({ providedIn: 'root' })
export class AutoClosureService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/crpc/auto-closure`;

  getQuestions(schemeVersion: string, entityType: string): Observable<AutoClosureQuestion[]> {
    return this.http.get<AutoClosureQuestion[]>(`${this.baseUrl}/questions`, {
      params: { schemeVersion, entityType }
    }).pipe(catchError(() => of([])));
  }

  evaluateResponse(schemeVersion: string, entityType: string, questionNumber: number, answer: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/evaluate`, null, {
      params: { schemeVersion, entityType, questionNumber: questionNumber.toString(), answer }
    });
  }

  evaluateAll(schemeVersion: string, entityType: string, responses: QuestionResponse[]): Observable<AutoClosureResult> {
    return this.http.post<AutoClosureResult>(`${this.baseUrl}/evaluate-all`, responses, {
      params: { schemeVersion, entityType }
    }).pipe(
      catchError(() => of({
        outcome: 'NEW_COMPLAINT',
        clauseReference: '',
        subJudice: false,
        generateComplaintNumber: true,
        createReRecord: true
      }))
    );
  }
}
