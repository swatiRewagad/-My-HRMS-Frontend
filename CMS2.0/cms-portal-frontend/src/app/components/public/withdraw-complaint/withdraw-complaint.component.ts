import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { TranslatePipe } from '../../../pipes/translate.pipe';

@Component({
  selector: 'app-withdraw-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './withdraw-complaint.component.html',
  styleUrl: './withdraw-complaint.component.scss'
})
export class WithdrawComplaintComponent implements OnInit {

  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  loading = signal(false);
  complaintId = '';
  error = signal('');

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.router.navigate(['/public/withdraw', id]);
    }
  }

  searchComplaint() {
    if (!this.complaintId.trim()) {
      this.error.set('Please enter your complaint reference number.');
      return;
    }
    this.loading.set(true);
    this.error.set('');

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintId.trim()}`).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/public/withdraw', this.complaintId.trim()]);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('No complaint found with this reference number, or it is not eligible for withdrawal.');
      }
    });
  }

  goBack() {
    this.router.navigate(['/public/history']);
  }
}
