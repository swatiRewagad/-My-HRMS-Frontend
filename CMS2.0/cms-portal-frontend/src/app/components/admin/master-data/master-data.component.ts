import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MasterDataService, CategoryMaster, DepartmentRoutingRule } from '../../../services/master-data.service';

@Component({
  selector: 'app-master-data',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './master-data.component.html',
  styleUrl: './master-data.component.scss'
})
export class MasterDataComponent implements OnInit {
  private router = inject(Router);
  private masterService = inject(MasterDataService);

  activeTab = signal<'categories' | 'routing'>('categories');

  // Categories
  categories = signal<CategoryMaster[]>([]);
  loadingCategories = signal(false);
  showCategoryForm = signal(false);
  editingCategory = signal<CategoryMaster | null>(null);
  categoryForm = { categoryName: '', subCategory: '', schemeVersion: 'RBIOS_2026', entityType: 'RBIO', active: true, sortOrder: 0 };

  // Routing Rules
  routingRules = signal<DepartmentRoutingRule[]>([]);
  loadingRouting = signal(false);
  showRoutingForm = signal(false);
  editingRule = signal<DepartmentRoutingRule | null>(null);
  routingForm = { entityName: '', department: 'CEPC', targetOffice: '', registrationStatus: 'ACTIVE', active: true };

  // Filters
  schemeFilter = '';
  entityTypeFilter = '';
  departmentFilter = '';

  ngOnInit() {
    this.loadCategories();
    this.loadRoutingRules();
  }

  loadCategories() {
    this.loadingCategories.set(true);
    this.masterService.getCategories(this.schemeFilter, this.entityTypeFilter).subscribe(data => {
      this.categories.set(data);
      this.loadingCategories.set(false);
    });
  }

  loadRoutingRules() {
    this.loadingRouting.set(true);
    this.masterService.getRoutingRules(this.departmentFilter).subscribe(data => {
      this.routingRules.set(data);
      this.loadingRouting.set(false);
    });
  }

  // Category CRUD
  openNewCategory() {
    this.editingCategory.set(null);
    this.categoryForm = { categoryName: '', subCategory: '', schemeVersion: 'RBIOS_2026', entityType: 'RBIO', active: true, sortOrder: 0 };
    this.showCategoryForm.set(true);
  }

  editCategory(cat: CategoryMaster) {
    this.editingCategory.set(cat);
    this.categoryForm = { categoryName: cat.categoryName, subCategory: cat.subCategory, schemeVersion: cat.schemeVersion, entityType: cat.entityType, active: cat.active, sortOrder: cat.sortOrder };
    this.showCategoryForm.set(true);
  }

  saveCategory() {
    const editing = this.editingCategory();
    if (editing) {
      this.masterService.updateCategory(editing.id, this.categoryForm).subscribe(() => {
        this.showCategoryForm.set(false);
        this.loadCategories();
      });
    } else {
      this.masterService.createCategory(this.categoryForm).subscribe(() => {
        this.showCategoryForm.set(false);
        this.loadCategories();
      });
    }
  }

  deleteCategory(id: number) {
    if (confirm('Delete this category?')) {
      this.masterService.deleteCategory(id).subscribe(() => this.loadCategories());
    }
  }

  cancelCategoryForm() {
    this.showCategoryForm.set(false);
  }

  // Routing CRUD
  openNewRule() {
    this.editingRule.set(null);
    this.routingForm = { entityName: '', department: 'CEPC', targetOffice: '', registrationStatus: 'ACTIVE', active: true };
    this.showRoutingForm.set(true);
  }

  editRule(rule: DepartmentRoutingRule) {
    this.editingRule.set(rule);
    this.routingForm = { entityName: rule.entityName, department: rule.department, targetOffice: rule.targetOffice, registrationStatus: rule.registrationStatus, active: rule.active };
    this.showRoutingForm.set(true);
  }

  saveRule() {
    const editing = this.editingRule();
    if (editing) {
      this.masterService.updateRoutingRule(editing.id, this.routingForm).subscribe(() => {
        this.showRoutingForm.set(false);
        this.loadRoutingRules();
      });
    } else {
      this.masterService.createRoutingRule(this.routingForm).subscribe(() => {
        this.showRoutingForm.set(false);
        this.loadRoutingRules();
      });
    }
  }

  deleteRule(id: number) {
    if (confirm('Delete this routing rule?')) {
      this.masterService.deleteRoutingRule(id).subscribe(() => this.loadRoutingRules());
    }
  }

  cancelRoutingForm() {
    this.showRoutingForm.set(false);
  }

  goBack() {
    this.router.navigate(['/admin/dashboard']);
  }
}
