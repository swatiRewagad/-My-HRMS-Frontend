import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SessionTimeoutComponent } from '../../../shared/session-timeout/session-timeout.component';
import { RbioHeaderComponent } from '../rbio-header/rbio-header.component';
import { RbioSidebarComponent } from '../rbio-sidebar/rbio-sidebar.component';
import { RbioDashboardComponent } from '../rbio-dashboard/rbio-dashboard.component';

@Component({
  selector: 'app-rbio-home',
  standalone: true,
  imports: [CommonModule, FormsModule, SessionTimeoutComponent, RbioHeaderComponent, RbioDashboardComponent, RbioSidebarComponent],
  templateUrl: './rbio-home.component.html',
  styleUrl: './rbio-home.component.scss'
})
export class RbioHomeComponent {

}
