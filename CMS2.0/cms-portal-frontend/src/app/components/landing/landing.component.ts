import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { EligibilityQuestionnaireComponent } from '../eligibility-questionnaire/eligibility-questionnaire.component';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, EligibilityQuestionnaireComponent],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss'
})
export class LandingComponent {

  constructor(private router: Router) {}

  showEligibility = signal(false);

  onEligible() {
    this.router.navigate(['/file-complaint']);
  }

  navigateToTrack() {
    this.router.navigate(['/track']);
  }

  navigateToOfficer() {
    this.router.navigate(['/officer']);
  }

  navigateToSearch() {
    this.router.navigate(['/search']);
  }

  navigateToAdmin() {
    this.router.navigate(['/admin/dashboard']);
  }

  navigateToEmailSyndication() {
    this.router.navigate(['/email-syndication']);
  }

  navigateToEmailSimulator() {
    this.router.navigate(['/email-syndication/simulator']);
  }

  navigateToCrpcAdmin() {
    this.router.navigate(['/email-syndication']);
  }

  navigateToCrpcLogin() {
    this.router.navigate(['/crpc/login']);
  }

  navigateToStaffPortal() {
    this.router.navigate(['/staff/login']);
  }

  navigateToPublicPortal() {
    this.router.navigate(['/public']);
  }

  navigateToPortal2() {
    this.router.navigate(['/public/eligibility-wizard']);
  }

  navigateToKeycloakAdmin() {
    window.open('http://localhost:8180/admin', '_blank');
  }

  navigateToRulesConfig() {
    this.router.navigate(['/admin/rules']);
  }

  navigateToExtractionRules() {
    this.router.navigate(['/admin/extraction-rules']);
  }
}
