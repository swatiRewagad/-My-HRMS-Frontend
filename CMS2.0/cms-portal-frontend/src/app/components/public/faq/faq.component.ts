import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../pipes/translate.pipe';

interface FaqItem {
  question: string;
  answer: string;
  category: string;
  open: boolean;
}

@Component({
  selector: 'app-faq',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './faq.component.html',
  styleUrl: './faq.component.scss',
})
export class FaqComponent {
  searchQuery = '';
  selectedCategory = 'ALL';

  categories = [
    'ALL',
    'Scheme Overview & Structure',
    'Institutions & Access Points',
    'Complaint Grounds & Eligibility',
    'Complaint Handling & Resolution',
    'Appeals & Further Recourse',
    'Miscellaneous',
  ];

  faqs: FaqItem[] = [
    {
      question: 'What is RB-IOS, 2026?',
      answer: 'The Reserve Bank – Integrated Ombudsman Scheme, 2026 (RB-IOS, 2026 / the Scheme) is a cost-free, expeditious and non-adversarial alternate grievance redress mechanism for customer complaints involving deficiency in service by Regulated Entities covered under the Scheme. It comes into force with effect from July 1, 2026. RB-IOS, 2026 replaces the Reserve Bank - Integrated Ombudsman Scheme, 2021. Complaints received before July 1, 2026; appeals arising from decisions under RB-IOS, 2021; and execution of awards issued thereunder will continue to be governed by RB-IOS, 2021 and related RBI instructions.',
      category: 'Scheme Overview & Structure',
      open: true,
    },
    {
      question: 'What is the RBI Alternate Grievance Redress (AGR) Framework?',
      answer: 'The AGR Framework of the Reserve Bank comprises the Offices of RBI Ombudsman, the Centralised Receipt and Processing Centre (CRPC), Consumer Education and Protection Cells (CEPCs), and the Consumer Education and Protection Department (CEPD). CEPD provides assistance to the Appellate Authority (AA) and processes the appeal cases.',
      category: 'Scheme Overview & Structure',
      open: true,
    },
    {
      question: 'Are all Regulated Entities covered under RB-IOS, 2026?',
      answer: 'No. RB-IOS, 2026 applies only to the categories of Regulated Entities specifically covered under the Scheme. The major categories covered are banks, certain NBFCs, non-bank Prepaid Payment Instrument issuers, and Credit Information Companies. The detailed coverage is given in Question 13.',
      category: 'Institutions & Access Points',
      open: true,
    },
    {
      question: 'Who is an RBI Ombudsman?',
      answer: 'The RBI Ombudsman is a senior official appointed by the Reserve Bank of India to redress customer complaints against deficiency in certain banking and financial services covered under the grounds of complaint specified in the Scheme.',
      category: 'Scheme Overview & Structure',
      open: false,
    },
    {
      question: 'What is "deficiency in service"?',
      answer: 'Deficiency in service means a shortcoming or an inadequacy in any financial service which a Regulated Entity is required to provide under any law, or under the directions or instructions or guidelines issued by the Reserve Bank or any other regulatory body, or as per the commitment made by the RE to the customer.',
      category: 'Complaint Grounds & Eligibility',
      open: false,
    },
    {
      question: 'Who is an RBI Deputy Ombudsman?',
      answer: 'The RBI Deputy Ombudsman is an officer appointed by the Reserve Bank to assist the Ombudsman in handling complaints and related matters under the Scheme. The Deputy Ombudsman exercises powers delegated by the Ombudsman.',
      category: 'Scheme Overview & Structure',
      open: false,
    },
    {
      question: 'How can I file a complaint under RB-IOS, 2026?',
      answer: 'You can file a complaint online through the CMS portal, by sending a physical letter to CRPC, or by calling the helpline number 14448. The online mode is recommended for faster processing.',
      category: 'Complaint Handling & Resolution',
      open: false,
    },
    {
      question: 'What is the time limit for filing a complaint?',
      answer: 'A complaint can be filed under the Scheme if the complainant has first approached the Regulated Entity and the RE has rejected the complaint or has not resolved it within 30 days of receipt. The complaint to the Ombudsman should be filed within one year from the date of the RE\'s reply or within one year and 30 days from the date of complaint to the RE if no reply is received.',
      category: 'Complaint Grounds & Eligibility',
      open: false,
    },
    {
      question: 'Can I appeal against the decision of the Ombudsman?',
      answer: 'Yes. If you are not satisfied with the decision of the Ombudsman, you can file an appeal before the Appellate Authority within 30 days from the date of receipt of the decision. The Appellate Authority is the Executive Director in charge of the Consumer Education and Protection Department of the Reserve Bank.',
      category: 'Appeals & Further Recourse',
      open: false,
    },
    {
      question: 'Is there any fee for filing a complaint?',
      answer: 'No. Filing a complaint under the Reserve Bank – Integrated Ombudsman Scheme is completely free of charge. There is no fee at any stage of the complaint resolution process.',
      category: 'Miscellaneous',
      open: false,
    },
  ];

  get filteredFaqs(): FaqItem[] {
    let filtered = this.faqs;

    if (this.selectedCategory !== 'ALL') {
      filtered = filtered.filter(f => f.category === this.selectedCategory);
    }

    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      filtered = filtered.filter(
        f => f.question.toLowerCase().includes(query) || f.answer.toLowerCase().includes(query)
      );
    }

    return filtered;
  }

  selectCategory(category: string) {
    this.selectedCategory = category;
  }

  toggleFaq(index: number) {
    const faq = this.filteredFaqs[index];
    const originalIndex = this.faqs.indexOf(faq);
    if (originalIndex > -1) {
      this.faqs[originalIndex].open = !this.faqs[originalIndex].open;
    }
  }
}
