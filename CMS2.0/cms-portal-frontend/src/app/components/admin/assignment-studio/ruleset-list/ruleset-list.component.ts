import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AssignmentStudioService } from '../../../../services/assignment-studio.service';

@Component({
  selector: 'app-ruleset-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ruleset-list.component.html',
  styleUrl: './ruleset-list.component.scss'
})
export class RulesetListComponent implements OnInit {

  rulesets = signal<any[]>([]);
  loading = signal(false);

  constructor(
    private studioService: AssignmentStudioService,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadRulesets();
  }

  loadRulesets() {
    this.loading.set(true);
    this.studioService.getRulesets().subscribe({
      next: (data) => {
        this.rulesets.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openEditor(rulesetId: number, versionId: number | null) {
    if (versionId) {
      this.router.navigate(['/admin/assignment-studio', rulesetId, 'versions', versionId, 'edit']);
      return;
    }
    this.loading.set(true);
    this.studioService.createVersion(rulesetId).subscribe({
      next: (version) => {
        this.loading.set(false);
        this.router.navigate(['/admin/assignment-studio', rulesetId, 'versions', version.id, 'edit']);
      },
      error: () => this.loading.set(false)
    });
  }
}
