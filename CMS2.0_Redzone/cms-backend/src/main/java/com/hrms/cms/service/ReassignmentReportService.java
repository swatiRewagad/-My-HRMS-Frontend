package com.hrms.cms.service;

import com.hrms.cms.entity.EntityUser;
import com.hrms.cms.entity.ReassignmentHistory;
import com.hrms.cms.repository.EntityUserRepository;
import com.hrms.cms.repository.ReassignmentHistoryRepository;
import com.hrms.cms.security.RequestIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * The reassignment history report (UST844).
 *
 * <p>Reads {@code REASSIGNMENT_HISTORY} rather than {@code REASSIGNMENT_REQUESTS}, because the report
 * answers "which records actually changed hands" — counting requests would include rejections and
 * withdrawals that moved nothing and overstate churn.
 *
 * <p>Scoping is delegated to {@link ReassignmentService#resolveScope}, so the report cannot end up
 * with a laxer rule than the write paths. An RE caller sees only their own entity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReassignmentReportService {

    private final ReassignmentHistoryRepository historyRepository;
    private final EntityUserRepository entityUserRepository;
    private final ReassignmentService reassignmentService;

    @Transactional(readOnly = true)
    public Page<ReassignmentHistory> search(RequestIdentity identity, String entityCode,
                                            String fromUserId, String toUserId,
                                            LocalDateTime from, LocalDateTime to,
                                            int page, int size) {
        String scope = reassignmentService.resolveScope(identity, entityCode);
        return historyRepository.search(scope,
                blankToNull(fromUserId), blankToNull(toUserId), from, to,
                PageRequest.of(Math.max(page, 0), reassignmentService.clampPageSize(size)));
    }

    /**
     * Per-officer inbound/outbound totals.
     *
     * <p>Names are resolved from the directory once into a map rather than per row: the history rows
     * already carry the name captured at the time, but an officer renamed since then would appear
     * under two labels in the same summary, which reads as two different people.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(RequestIdentity identity, String entityCode,
                                       LocalDateTime from, LocalDateTime to) {
        String scope = reassignmentService.resolveScope(identity, entityCode);

        Map<String, String> names = new HashMap<>();
        for (EntityUser user : entityUserRepository.findByEntityCodeOrderByDisplayNameAsc(scope)) {
            names.put(user.getUserId(), user.getDisplayName());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityCode", scope);
        result.put("outbound", toRows(historyRepository.countOutboundByOfficer(scope, from, to), names));
        result.put("inbound", toRows(historyRepository.countInboundByOfficer(scope, from, to), names));
        result.put("totalMoves", historyRepository.countByEntityCode(scope));
        return result;
    }

    private List<Map<String, Object>> toRows(List<Object[]> rows, Map<String, String> names) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : rows) {
            String userId = row[0] == null ? null : row[0].toString();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", userId);
            // Falls back to the raw id when the officer is not in the directory — an unknown id is
            // more useful in an audit report than a blank cell.
            item.put("displayName", names.getOrDefault(userId, userId));
            item.put("count", row[1] == null ? 0 : ((Number) row[1]).intValue());
            out.add(item);
        }
        return out;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
