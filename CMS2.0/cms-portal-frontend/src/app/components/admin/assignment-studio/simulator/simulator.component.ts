import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AssignmentStudioService, AttributeDefinition } from '../../../../services/assignment-studio.service';

interface TestCase {
  [key: string]: any;
}

@Component({
  selector: 'app-simulator',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './simulator.component.html',
  styleUrl: './simulator.component.scss'
})
export class SimulatorComponent implements OnInit {

  attributes = signal<AttributeDefinition[]>([]);
  rulesetId = signal(0);
  versionId = signal(0);
  testCases = signal<TestCase[]>([]);
  results = signal<any>(null);
  running = signal(false);
  errorMessage = signal('');
  jsonInput = signal('');
  inputMode = signal<'form' | 'json'>('form');

  constructor(
    private route: ActivatedRoute,
    private studioService: AssignmentStudioService
  ) {}

  ngOnInit() {
    const params = this.route.parent?.snapshot.paramMap;
    this.rulesetId.set(+(params?.get('rulesetId') || this.route.snapshot.queryParamMap.get('rulesetId') || 0));
    this.versionId.set(+(params?.get('versionId') || this.route.snapshot.queryParamMap.get('versionId') || 0));
    this.loadAttributes();
  }

  private loadAttributes() {
    this.studioService.getAttributes().subscribe({
      next: (attrs) => {
        this.attributes.set(attrs);
        if (this.testCases().length === 0) this.addTestCase();
      },
      error: () => this.errorMessage.set('Failed to load attributes')
    });
  }

  addTestCase() {
    const blank: TestCase = {};
    for (const attr of this.attributes()) {
      blank[attr.code] = '';
    }
    this.testCases.set([...this.testCases(), blank]);
  }

  removeTestCase(index: number) {
    const cases = [...this.testCases()];
    cases.splice(index, 1);
    this.testCases.set(cases);
  }

  updateTestCase(index: number, attrCode: string, value: string) {
    const cases = [...this.testCases()];
    cases[index] = { ...cases[index], [attrCode]: value };
    this.testCases.set(cases);
  }

  toggleInputMode() {
    if (this.inputMode() === 'form') {
      this.jsonInput.set(JSON.stringify(this.testCases(), null, 2));
      this.inputMode.set('json');
    } else {
      try {
        const parsed = JSON.parse(this.jsonInput());
        if (!Array.isArray(parsed)) throw new Error('Must be an array');
        this.testCases.set(parsed);
        this.inputMode.set('form');
        this.errorMessage.set('');
      } catch (e: any) {
        this.errorMessage.set('Invalid JSON: ' + e.message);
      }
    }
  }

  runSimulation() {
    if (this.rulesetId() === 0 || this.versionId() === 0) {
      this.errorMessage.set('Please specify ruleset and version IDs via query params (?rulesetId=X&versionId=Y)');
      return;
    }

    let cases: any[];
    if (this.inputMode() === 'json') {
      try {
        cases = JSON.parse(this.jsonInput());
        if (!Array.isArray(cases)) throw new Error('Must be an array');
      } catch (e: any) {
        this.errorMessage.set('Invalid JSON: ' + e.message);
        return;
      }
    } else {
      cases = this.testCases().map(tc => {
        const clean: any = {};
        for (const [k, v] of Object.entries(tc)) {
          if (v !== '' && v != null) {
            clean[k] = isNaN(Number(v)) ? v : Number(v);
          }
        }
        return clean;
      });
    }

    if (cases.length === 0) {
      this.errorMessage.set('Add at least one test case');
      return;
    }

    this.running.set(true);
    this.errorMessage.set('');
    this.results.set(null);

    this.studioService.simulate(this.rulesetId(), this.versionId(), cases).subscribe({
      next: (resp) => {
        this.results.set(resp);
        this.running.set(false);
      },
      error: (err) => {
        this.running.set(false);
        this.errorMessage.set(err.error?.detail || 'Simulation failed');
      }
    });
  }

  loadSampleData() {
    const samples: TestCase[] = [
      { claimAmount: '500000', complaintCategory: 'CREDIT_CARD', state: 'MH', channel: 'EMAIL' },
      { claimAmount: '2500000', complaintCategory: 'DEPOSIT', state: 'KA', channel: 'PORTAL' },
      { claimAmount: '150000', complaintCategory: 'INSURANCE', state: 'DL', channel: 'PHYSICAL_LETTER' },
    ];
    this.testCases.set(samples);
  }
}
