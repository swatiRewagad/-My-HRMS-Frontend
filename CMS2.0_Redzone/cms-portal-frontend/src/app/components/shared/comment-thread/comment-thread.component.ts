import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { ComplaintCommentService } from '../../../services/complaint-comment.service';
import { Comment, CommentAudienceOption, CommentVisibility } from './comment-thread.types';

/**
 * The one staff comment thread.
 *
 * <h2>Visibility is readership, and it is the point</h2>
 * Three comment-like tables already existed here and none of them modelled who may READ a row:
 * ComplaintQueryMessage and ReassignmentClarification record an author side, and ComplaintInternalNote
 * is hardcoded to one audience (the owning entity's RE staff). So a private aside and an
 * everyone-can-see remark were indistinguishable, and officers worked around it by not writing things
 * down. PRIVATE / RESTRICTED / PUBLIC are now a column, enforced server-side.
 *
 * <h2>"Public" means public to staff</h2>
 * Never to the complainant, at any tier. The server refuses the endpoint for a citizen identity, so
 * this component's absence from the citizen routes is a second line rather than the control — but the
 * PUBLIC option deliberately reads "All RBI staff" in the composer, because a label saying "Public"
 * invites an officer to write as if the complainant will read it.
 *
 * <h2>Replies are one level deep</h2>
 * Nesting is server-supplied via {@link Comment#replies}; this component does no parent/child
 * stitching. A reply cannot be replied to, which keeps the thread readable and is why the reply
 * affordance is absent on a reply.
 */
@Component({
  selector: 'app-comment-thread',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './comment-thread.component.html',
  styleUrl: './comment-thread.component.scss'
})
export class CommentThreadComponent implements OnInit {

  private commentService = inject(ComplaintCommentService);

  readonly complaintNumber = input.required<string>();

  /** Roles the composer offers as RESTRICTED targets. Empty hides the role picker entirely. */
  readonly audienceRoles = input<readonly CommentAudienceOption[]>([]);

  loading = signal(true);
  posting = signal(false);
  error = signal('');
  comments = signal<Comment[]>([]);

  newBody = signal('');
  newVisibility = signal<CommentVisibility>('PUBLIC');
  selectedRoles = signal<string[]>([]);

  replyingTo = signal<number | null>(null);
  replyBody = signal('');

  editingId = signal<number | null>(null);
  editBody = signal('');

  readonly total = computed(() =>
    this.comments().reduce((sum, c) => sum + 1 + c.replies.length, 0));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.commentService.list(this.complaintNumber()).subscribe({
      next: res => {
        this.comments.set(res.comments ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('comments.error.load_failed');
        this.loading.set(false);
      }
    });
  }

  toggleRole(role: string): void {
    this.selectedRoles.update(list =>
      list.includes(role) ? list.filter(r => r !== role) : [...list, role]);
  }

  post(): void {
    if (!this.newBody().trim()) {
      this.error.set('comments.error.body_required');
      return;
    }
    // A RESTRICTED comment with no audience would be indistinguishable from PRIVATE on the server but
    // would read as shared in the UI, so it is refused rather than silently narrowed.
    if (this.newVisibility() === 'RESTRICTED' && this.selectedRoles().length === 0) {
      this.error.set('comments.error.audience_required');
      return;
    }
    this.posting.set(true);
    this.commentService.post(this.complaintNumber(), {
      body: this.newBody(),
      visibility: this.newVisibility(),
      restrictedToRoles: this.newVisibility() === 'RESTRICTED' ? this.selectedRoles() : []
    }).subscribe({
      next: () => {
        this.newBody.set('');
        this.selectedRoles.set([]);
        this.error.set('');
        this.posting.set(false);
        this.load();
      },
      error: err => {
        this.posting.set(false);
        this.error.set(err.error?.message || 'comments.error.post_failed');
      }
    });
  }

  startReply(commentId: number): void {
    this.replyingTo.set(commentId);
    this.replyBody.set('');
  }

  cancelReply(): void {
    this.replyingTo.set(null);
    this.replyBody.set('');
  }

  /**
   * A reply inherits its parent's visibility rather than offering its own picker: a PUBLIC reply under a
   * PRIVATE comment would quote the private text back into the open thread.
   */
  postReply(parent: Comment): void {
    if (!this.replyBody().trim()) {
      this.error.set('comments.error.body_required');
      return;
    }
    this.posting.set(true);
    this.commentService.post(this.complaintNumber(), {
      body: this.replyBody(),
      visibility: parent.visibility,
      restrictedToRoles: parent.restrictedToRoles,
      restrictedToUserIds: parent.restrictedToUserIds,
      parentId: parent.id
    }).subscribe({
      next: () => {
        this.cancelReply();
        this.error.set('');
        this.posting.set(false);
        this.load();
      },
      error: err => {
        this.posting.set(false);
        this.error.set(err.error?.message || 'comments.error.post_failed');
      }
    });
  }

  startEdit(comment: Comment): void {
    this.editingId.set(comment.id);
    this.editBody.set(comment.body);
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.editBody.set('');
  }

  saveEdit(commentId: number): void {
    if (!this.editBody().trim()) {
      this.error.set('comments.error.body_required');
      return;
    }
    this.posting.set(true);
    this.commentService.edit(commentId, this.editBody()).subscribe({
      next: () => {
        this.posting.set(false);
        this.cancelEdit();
        this.load();
      },
      error: err => {
        this.posting.set(false);
        // There is no edit window on a comment — unlike ComplaintInternalNote — so the only refusal the
        // server issues here is 403 for a non-author. Reloading re-reads `editable` so the pencil
        // disappears if this client's view of authorship was stale.
        this.error.set(err.error?.message || 'comments.error.edit_refused');
        this.cancelEdit();
        this.load();
      }
    });
  }

  visibilityLabelKey(visibility: CommentVisibility): string {
    return `comments.visibility.${visibility.toLowerCase()}`;
  }

  initial(name: string): string {
    return (name || '?').charAt(0).toUpperCase();
  }
}
