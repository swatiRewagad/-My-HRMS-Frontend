import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SpeechButtonComponent } from '../../../../../shared/speech-button/speech-button.component';
import { TranslateOrPipe } from '../../../../../pipes/translate-or.pipe';

/** One row of the shared "All Comments" feed — the same shape as the parent's assessmentComments signal. */
export interface CommentEntry {
  id: number;
  author: string;
  target: string;
  complaintNumber: string;
  initials: string;
  time?: string;
  text: string;
  color: string;
  role?: string;
  noRecordNumber: string;
  createdAt?: string;
}

/**
 * The "All Comments" feed + "Add Comment" box duplicated near-identically across the Assessment, Forward
 * and Final Decision tabs in the original monolith. The three call sites differ only in a handful of
 * cosmetic/validation flags, exposed below as inputs; the comment list and text itself stay owned by the
 * parent (CepcComplaintDetailsView), since the feed is genuinely shared across tabs rather than per-tab —
 * typing a comment on one tab and switching to another shows the same draft text and the same feed.
 */
@Component({
  selector: 'app-cepc-complaint-comments',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, TranslateOrPipe],
  templateUrl: './complaint-comments.component.html',
  styleUrl: './complaint-comments.component.scss',
})
export class ComplaintCommentsComponent {
  @Input() comments: CommentEntry[] = [];
  @Input() commentText = '';
  @Output() commentTextChange = new EventEmitter<string>();

  /** Forward hides this entirely for the OFFICE target, which captures its own reason/comments fields instead. */
  @Input() showAddComment = true;
  /** Final Decision swaps this to "Reopen Comments" while Reopen is the selected action. */
  @Input() addCommentLabel = 'Comments';
  /** Forward's copy has a bookmark button the other two don't. */
  @Input() showBookmarkButton = false;
  /** Final Decision's copy has a pi-expand icon next to the label; Forward and Assessment don't. */
  @Input() showExpandIcon = false;
  /** Forward's copy shows an explicit "No comments" row when the feed is empty; the others render nothing. */
  @Input() showEmptyState = false;
  /** Final Decision's copy shows a max-length field error under the textarea. */
  @Input() fieldError: string | null = null;

  expandedComments = new Set<number>();

  onCommentTextChange(value: string) {
    this.commentText = value;
    this.commentTextChange.emit(value);
  }

  toggleCommentExpand(commentId: number) {
    if (this.expandedComments.has(commentId)) {
      this.expandedComments.delete(commentId);
    } else {
      this.expandedComments.add(commentId);
    }
  }

  formatCommentDate(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    const day = d.getDate().toString().padStart(2, '0');
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const mon = months[d.getMonth()];
    const year = d.getFullYear();
    const hrs = d.getHours().toString().padStart(2, '0');
    const mins = d.getMinutes().toString().padStart(2, '0');
    return `${day} ${mon} ${year}, ${hrs}:${mins}`;
  }
}
