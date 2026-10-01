package com.hrms.cms.service;

import com.hrms.cms.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raises the AA Admin alert required when a closure clause is missing from CLOSURE_CLAUSE_MASTER.
 *
 * The story is explicit that an unmapped clause must surface a configuration alert to AA Admin rather
 * than being silently absorbed: the citizen's escalation is blocked until someone adds the clause, so
 * a failure nobody is told about becomes a complaint that can never be appealed.
 *
 * De-duplicated per clause+scheme for the lifetime of the process. A clause that is missing will be
 * hit by every caller who touches an affected complaint, and one alert per request would bury the AA
 * Admin's notification bell under hundreds of copies of the same fact — which in practice means the
 * alert gets ignored, defeating the point.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClauseConfigurationAlertService {

    private static final String AA_ADMIN_ROLE = "AA_ADMIN";

    private final NotificationService notificationService;
    private final KeycloakUserService keycloakUserService;
    private final InAppNotificationRepository notificationRepository;

    private final Set<String> alreadyAlerted = ConcurrentHashMap.newKeySet();

    public void raiseUnmappedClauseAlert(String clauseCode, String schemeVersion) {
        String key = schemeVersion + "|" + clauseCode;
        if (!alreadyAlerted.add(key)) {
            return;
        }

        String title = "Closure clause not configured";
        String message = "Closure clause '" + (clauseCode == null ? "(none recorded)" : clauseCode)
                + "' is missing from the Clause Master for scheme " + schemeVersion
                + ". Appeals and representations against complaints closed under this clause are"
                + " blocked until it is added.";

        try {
            var admins = keycloakUserService.getUsersByRole(AA_ADMIN_ROLE);
            if (admins.isEmpty()) {
                // Still loud in the log: an unconfigured clause with no admin to tell is worse, not
                // better, and must not look like a successfully delivered alert.
                log.error("No {} user exists to receive the unmapped-clause alert for {} ({})",
                        AA_ADMIN_ROLE, clauseCode, schemeVersion);
                return;
            }
            for (var admin : admins) {
                Object userId = admin.get("userId");
                if (userId == null) {
                    continue;
                }
                notificationService.send(
                        userId.toString(),
                        "CONFIGURATION_ALERT",
                        title,
                        message,
                        clauseCode,
                        "CLAUSE_MASTER",
                        "/admin/master-data");
            }
            log.warn("Raised unmapped-clause configuration alert to {} AA_ADMIN user(s): {} ({})",
                    admins.size(), clauseCode, schemeVersion);
        } catch (Exception e) {
            // Never let alerting failure mask the original fail-closed response.
            log.error("Failed to raise unmapped-clause alert for {} ({}) at {}: {}",
                    clauseCode, schemeVersion, LocalDateTime.now(), e.getMessage());
        }
    }

    /** Clears the de-duplication cache; used by tests and after a Clause Master reconfiguration. */
    public void resetAlertCache() {
        alreadyAlerted.clear();
    }
}
