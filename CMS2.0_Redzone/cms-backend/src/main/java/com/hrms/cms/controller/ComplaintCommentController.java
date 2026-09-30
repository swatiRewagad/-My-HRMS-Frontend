package com.hrms.cms.controller;

import com.hrms.cms.entity.ComplaintComment;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.ComplaintCommentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Threaded staff comments on a complaint, with PRIVATE / RESTRICTED / PUBLIC readership.
 *
 * <p>A SEPARATE CONTROLLER, not extra routes on {@link ComplaintQueryController}. That class's API
 * shape is pinned by {@code e2e/re-portal/query-threads.spec.ts} and is edited by other concurrent
 * sessions; a new file keeps this feature's routes from colliding with theirs, and keeps the
 * entity-scoped RE surface (which comments deliberately exclude) from sharing a helper with the
 * staff-only one.
 *
 * <p>NO {@code @PreAuthorize}. {@code SecurityConfig} is not the gate for this path and 31 existing
 * {@code @PreAuthorize} annotations in this codebase are dead, so one here would be false
 * confidence. Access control is imperative in {@link ComplaintCommentService}, which refuses any
 * caller that is not recognised staff — including a citizen — and never reveals a comment the caller
 * may not read. This controller validates the request body and maps exceptions to status codes; it
 * makes no authorisation decision of its own.
 */
@RestController
@RequestMapping("/api/v1/complaint-comments")
@RequiredArgsConstructor
@Slf4j
public class ComplaintCommentController {

    private final ComplaintCommentService commentService;
    private final RequestIdentityResolver identityResolver;

    /**
     * The caller's visible thread. Replies are nested under their parent, and a reply whose parent is
     * invisible is dropped with it — a reply inherits the parent's tier, so this cannot hide a comment
     * the caller was entitled to see.
     */
    @GetMapping("/complaint/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> listThread(@PathVariable String complaintNumber,
                                                          HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            List<ComplaintComment> visible = commentService.getThread(complaintNumber, identity);

            Map<Long, Map<String, Object>> topLevel = new LinkedHashMap<>();
            for (ComplaintComment comment : visible) {
                if (!comment.isReply()) {
                    Map<String, Object> rendered = render(comment, identity);
                    rendered.put("replies", new ArrayList<Map<String, Object>>());
                    topLevel.put(comment.getId(), rendered);
                }
            }

            int orphanedReplies = 0;
            for (ComplaintComment comment : visible) {
                if (!comment.isReply()) {
                    continue;
                }
                Map<String, Object> parent = topLevel.get(comment.getParentId());
                if (parent == null) {
                    orphanedReplies++;
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> replies = (List<Map<String, Object>>) parent.get("replies");
                replies.add(render(comment, identity));
            }
            if (orphanedReplies > 0) {
                log.warn("Dropped {} reply/replies with no visible parent on complaint {}",
                        orphanedReplies, complaintNumber);
            }

            return ResponseEntity.ok(Map.of("success", true,
                    "count", topLevel.size(), "comments", List.copyOf(topLevel.values())));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    /**
     * Posts a comment, or a reply when the body carries {@code parentId} — the shape
     * {@code ComplaintCommentService.post()} in the Angular client sends for both. A reply routed here
     * takes the SAME service path as {@code /{parentId}/replies}, so the tier the client supplied
     * alongside it is ignored in favour of the parent's inherited one.
     */
    @PostMapping("/complaint/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable String complaintNumber,
                                                          @RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            Long parentId = parentId(body.get("parentId"));
            ComplaintComment comment = parentId != null
                    ? commentService.addReply(parentId, identity, requireBody(body.get("body")))
                    : commentService.addComment(
                            complaintNumber, identity,
                            requireBody(body.get("body")),
                            str(body.get("visibility")),
                            csv(body.get("restrictedToRoles")),
                            csv(body.get("restrictedToUserIds")));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true, "commentId", comment.getId(),
                    "visibility", comment.getVisibility()));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/{parentId}/replies")
    public ResponseEntity<Map<String, Object>> addReply(@PathVariable Long parentId,
                                                        @RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            ComplaintComment reply = commentService.addReply(
                    parentId, identity, requireBody(body.get("body")));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true, "commentId", reply.getId(),
                    "parentId", reply.getParentId(),
                    // Echoed because the server, not the client, chose it — it is inherited.
                    "visibility", reply.getVisibility()));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    /** Body only. The tier is immutable after posting — see the service. */
    @PutMapping("/{commentId}")
    public ResponseEntity<Map<String, Object>> editComment(@PathVariable Long commentId,
                                                           @RequestBody Map<String, Object> body,
                                                           HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            ComplaintComment comment = commentService.editComment(
                    commentId, identity, requireBody(body.get("body")));
            return ResponseEntity.ok(Map.of("success", true,
                    "commentId", comment.getId(), "editCount", comment.getEditCount()));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    /**
     * Shaped to the {@code Comment} interface in
     * {@code components/shared/comment-thread/comment-thread.types.ts}: a nested {@code author}, the
     * restriction lists as arrays, and server-computed {@code editable}. That consumer was built
     * against this contract in a parallel session, so it is the contract rather than a preference.
     */
    private Map<String, Object> render(ComplaintComment comment, RequestIdentity identity) {
        Map<String, Object> author = new LinkedHashMap<>();
        author.put("userId", comment.getAuthorUserId());
        author.put("name", comment.getAuthorName());
        author.put("role", comment.getAuthorRole() == null ? "" : comment.getAuthorRole());

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", comment.getId());
        item.put("parentId", comment.getParentId());
        item.put("body", comment.getBody());
        item.put("visibility", comment.getVisibility());
        // Only ever sent to a caller who already passed canRead, so naming the rest of the audience
        // tells a reader nothing they are not already inside.
        item.put("restrictedToRoles", asList(comment.getRestrictedToRoles()));
        item.put("restrictedToUserIds", asList(comment.getRestrictedToUserIds()));
        item.put("author", author);
        item.put("createdAt", comment.getCreatedAt().toString());
        item.put("updatedAt", comment.getUpdatedAt() == null ? null : comment.getUpdatedAt().toString());
        item.put("editCount", comment.getEditCount());
        // Whether the pencil shows is the server's call, not the browser's.
        item.put("editable", identity.getUserId().equals(comment.getAuthorUserId()));
        return item;
    }

    private List<String> asList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private Long parentId(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("parentId must be a comment id");
        }
    }

    private String requireBody(Object value) {
        String text = str(value);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Comment body is required");
        }
        return text;
    }

    /** Accepts either a comma-separated string or a JSON array, and normalises to the stored CSV. */
    private String csv(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull)
                    .map(Object::toString).map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .reduce((a, b) -> a + "," + b).orElse(null);
        }
        return str(value);
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private ResponseEntity<Map<String, Object>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false, "message", "Could not establish caller identity"));
    }

    private ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> forbidden(Exception e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false, "message", e.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false, "message", e.getMessage()));
    }
}
