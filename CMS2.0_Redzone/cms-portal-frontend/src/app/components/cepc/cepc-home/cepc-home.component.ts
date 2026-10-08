import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SessionTimeoutComponent } from '../../../shared/session-timeout/session-timeout.component';
import { CepcHeaderComponent } from '../cepc-header/cepc-header.component';
import { CepcSidebarComponent } from '../cepc-sidebar/cepc-sidebar.component';
import { CepcDashboardComponent } from '../cepc-dashboard/cepc-dashboard.component';

@Component({
  selector: 'app-cepc-home',
  standalone: true,
  imports: [CommonModule, FormsModule, SessionTimeoutComponent, CepcHeaderComponent, CepcDashboardComponent, CepcSidebarComponent],
  templateUrl: './cepc-home.component.html',
  styleUrl: './cepc-home.component.scss'
})
export class CepcHomeComponent {

}
