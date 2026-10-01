/**
 * Configuration contract for the shared staff comment thread.
 *
 * NEVER render this component on a citizen-facing route. The server refuses these endpoints for a
 * citizen identity, so the omission is defence in depth rather than the control itself — but
 * `public/complaint-detail` and `public/complaint-history` have no comment affordance at all today and
 * that is deliberate, not an oversight to be corrected.
 */

/**
 * Who may READ a comment. Distinct from authorship, which every prior thread in this codebase modelled
 * instead: ComplaintQueryMessage carries `authorSide` and ReassignmentClarification carries
 * `addedBySide`, and neither says anything about who is allowed to see the row.
 */
export type CommentVisibility =
  /** Author only. A note to self, invisible to every other user including their own supervisor. */
  | 'PRIVATE'
  /** The author plus the named roles and user ids. */
  | 'RESTRICTED'
  /**
   * Every authenticated STAFF user. Never the complainant — "public" here means public within RBI,
   * which is why the label the user sees says "All staff" and not "Public".
   */
  | 'PUBLIC';

export interface CommentAuthor {
  userId: string;
  name: string;
  role: string;
}

export interface Comment {
  id: number;

  /** Null for a top-level comment. Replies are one level deep only; a reply cannot be replied to. */
  parentId: number | null;

  body: string;
  visibility: CommentVisibility;

  /** Only populated when visibility === 'RESTRICTED'. */
  restrictedToRoles: string[];
  restrictedToUserIds: string[];

  author: CommentAuthor;
  createdAt: string;
  updatedAt: string | null;

  /**
   * Whether THIS caller may edit. Server-computed, like the internal-notes panel's flag: a client with
   * a skewed clock must not offer an edit box for a comment the server has already locked.
   */
  editable: boolean;

  /** Replies, oldest first. Server-nested so the client does no parent/child stitching of its own. */
  replies: Comment[];
}

/** A role or named user the composer offers as a RESTRICTED target. */
export interface CommentAudienceOption {
  value: string;
  labelKey?: string;
  label?: string;
}
