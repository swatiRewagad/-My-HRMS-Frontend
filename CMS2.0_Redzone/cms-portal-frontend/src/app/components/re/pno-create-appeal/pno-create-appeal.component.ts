import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { TranslatePipe } from '../../../pipes/translate.pipe';

/**
 * PNO entry point into appeal registration (story 14).
 *
 * WHY THIS IS A SEPARATE, THIN COMPONENT: the Register form itself is AaRegisterComponent, and both
 * channels must post to the same endpoint so the mandatory-field, eligibility and entity-scope checks
 * cannot diverge between them. This screen only decides whether to OFFER the action and then hands over,
 * rather than duplicating a second copy of the form.
 *
 * ALL THREE DECISIONS ARE THE SERVER'S:
 *   - whether the parent is appeal-eligible (closed or reopened),
 *   - whether the clause is appealable BY AN ENTITY at all (an entity may appeal only 15(1)(b);
 *     everything else is a representation),
 *   - whether this parent belongs to the caller's own regulated entity.
 * The register-form endpoint answers all three from the caller's token, so this component asks and
 * renders the answer. It deliberately does not re-derive appealability in the browser: a client-side
 * copy of that rule would drift from CLOSURE_CLAUSE_MASTER and could offer an appeal the law does not.
 *
 * ROUTING NOTE (reported, not worked around): there is no route to this component yet. app.routes.ts is
 * shared and this session must not edit it. See the S2A report for the single entry required.
 */
@Component({
  selector: 'app-pno-create-appeal',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './pno-create-appeal.component.html',
  styleUrl: './pno-create-appeal.component.scss'
})
export class PnoCreateAppealComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  complaintNumber = signal('');
  loading = signal(true);
  loadError = signal(false);
  errorMessageKey = signal<string | null>(null);

  appealEligible = signal(false);
  /** Set when the server says this parent is not appealable, so the reason is shown, not guessed. */
  ineligibleKey = signal<string | null>(null);
  closureClause = signal<string | null>(null);
  /** True when the clause permits an ENTITY to appeal; otherwise the action is a Representation. */
  entityMayAppeal = signal(false);

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/re-portal/login']);
      return;
    }

    const number = this.route.snapshot.paramMap.get('complaintNumber') ?? '';
    this.complaintNumber.set(number);
    if (!number) {
      this.loading.set(false);
      this.loadError.set(true);
      this.errorMessageKey.set('aa.register.error_not_permitted');
      return;
    }
    this.loadEligibility(number);
  }

  private loadEligibility(complaintNumber: string) {
    this.loading.set(true);
    this.loadError.set(false);
    this.errorMessageKey.set(null);

    const url = `${environment.apiBaseUrl}/api/v1/aa/parent-complaints/${complaintNumber}/register-form`;

    this.http.get<any>(url).subscribe({
      next: (res) => {
        const data = res?.data ?? {};
        this.appealEligible.set(data.appealEligible === true);
        this.ineligibleKey.set(data.appealEligibilityKey ?? null);
        this.closureClause.set(data.closureClause ?? null);
        this.loading.set(false);
        if (data.closureClause) {
          this.loadClauseAppealability(data.closureClause);
        }
      },
      error: (err) => {
        // A 404 here is deliberate on the server for a parent outside the caller's entity: a 403 would
        // confirm the complaint exists and turn this screen into an oracle for other banks' numbers.
        this.loading.set(false);
        this.loadError.set(true);
        this.errorMessageKey.set(err?.error?.messageKey ?? 'aa.register.error_not_permitted');
      }
    });
  }

  /** Reads appealability from CLOSURE_CLAUSE_MASTER rather than hardcoding which clauses qualify. */
  private loadClauseAppealability(clauseCode: string) {
    const url = `${environment.apiBaseUrl}/api/v1/aa/parent-complaints/masters/closure-clauses`;
    this.http.get<any>(url).subscribe({
      next: (res) => {
        const match = (res?.data ?? []).find((c: any) => c.clauseCode === clauseCode);
        this.entityMayAppeal.set(match?.appealableByEntity === true);
      },
      error: () => this.entityMayAppeal.set(false)
    });
  }

  /** Hands over to the shared Register form; the server re-checks everything on POST. */
  proceed() {
    if (!this.appealEligible()) {
      return;
    }
    this.router.navigate(['/aa/register', this.complaintNumber()]);
  }

  goBack() {
    this.router.navigate(['/re-portal/complaints', this.complaintNumber()]);
  }

  retry() {
    if (this.complaintNumber()) {
      this.loadEligibility(this.complaintNumber());
    }
  }
}
