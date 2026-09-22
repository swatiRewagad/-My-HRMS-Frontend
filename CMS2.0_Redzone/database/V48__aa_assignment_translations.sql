-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V48 — AA assignment/notification translation keys, all ten locales (session S2C)
--
-- Duplicates AaAssignmentTranslationSeeder deliberately. The seeder only runs on an application boot,
-- so a database restored and queried without one would serve English to every locale. Both paths are
-- insert-if-absent, so whichever runs first wins and the second is a no-op.
--
-- Re-runnable. Note `value` is a reserved word and is quoted in the entity mapping, so it is
-- backtick-quoted here.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.assignment.assigned', 'aa-assignment', 'Appeal assigned to you', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.assignment.assigned');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.assignment.assigned_under_grace', 'aa-assignment', 'Appeal assigned above the normal workload limit', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.assignment.assigned_under_grace');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.assignment.vernacular_override', 'aa-assignment', 'Appeal assigned to you for language handling', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.assignment.vernacular_override');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.assignment.manual_override', 'aa-assignment', 'Appeal assigned to you by the administrator', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.assignment.manual_override');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.assignment.pool_empty', 'aa-assignment', 'No officer is currently available for assignment', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.assignment.pool_empty');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.assignment.pool_exhausted', 'aa-assignment', 'All officers have reached their workload limit', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.assignment.pool_exhausted');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.assignment.error_reason_required', 'aa-assignment', 'A reason is required for a manual assignment', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.assignment.error_reason_required');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.assignment.error_target_not_in_pool', 'aa-assignment', 'The selected officer is not in the assignment pool', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.assignment.error_target_not_in_pool');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.assignment.error_target_unavailable', 'aa-assignment', 'The selected officer is inactive or on leave', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.assignment.error_target_unavailable');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.threshold_updated', 'aa-assignment', 'Workload limit updated', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.threshold_updated');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.threshold_updated_rebalance_pending', 'aa-assignment', 'Workload limit updated. Rebalancing is pending administrator action.', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.threshold_updated_rebalance_pending');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.skills_updated', 'aa-assignment', 'Language skills updated', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.skills_updated');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.activation_updated', 'aa-assignment', 'Officer status updated', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.activation_updated');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.activation_unchanged', 'aa-assignment', 'Officer status was already set to this value', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.activation_unchanged');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.bulk_activation_complete', 'aa-assignment', 'All selected officers were updated', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.bulk_activation_complete');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.bulk_activation_partial', 'aa-assignment', 'Some officers could not be updated', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.bulk_activation_partial');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.rebalance_complete', 'aa-assignment', 'Queue rebalanced', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.rebalance_complete');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.deactivate_warning_pending_work', 'aa-assignment', 'This officer still has pending complaints assigned', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.deactivate_warning_pending_work');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.deactivate_no_pending_work', 'aa-assignment', 'This officer has no pending complaints', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.deactivate_no_pending_work');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.notify_deactivated_with_pending', 'aa-assignment', 'An officer was deactivated while still holding pending complaints', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.notify_deactivated_with_pending');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.error_officer_not_found', 'aa-assignment', 'Officer not found in the assignment pool', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.error_officer_not_found');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.error_threshold_negative', 'aa-assignment', 'The workload limit cannot be negative', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.error_threshold_negative');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.error_threshold_required', 'aa-assignment', 'A workload limit value is required', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.error_threshold_required');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.error_no_officers_selected', 'aa-assignment', 'Select at least one officer', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.error_no_officers_selected');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.error_confirmation_required', 'aa-assignment', 'Confirmation is required because this officer has pending complaints', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.error_confirmation_required');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.error_role_group_required', 'aa-assignment', 'A role group is required', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.error_role_group_required');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.pool.error_unknown', 'aa-assignment', 'The action could not be completed', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.pool.error_unknown');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.submitted', 'aa-assignment', 'Reassignment request submitted for approval', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.submitted');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.auto_approved', 'aa-assignment', 'Reassignment request approved automatically', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.auto_approved');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.approved', 'aa-assignment', 'Reassignment request approved', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.approved');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.rejected', 'aa-assignment', 'Reassignment request rejected', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.rejected');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.withdrawn', 'aa-assignment', 'Reassignment request withdrawn', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.withdrawn');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.notify_approved', 'aa-assignment', 'Your reassignment request was approved', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.notify_approved');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.notify_rejected', 'aa-assignment', 'Your reassignment request was rejected', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.notify_rejected');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.notify_pending_approval', 'aa-assignment', 'A reassignment request is awaiting your approval', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.notify_pending_approval');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.error_appeal_required', 'aa-assignment', 'An appeal number is required', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.error_appeal_required');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.error_reason_required', 'aa-assignment', 'A reason is required for a reassignment request', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.error_reason_required');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.error_identity_unresolved', 'aa-assignment', 'Your identity could not be established', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.error_identity_unresolved');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.error_not_assigned', 'aa-assignment', 'This appeal is not currently assigned to anyone', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.error_not_assigned');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.error_not_holder', 'aa-assignment', 'You can only request reassignment of an appeal assigned to you', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.error_not_holder');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.error_already_pending', 'aa-assignment', 'A reassignment request is already pending for this appeal', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.error_already_pending');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.error_request_not_found', 'aa-assignment', 'Reassignment request not found', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.error_request_not_found');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.error_not_pending', 'aa-assignment', 'This request has already been decided', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.error_not_pending');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.reassign.error_not_requester', 'aa-assignment', 'Only the officer who raised a request may withdraw it', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.reassign.error_not_requester');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.escalation.unclaimed_draft', 'aa-assignment', 'A draft has remained unclaimed beyond the allowed time', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.escalation.unclaimed_draft');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.escalation.claimed', 'aa-assignment', 'Draft marked as picked up', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.escalation.claimed');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.escalation.claim_not_holder', 'aa-assignment', 'This draft is not assigned to you', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.escalation.claim_not_holder');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.escalation.sweep_complete', 'aa-assignment', 'Escalation check complete', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.escalation.sweep_complete');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.console_title', 'aa-assignment', 'AA Assignment Administration', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.console_title');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.officer_pool', 'aa-assignment', 'Officer Pool', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.officer_pool');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.threshold', 'aa-assignment', 'Workload Limit', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.threshold');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.current_workload', 'aa-assignment', 'Current Workload', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.current_workload');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.status_active', 'aa-assignment', 'Active', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.status_active');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.status_inactive', 'aa-assignment', 'Inactive', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.status_inactive');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.status_on_leave', 'aa-assignment', 'On Leave', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.status_on_leave');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.at_threshold', 'aa-assignment', 'At Limit', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.at_threshold');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.eligible', 'aa-assignment', 'Eligible', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.eligible');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.bulk_activate', 'aa-assignment', 'Activate Selected', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.bulk_activate');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.bulk_deactivate', 'aa-assignment', 'Deactivate Selected', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.bulk_deactivate');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.edit_threshold', 'aa-assignment', 'Edit Limit', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.edit_threshold');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.skill_languages', 'aa-assignment', 'Language Skills', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.skill_languages');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.reason', 'aa-assignment', 'Reason', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.reason');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.confirm', 'aa-assignment', 'Confirm', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.confirm');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.cancel', 'aa-assignment', 'Cancel', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.cancel');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.select_all', 'aa-assignment', 'Select All', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.select_all');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.pending_approvals', 'aa-assignment', 'Pending Reassignment Approvals', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.pending_approvals');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.approve', 'aa-assignment', 'Approve', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.approve');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.reject', 'aa-assignment', 'Reject', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.reject');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.rebalance', 'aa-assignment', 'Rebalance Queue', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.rebalance');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.audit_trail', 'aa-assignment', 'Audit Trail', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.audit_trail');
INSERT INTO translation_keys (code, module, default_value, created_at, updated_at)
SELECT 'aa.admin.no_officers', 'aa-assignment', 'No officers in this pool', NOW(6), NOW(6)
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM translation_keys WHERE code = 'aa.admin.no_officers');

-- ── hi ──
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अपील आपको सौंपी गई है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'सामान्य कार्यभार सीमा से अधिक अपील सौंपी गई है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned_under_grace'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'भाषा संबंधी कार्यवाही के लिए अपील आपको सौंपी गई है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.vernacular_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'प्रशासक द्वारा अपील आपको सौंपी गई है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.manual_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'इस समय सौंपने के लिए कोई अधिकारी उपलब्ध नहीं है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_empty'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'सभी अधिकारी अपनी कार्यभार सीमा तक पहुँच चुके हैं', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_exhausted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'मैन्युअल आवंटन के लिए कारण देना अनिवार्य है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'चयनित अधिकारी आवंटन समूह में नहीं है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_not_in_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'चयनित अधिकारी निष्क्रिय है अथवा अवकाश पर है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_unavailable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'कार्यभार सीमा अद्यतन कर दी गई', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'कार्यभार सीमा अद्यतन कर दी गई। पुनर्संतुलन प्रशासक की कार्रवाई हेतु लंबित है।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated_rebalance_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'भाषा कौशल अद्यतन कर दिए गए', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.skills_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अधिकारी की स्थिति अद्यतन कर दी गई', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अधिकारी की स्थिति पहले से ही यही निर्धारित थी', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_unchanged'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'चयनित सभी अधिकारी अद्यतन कर दिए गए', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'कुछ अधिकारियों को अद्यतन नहीं किया जा सका', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_partial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'कतार का पुनर्संतुलन कर दिया गया', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.rebalance_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'इस अधिकारी के पास अभी भी लंबित शिकायतें सौंपी हुई हैं', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_warning_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'इस अधिकारी के पास कोई लंबित शिकायत नहीं है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_no_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'एक अधिकारी को लंबित शिकायतें रहते हुए ही निष्क्रिय कर दिया गया', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.notify_deactivated_with_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'आवंटन समूह में अधिकारी नहीं मिला', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_officer_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'कार्यभार सीमा ऋणात्मक नहीं हो सकती', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_negative'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'कार्यभार सीमा का मान देना अनिवार्य है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'कम से कम एक अधिकारी का चयन करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_no_officers_selected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'इस अधिकारी के पास लंबित शिकायतें होने के कारण पुष्टि आवश्यक है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_confirmation_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'भूमिका समूह देना अनिवार्य है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_role_group_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'यह कार्रवाई पूरी नहीं की जा सकी', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_unknown'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'पुनरावंटन अनुरोध अनुमोदन हेतु प्रस्तुत किया गया', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.submitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'पुनरावंटन अनुरोध स्वतः अनुमोदित कर दिया गया', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.auto_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'पुनरावंटन अनुरोध अनुमोदित कर दिया गया', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'पुनरावंटन अनुरोध अस्वीकृत कर दिया गया', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'पुनरावंटन अनुरोध वापस ले लिया गया', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.withdrawn'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'आपका पुनरावंटन अनुरोध अनुमोदित कर दिया गया', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'आपका पुनरावंटन अनुरोध अस्वीकृत कर दिया गया', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'एक पुनरावंटन अनुरोध आपके अनुमोदन की प्रतीक्षा में है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_pending_approval'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अपील संख्या देना अनिवार्य है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_appeal_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'पुनरावंटन अनुरोध के लिए कारण देना अनिवार्य है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'आपकी पहचान स्थापित नहीं हो सकी', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_identity_unresolved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'यह अपील इस समय किसी को सौंपी नहीं गई है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'आप केवल अपने को सौंपी गई अपील के पुनरावंटन का अनुरोध कर सकते हैं', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'इस अपील के लिए एक पुनरावंटन अनुरोध पहले से ही लंबित है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_already_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'पुनरावंटन अनुरोध नहीं मिला', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_request_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'इस अनुरोध पर पहले ही निर्णय हो चुका है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अनुरोध केवल वही अधिकारी वापस ले सकता है जिसने उसे प्रस्तुत किया था', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_requester'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'एक प्रारूप निर्धारित समय से अधिक समय तक अस्वीकृत पड़ा रहा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.unclaimed_draft'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'प्रारूप ग्रहण किया गया के रूप में चिह्नित', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claimed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'यह प्रारूप आपको सौंपा नहीं गया है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claim_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'उत्प्रेषण जाँच पूर्ण हुई', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.sweep_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अपीलीय प्राधिकारी आवंटन प्रशासन', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.console_title'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अधिकारी समूह', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.officer_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'कार्यभार सीमा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'वर्तमान कार्यभार', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.current_workload'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'सक्रिय', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_active'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'निष्क्रिय', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_inactive'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अवकाश पर', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_on_leave'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'सीमा पर', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.at_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'पात्र', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.eligible'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'चयनित को सक्रिय करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_activate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'चयनित को निष्क्रिय करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_deactivate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'सीमा संपादित करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.edit_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'भाषा कौशल', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.skill_languages'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'कारण', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reason'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'पुष्टि करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.confirm'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'रद्द करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.cancel'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'सभी चुनें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.select_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'लंबित पुनरावंटन अनुमोदन', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.pending_approvals'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अनुमोदित करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.approve'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अस्वीकार करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reject'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'कतार का पुनर्संतुलन करें', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.rebalance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'अंकेक्षण अभिलेख', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.audit_trail'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'hi', 'इस समूह में कोई अधिकारी नहीं है', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.no_officers'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'hi');

-- ── mr ──
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'अपील आपल्याकडे सोपविण्यात आली आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'नेहमीच्या कार्यभार मर्यादेपेक्षा अधिक अपील सोपविण्यात आली आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned_under_grace'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'भाषाविषयक कामकाजासाठी अपील आपल्याकडे सोपविण्यात आली आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.vernacular_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'प्रशासकाने अपील आपल्याकडे सोपविली आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.manual_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'सध्या वाटपासाठी कोणताही अधिकारी उपलब्ध नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_empty'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'सर्व अधिकारी आपल्या कार्यभार मर्यादेपर्यंत पोहोचले आहेत', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_exhausted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'मॅन्युअल वाटपासाठी कारण देणे आवश्यक आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'निवडलेला अधिकारी वाटप गटात नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_not_in_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'निवडलेला अधिकारी निष्क्रिय आहे किंवा रजेवर आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_unavailable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'कार्यभार मर्यादा अद्ययावत केली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'कार्यभार मर्यादा अद्ययावत केली. पुनर्संतुलन प्रशासकाच्या कार्यवाहीसाठी प्रलंबित आहे.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated_rebalance_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'भाषा कौशल्ये अद्ययावत केली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.skills_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'अधिकाऱ्याची स्थिती अद्ययावत केली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'अधिकाऱ्याची स्थिती आधीच याच मूल्यावर निश्चित होती', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_unchanged'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'निवडलेले सर्व अधिकारी अद्ययावत केले', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'काही अधिकाऱ्यांना अद्ययावत करता आले नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_partial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'रांगेचे पुनर्संतुलन केले', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.rebalance_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'या अधिकाऱ्याकडे अद्याप प्रलंबित तक्रारी सोपविलेल्या आहेत', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_warning_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'या अधिकाऱ्याकडे कोणतीही प्रलंबित तक्रार नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_no_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'प्रलंबित तक्रारी असतानाही एका अधिकाऱ्याला निष्क्रिय करण्यात आले', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.notify_deactivated_with_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'वाटप गटात अधिकारी आढळला नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_officer_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'कार्यभार मर्यादा ऋण असू शकत नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_negative'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'कार्यभार मर्यादेचे मूल्य देणे आवश्यक आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'किमान एका अधिकाऱ्याची निवड करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_no_officers_selected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'या अधिकाऱ्याकडे प्रलंबित तक्रारी असल्यामुळे निश्चिती आवश्यक आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_confirmation_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'भूमिका गट देणे आवश्यक आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_role_group_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'ही कार्यवाही पूर्ण करता आली नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_unknown'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'पुनर्वाटप विनंती मंजुरीसाठी सादर केली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.submitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'पुनर्वाटप विनंती स्वयंचलितपणे मंजूर केली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.auto_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'पुनर्वाटप विनंती मंजूर केली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'पुनर्वाटप विनंती नाकारली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'पुनर्वाटप विनंती मागे घेतली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.withdrawn'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'आपली पुनर्वाटप विनंती मंजूर करण्यात आली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'आपली पुनर्वाटप विनंती नाकारण्यात आली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'एक पुनर्वाटप विनंती आपल्या मंजुरीच्या प्रतीक्षेत आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_pending_approval'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'अपील क्रमांक देणे आवश्यक आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_appeal_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'पुनर्वाटप विनंतीसाठी कारण देणे आवश्यक आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'आपली ओळख निश्चित करता आली नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_identity_unresolved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'ही अपील सध्या कोणालाही सोपविलेली नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'आपण केवळ आपल्याकडे सोपविलेल्या अपिलाच्या पुनर्वाटपाची विनंती करू शकता', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'या अपिलासाठी एक पुनर्वाटप विनंती आधीच प्रलंबित आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_already_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'पुनर्वाटप विनंती आढळली नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_request_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'या विनंतीवर आधीच निर्णय घेण्यात आला आहे', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'विनंती केवळ ती सादर करणारा अधिकारीच मागे घेऊ शकतो', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_requester'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'एक मसुदा अनुमत कालावधीपेक्षा अधिक काळ अस्वीकृत राहिला', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.unclaimed_draft'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'मसुदा स्वीकारला असे चिन्हांकित केले', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claimed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'हा मसुदा आपल्याकडे सोपविलेला नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claim_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'वाढीव स्तर तपासणी पूर्ण झाली', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.sweep_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'अपिलीय प्राधिकरण वाटप प्रशासन', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.console_title'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'अधिकारी गट', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.officer_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'कार्यभार मर्यादा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'सध्याचा कार्यभार', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.current_workload'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'सक्रिय', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_active'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'निष्क्रिय', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_inactive'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'रजेवर', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_on_leave'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'मर्यादेवर', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.at_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'पात्र', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.eligible'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'निवडलेले सक्रिय करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_activate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'निवडलेले निष्क्रिय करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_deactivate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'मर्यादा संपादित करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.edit_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'भाषा कौशल्ये', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.skill_languages'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'कारण', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reason'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'निश्चित करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.confirm'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'रद्द करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.cancel'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'सर्व निवडा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.select_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'प्रलंबित पुनर्वाटप मंजुऱ्या', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.pending_approvals'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'मंजूर करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.approve'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'नाकारा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reject'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'रांगेचे पुनर्संतुलन करा', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.rebalance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'लेखापरीक्षण नोंद', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.audit_trail'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'mr', 'या गटात कोणताही अधिकारी नाही', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.no_officers'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'mr');

-- ── bn ──
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'আপিলটি আপনাকে বরাদ্দ করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'স্বাভাবিক কর্মভার সীমার অতিরিক্ত আপিল বরাদ্দ করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned_under_grace'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'ভাষা সংক্রান্ত কাজের জন্য আপিলটি আপনাকে বরাদ্দ করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.vernacular_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'প্রশাসক আপিলটি আপনাকে বরাদ্দ করেছেন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.manual_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'বরাদ্দের জন্য বর্তমানে কোনো আধিকারিক উপলব্ধ নেই', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_empty'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'সকল আধিকারিক তাঁদের কর্মভার সীমায় পৌঁছে গিয়েছেন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_exhausted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'হাতে করা বরাদ্দের জন্য কারণ উল্লেখ করা আবশ্যক', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'নির্বাচিত আধিকারিক বরাদ্দ গোষ্ঠীতে নেই', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_not_in_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'নির্বাচিত আধিকারিক নিষ্ক্রিয় অথবা ছুটিতে আছেন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_unavailable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'কর্মভার সীমা হালনাগাদ করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'কর্মভার সীমা হালনাগাদ করা হয়েছে। পুনঃসমতা প্রশাসকের পদক্ষেপের অপেক্ষায় রয়েছে।', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated_rebalance_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'ভাষা দক্ষতা হালনাগাদ করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.skills_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'আধিকারিকের অবস্থা হালনাগাদ করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'আধিকারিকের অবস্থা আগে থেকেই এই মানে নির্ধারিত ছিল', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_unchanged'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'নির্বাচিত সকল আধিকারিক হালনাগাদ করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'কিছু আধিকারিককে হালনাগাদ করা যায়নি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_partial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'সারি পুনঃসমতাযুক্ত করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.rebalance_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'এই আধিকারিকের কাছে এখনও নিষ্পত্তিহীন অভিযোগ বরাদ্দ রয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_warning_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'এই আধিকারিকের কোনো নিষ্পত্তিহীন অভিযোগ নেই', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_no_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'নিষ্পত্তিহীন অভিযোগ থাকা সত্ত্বেও একজন আধিকারিককে নিষ্ক্রিয় করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.notify_deactivated_with_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'বরাদ্দ গোষ্ঠীতে আধিকারিককে পাওয়া যায়নি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_officer_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'কর্মভার সীমা ঋণাত্মক হতে পারে না', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_negative'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'কর্মভার সীমার মান উল্লেখ করা আবশ্যক', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'অন্তত একজন আধিকারিক নির্বাচন করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_no_officers_selected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'এই আধিকারিকের নিষ্পত্তিহীন অভিযোগ থাকায় নিশ্চিতকরণ আবশ্যক', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_confirmation_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'একটি ভূমিকা গোষ্ঠী উল্লেখ করা আবশ্যক', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_role_group_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'এই কাজটি সম্পন্ন করা যায়নি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_unknown'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'পুনর্বরাদ্দের আবেদন অনুমোদনের জন্য দাখিল করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.submitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'পুনর্বরাদ্দের আবেদন স্বয়ংক্রিয়ভাবে অনুমোদিত হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.auto_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'পুনর্বরাদ্দের আবেদন অনুমোদিত হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'পুনর্বরাদ্দের আবেদন প্রত্যাখ্যাত হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'পুনর্বরাদ্দের আবেদন প্রত্যাহার করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.withdrawn'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'আপনার পুনর্বরাদ্দের আবেদন অনুমোদিত হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'আপনার পুনর্বরাদ্দের আবেদন প্রত্যাখ্যাত হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'একটি পুনর্বরাদ্দের আবেদন আপনার অনুমোদনের অপেক্ষায় রয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_pending_approval'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'আপিল নম্বর উল্লেখ করা আবশ্যক', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_appeal_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'পুনর্বরাদ্দের আবেদনের জন্য কারণ উল্লেখ করা আবশ্যক', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'আপনার পরিচয় নিশ্চিত করা যায়নি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_identity_unresolved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'এই আপিলটি বর্তমানে কারও কাছে বরাদ্দ নেই', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'আপনি কেবল আপনাকে বরাদ্দ করা আপিলের পুনর্বরাদ্দের আবেদন করতে পারেন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'এই আপিলের জন্য একটি পুনর্বরাদ্দের আবেদন ইতিমধ্যেই বিচারাধীন রয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_already_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'পুনর্বরাদ্দের আবেদন পাওয়া যায়নি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_request_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'এই আবেদনটি সম্পর্কে ইতিমধ্যেই সিদ্ধান্ত নেওয়া হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'যে আধিকারিক আবেদনটি করেছেন কেবল তিনিই তা প্রত্যাহার করতে পারেন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_requester'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'একটি খসড়া অনুমোদিত সময়সীমার পরেও অগৃহীত রয়ে গিয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.unclaimed_draft'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'খসড়াটি গৃহীত হিসেবে চিহ্নিত করা হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claimed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'এই খসড়াটি আপনাকে বরাদ্দ করা হয়নি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claim_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'ঊর্ধ্বতন স্তরে প্রেরণের পরীক্ষা সম্পন্ন হয়েছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.sweep_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'আপিল কর্তৃপক্ষ বরাদ্দ প্রশাসন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.console_title'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'আধিকারিক গোষ্ঠী', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.officer_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'কর্মভার সীমা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'বর্তমান কর্মভার', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.current_workload'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'সক্রিয়', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_active'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'নিষ্ক্রিয়', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_inactive'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'ছুটিতে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_on_leave'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'সীমায় পৌঁছেছে', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.at_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'যোগ্য', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.eligible'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'নির্বাচিতদের সক্রিয় করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_activate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'নির্বাচিতদের নিষ্ক্রিয় করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_deactivate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'সীমা সম্পাদনা করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.edit_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'ভাষা দক্ষতা', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.skill_languages'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'কারণ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reason'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'নিশ্চিত করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.confirm'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'বাতিল করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.cancel'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'সব নির্বাচন করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.select_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'অপেক্ষমাণ পুনর্বরাদ্দ অনুমোদন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.pending_approvals'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'অনুমোদন করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.approve'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'প্রত্যাখ্যান করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reject'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'সারি পুনঃসমতাযুক্ত করুন', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.rebalance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'নিরীক্ষা নথি', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.audit_trail'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'bn', 'এই গোষ্ঠীতে কোনো আধিকারিক নেই', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.no_officers'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'bn');

-- ── te ──
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'అప్పీలు మీకు కేటాయించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'సాధారణ పని భారం పరిమితికి మించి అప్పీలు కేటాయించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned_under_grace'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'భాషా సంబంధిత నిర్వహణ కోసం అప్పీలు మీకు కేటాయించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.vernacular_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'నిర్వాహకుడు అప్పీలును మీకు కేటాయించారు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.manual_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'కేటాయింపు కోసం ప్రస్తుతం ఏ అధికారి అందుబాటులో లేరు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_empty'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'అధికారులందరూ తమ పని భారం పరిమితిని చేరుకున్నారు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_exhausted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'మాన్యువల్ కేటాయింపుకు కారణం తెలపడం తప్పనిసరి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఎంచుకున్న అధికారి కేటాయింపు బృందంలో లేరు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_not_in_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఎంచుకున్న అధికారి క్రియారహితంగా ఉన్నారు లేదా సెలవులో ఉన్నారు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_unavailable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పని భారం పరిమితి నవీకరించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పని భారం పరిమితి నవీకరించబడింది. పునఃసమతుల్యం నిర్వాహకుని చర్య కోసం పెండింగ్‌లో ఉంది.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated_rebalance_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'భాషా నైపుణ్యాలు నవీకరించబడ్డాయి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.skills_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'అధికారి స్థితి నవీకరించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'అధికారి స్థితి ఇప్పటికే ఈ విలువకు నిర్ణయించబడి ఉంది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_unchanged'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఎంచుకున్న అధికారులందరూ నవీకరించబడ్డారు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'కొందరు అధికారులను నవీకరించడం సాధ్యపడలేదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_partial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'వరుస పునఃసమతుల్యం చేయబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.rebalance_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఈ అధికారికి ఇంకా పరిష్కారం కాని ఫిర్యాదులు కేటాయించబడి ఉన్నాయి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_warning_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఈ అధికారికి పరిష్కారం కాని ఫిర్యాదులు ఏవీ లేవు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_no_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పరిష్కారం కాని ఫిర్యాదులు ఉన్నప్పటికీ ఒక అధికారిని క్రియారహితం చేశారు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.notify_deactivated_with_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'కేటాయింపు బృందంలో అధికారి కనుగొనబడలేదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_officer_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పని భారం పరిమితి ఋణాత్మకంగా ఉండకూడదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_negative'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పని భారం పరిమితి విలువ తెలపడం తప్పనిసరి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'కనీసం ఒక అధికారిని ఎంచుకోండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_no_officers_selected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఈ అధికారికి పరిష్కారం కాని ఫిర్యాదులు ఉన్నందున నిర్ధారణ అవసరం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_confirmation_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పాత్ర సమూహం తెలపడం తప్పనిసరి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_role_group_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఈ చర్యను పూర్తి చేయడం సాధ్యపడలేదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_unknown'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పునఃకేటాయింపు అభ్యర్థన ఆమోదం కోసం సమర్పించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.submitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పునఃకేటాయింపు అభ్యర్థన స్వయంచాలకంగా ఆమోదించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.auto_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పునఃకేటాయింపు అభ్యర్థన ఆమోదించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పునఃకేటాయింపు అభ్యర్థన తిరస్కరించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పునఃకేటాయింపు అభ్యర్థన ఉపసంహరించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.withdrawn'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'మీ పునఃకేటాయింపు అభ్యర్థన ఆమోదించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'మీ పునఃకేటాయింపు అభ్యర్థన తిరస్కరించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఒక పునఃకేటాయింపు అభ్యర్థన మీ ఆమోదం కోసం వేచి ఉంది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_pending_approval'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'అప్పీలు సంఖ్య తెలపడం తప్పనిసరి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_appeal_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పునఃకేటాయింపు అభ్యర్థనకు కారణం తెలపడం తప్పనిసరి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'మీ గుర్తింపును నిర్ధారించడం సాధ్యపడలేదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_identity_unresolved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఈ అప్పీలు ప్రస్తుతం ఎవరికీ కేటాయించబడలేదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'మీకు కేటాయించబడిన అప్పీలు పునఃకేటాయింపును మాత్రమే మీరు అభ్యర్థించగలరు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఈ అప్పీలు కోసం ఒక పునఃకేటాయింపు అభ్యర్థన ఇప్పటికే పెండింగ్‌లో ఉంది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_already_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పునఃకేటాయింపు అభ్యర్థన కనుగొనబడలేదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_request_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఈ అభ్యర్థనపై ఇప్పటికే నిర్ణయం తీసుకోబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'అభ్యర్థనను లేవనెత్తిన అధికారి మాత్రమే దానిని ఉపసంహరించగలరు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_requester'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఒక ముసాయిదా అనుమతించిన కాలవ్యవధి దాటినా స్వీకరించబడకుండా ఉండిపోయింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.unclaimed_draft'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ముసాయిదా స్వీకరించబడినదిగా గుర్తించబడింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claimed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఈ ముసాయిదా మీకు కేటాయించబడలేదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claim_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఉన్నత స్థాయికి పంపే తనిఖీ పూర్తయింది', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.sweep_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'అప్పీలు అధికార కేటాయింపు నిర్వహణ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.console_title'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'అధికారుల బృందం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.officer_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పని భారం పరిమితి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ప్రస్తుత పని భారం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.current_workload'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'క్రియాశీలం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_active'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'క్రియారహితం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_inactive'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'సెలవులో', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_on_leave'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పరిమితి వద్ద', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.at_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'అర్హులు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.eligible'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఎంచుకున్నవారిని క్రియాశీలం చేయండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_activate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఎంచుకున్నవారిని క్రియారహితం చేయండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_deactivate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పరిమితిని సవరించండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.edit_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'భాషా నైపుణ్యాలు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.skill_languages'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'కారణం', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reason'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'నిర్ధారించండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.confirm'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'రద్దు చేయండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.cancel'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'అన్నీ ఎంచుకోండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.select_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'పెండింగ్‌లో ఉన్న పునఃకేటాయింపు ఆమోదాలు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.pending_approvals'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఆమోదించండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.approve'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'తిరస్కరించండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reject'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'వరుసను పునఃసమతుల్యం చేయండి', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.rebalance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'తనిఖీ నమోదు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.audit_trail'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'te', 'ఈ బృందంలో అధికారులు ఎవరూ లేరు', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.no_officers'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'te');

-- ── ta ──
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மேல்முறையீடு உங்களுக்கு ஒப்படைக்கப்பட்டுள்ளது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'வழக்கமான பணிச்சுமை வரம்பைக் கடந்து மேல்முறையீடு ஒப்படைக்கப்பட்டுள்ளது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned_under_grace'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மொழி சார்ந்த கையாளுதலுக்காக மேல்முறையீடு உங்களுக்கு ஒப்படைக்கப்பட்டுள்ளது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.vernacular_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'நிர்வாகி மேல்முறையீட்டை உங்களுக்கு ஒப்படைத்துள்ளார்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.manual_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'ஒப்படைப்புக்கு தற்போது எந்த அதிகாரியும் கிடைக்கவில்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_empty'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'அனைத்து அதிகாரிகளும் தமது பணிச்சுமை வரம்பை எட்டிவிட்டனர்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_exhausted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'கைமுறை ஒப்படைப்புக்கு காரணம் அளிப்பது கட்டாயம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'தேர்ந்தெடுக்கப்பட்ட அதிகாரி ஒப்படைப்புக் குழுவில் இல்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_not_in_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'தேர்ந்தெடுக்கப்பட்ட அதிகாரி செயலில் இல்லை அல்லது விடுப்பில் உள்ளார்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_unavailable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'பணிச்சுமை வரம்பு புதுப்பிக்கப்பட்டது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'பணிச்சுமை வரம்பு புதுப்பிக்கப்பட்டது. மறுசமநிலைப்படுத்தல் நிர்வாகியின் நடவடிக்கைக்காக நிலுவையில் உள்ளது.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated_rebalance_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மொழித் திறன்கள் புதுப்பிக்கப்பட்டன', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.skills_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'அதிகாரியின் நிலை புதுப்பிக்கப்பட்டது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'அதிகாரியின் நிலை ஏற்கெனவே இந்த மதிப்பிலேயே அமைக்கப்பட்டிருந்தது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_unchanged'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'தேர்ந்தெடுக்கப்பட்ட அனைத்து அதிகாரிகளும் புதுப்பிக்கப்பட்டனர்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'சில அதிகாரிகளைப் புதுப்பிக்க முடியவில்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_partial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'வரிசை மறுசமநிலைப்படுத்தப்பட்டது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.rebalance_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'இந்த அதிகாரியிடம் இன்னும் நிலுவையில் உள்ள முறையீடுகள் ஒப்படைக்கப்பட்டுள்ளன', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_warning_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'இந்த அதிகாரியிடம் நிலுவையில் உள்ள முறையீடுகள் இல்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_no_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'நிலுவையில் உள்ள முறையீடுகள் இருந்தபோதும் ஒரு அதிகாரி செயலிழக்கப்பட்டார்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.notify_deactivated_with_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'ஒப்படைப்புக் குழுவில் அதிகாரி காணப்படவில்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_officer_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'பணிச்சுமை வரம்பு எதிர்மறையாக இருக்க முடியாது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_negative'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'பணிச்சுமை வரம்பு மதிப்பு அளிப்பது கட்டாயம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'குறைந்தது ஒரு அதிகாரியைத் தேர்ந்தெடுக்கவும்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_no_officers_selected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'இந்த அதிகாரியிடம் நிலுவையில் உள்ள முறையீடுகள் இருப்பதால் உறுதிப்படுத்தல் தேவை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_confirmation_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'பணிப் பங்கு குழு அளிப்பது கட்டாயம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_role_group_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'இந்த நடவடிக்கையை நிறைவு செய்ய முடியவில்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_unknown'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மறுஒப்படைப்புக் கோரிக்கை ஒப்புதலுக்காகச் சமர்ப்பிக்கப்பட்டது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.submitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மறுஒப்படைப்புக் கோரிக்கை தானாகவே ஒப்புதல் பெற்றது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.auto_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மறுஒப்படைப்புக் கோரிக்கை ஒப்புதல் பெற்றது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மறுஒப்படைப்புக் கோரிக்கை நிராகரிக்கப்பட்டது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மறுஒப்படைப்புக் கோரிக்கை திரும்பப் பெறப்பட்டது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.withdrawn'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'உங்கள் மறுஒப்படைப்புக் கோரிக்கை ஒப்புதல் பெற்றது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'உங்கள் மறுஒப்படைப்புக் கோரிக்கை நிராகரிக்கப்பட்டது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'ஒரு மறுஒப்படைப்புக் கோரிக்கை உங்கள் ஒப்புதலுக்குக் காத்திருக்கிறது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_pending_approval'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மேல்முறையீட்டு எண் அளிப்பது கட்டாயம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_appeal_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மறுஒப்படைப்புக் கோரிக்கைக்குக் காரணம் அளிப்பது கட்டாயம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'உங்கள் அடையாளத்தை உறுதிப்படுத்த முடியவில்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_identity_unresolved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'இந்த மேல்முறையீடு தற்போது யாருக்கும் ஒப்படைக்கப்படவில்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'உங்களுக்கு ஒப்படைக்கப்பட்ட மேல்முறையீட்டின் மறுஒப்படைப்பை மட்டுமே நீங்கள் கோர முடியும்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'இந்த மேல்முறையீட்டுக்கு ஒரு மறுஒப்படைப்புக் கோரிக்கை ஏற்கெனவே நிலுவையில் உள்ளது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_already_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மறுஒப்படைப்புக் கோரிக்கை காணப்படவில்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_request_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'இந்தக் கோரிக்கையின் மீது ஏற்கெனவே முடிவு எடுக்கப்பட்டுவிட்டது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'கோரிக்கையை எழுப்பிய அதிகாரி மட்டுமே அதைத் திரும்பப் பெற முடியும்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_requester'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'ஒரு வரைவு அனுமதிக்கப்பட்ட காலத்திற்கு மேலும் ஏற்கப்படாமல் இருந்துவிட்டது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.unclaimed_draft'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'வரைவு ஏற்றுக்கொள்ளப்பட்டதாகக் குறிக்கப்பட்டது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claimed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'இந்த வரைவு உங்களுக்கு ஒப்படைக்கப்படவில்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claim_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மேல்நிலைப் பரிசீலனைச் சோதனை நிறைவடைந்தது', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.sweep_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மேல்முறையீட்டு ஆணைய ஒப்படைப்பு நிர்வாகம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.console_title'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'அதிகாரிகள் குழு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.officer_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'பணிச்சுமை வரம்பு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'தற்போதைய பணிச்சுமை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.current_workload'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'செயலில்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_active'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'செயலில் இல்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_inactive'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'விடுப்பில்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_on_leave'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'வரம்பில்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.at_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'தகுதியுள்ளவர்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.eligible'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'தேர்ந்தெடுத்தவற்றைச் செயல்படுத்து', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_activate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'தேர்ந்தெடுத்தவற்றைச் செயலிழக்கச் செய்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_deactivate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'வரம்பைத் திருத்து', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.edit_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'மொழித் திறன்கள்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.skill_languages'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'காரணம்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reason'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'உறுதிப்படுத்து', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.confirm'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'ரத்து செய்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.cancel'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'அனைத்தையும் தேர்ந்தெடு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.select_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'நிலுவையில் உள்ள மறுஒப்படைப்பு ஒப்புதல்கள்', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.pending_approvals'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'ஒப்புதல் அளி', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.approve'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'நிராகரி', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reject'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'வரிசையை மறுசமநிலைப்படுத்து', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.rebalance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'தணிக்கைப் பதிவு', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.audit_trail'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ta', 'இந்தக் குழுவில் அதிகாரிகள் இல்லை', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.no_officers'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ta');

-- ── gu ──
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'અપીલ તમને સોંપવામાં આવી છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'સામાન્ય કાર્યભાર મર્યાદાથી વધુ અપીલ સોંપવામાં આવી છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned_under_grace'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'ભાષા સંબંધિત કામગીરી માટે અપીલ તમને સોંપવામાં આવી છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.vernacular_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પ્રશાસકે અપીલ તમને સોંપી છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.manual_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'હાલમાં સોંપણી માટે કોઈ અધિકારી ઉપલબ્ધ નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_empty'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'બધા અધિકારીઓ તેમની કાર્યભાર મર્યાદા સુધી પહોંચી ગયા છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_exhausted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'મેન્યુઅલ સોંપણી માટે કારણ આપવું આવશ્યક છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પસંદ કરેલા અધિકારી સોંપણી જૂથમાં નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_not_in_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પસંદ કરેલા અધિકારી નિષ્ક્રિય છે અથવા રજા પર છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_unavailable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'કાર્યભાર મર્યાદા અદ્યતન કરવામાં આવી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'કાર્યભાર મર્યાદા અદ્યતન કરવામાં આવી. પુનઃસંતુલન પ્રશાસકની કાર્યવાહી માટે બાકી છે.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated_rebalance_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'ભાષા કૌશલ્ય અદ્યતન કરવામાં આવ્યાં', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.skills_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'અધિકારીની સ્થિતિ અદ્યતન કરવામાં આવી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'અધિકારીની સ્થિતિ પહેલેથી જ આ મૂલ્ય પર નિર્ધારિત હતી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_unchanged'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પસંદ કરેલા બધા અધિકારીઓ અદ્યતન કરવામાં આવ્યા', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'કેટલાક અધિકારીઓને અદ્યતન કરી શકાયા નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_partial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'કતારનું પુનઃસંતુલન કરવામાં આવ્યું', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.rebalance_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'આ અધિકારી પાસે હજુ પણ બાકી ફરિયાદો સોંપાયેલી છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_warning_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'આ અધિકારી પાસે કોઈ બાકી ફરિયાદ નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_no_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'બાકી ફરિયાદો હોવા છતાં એક અધિકારીને નિષ્ક્રિય કરવામાં આવ્યા', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.notify_deactivated_with_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'સોંપણી જૂથમાં અધિકારી મળ્યા નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_officer_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'કાર્યભાર મર્યાદા ઋણ હોઈ શકતી નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_negative'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'કાર્યભાર મર્યાદાનું મૂલ્ય આપવું આવશ્યક છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'ઓછામાં ઓછા એક અધિકારીની પસંદગી કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_no_officers_selected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'આ અધિકારી પાસે બાકી ફરિયાદો હોવાથી પુષ્ટિ આવશ્યક છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_confirmation_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'ભૂમિકા જૂથ આપવું આવશ્યક છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_role_group_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'આ કાર્યવાહી પૂર્ણ કરી શકાઈ નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_unknown'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પુનઃસોંપણી વિનંતી મંજૂરી માટે રજૂ કરવામાં આવી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.submitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પુનઃસોંપણી વિનંતી સ્વયંસંચાલિત રીતે મંજૂર કરવામાં આવી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.auto_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પુનઃસોંપણી વિનંતી મંજૂર કરવામાં આવી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પુનઃસોંપણી વિનંતી નામંજૂર કરવામાં આવી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પુનઃસોંપણી વિનંતી પાછી ખેંચવામાં આવી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.withdrawn'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'તમારી પુનઃસોંપણી વિનંતી મંજૂર કરવામાં આવી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'તમારી પુનઃસોંપણી વિનંતી નામંજૂર કરવામાં આવી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'એક પુનઃસોંપણી વિનંતી તમારી મંજૂરીની પ્રતીક્ષામાં છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_pending_approval'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'અપીલ ક્રમાંક આપવો આવશ્યક છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_appeal_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પુનઃસોંપણી વિનંતી માટે કારણ આપવું આવશ્યક છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'તમારી ઓળખ સ્થાપિત કરી શકાઈ નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_identity_unresolved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'આ અપીલ હાલમાં કોઈને સોંપવામાં આવી નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'તમે ફક્ત તમને સોંપાયેલી અપીલની પુનઃસોંપણીની વિનંતી કરી શકો છો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'આ અપીલ માટે એક પુનઃસોંપણી વિનંતી પહેલેથી જ બાકી છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_already_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પુનઃસોંપણી વિનંતી મળી નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_request_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'આ વિનંતી પર પહેલેથી જ નિર્ણય લેવાઈ ગયો છે', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'વિનંતી ફક્ત તે અધિકારી પાછી ખેંચી શકે જેમણે તે રજૂ કરી હતી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_requester'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'એક મુસદ્દો અનુમત સમય કરતાં વધુ સમય સુધી અસ્વીકૃત રહ્યો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.unclaimed_draft'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'મુસદ્દો સ્વીકારાયેલો તરીકે ચિહ્નિત કરવામાં આવ્યો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claimed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'આ મુસદ્દો તમને સોંપવામાં આવ્યો નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claim_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'ઉચ્ચ સ્તરે મોકલવાની તપાસ પૂર્ણ થઈ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.sweep_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'અપીલ સત્તાધિકારી સોંપણી પ્રશાસન', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.console_title'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'અધિકારી જૂથ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.officer_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'કાર્યભાર મર્યાદા', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'વર્તમાન કાર્યભાર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.current_workload'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'સક્રિય', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_active'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'નિષ્ક્રિય', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_inactive'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'રજા પર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_on_leave'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'મર્યાદા પર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.at_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પાત્ર', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.eligible'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પસંદ કરેલાને સક્રિય કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_activate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પસંદ કરેલાને નિષ્ક્રિય કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_deactivate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'મર્યાદા સંપાદિત કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.edit_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'ભાષા કૌશલ્ય', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.skill_languages'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'કારણ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reason'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'પુષ્ટિ કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.confirm'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'રદ કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.cancel'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'બધા પસંદ કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.select_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'બાકી પુનઃસોંપણી મંજૂરીઓ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.pending_approvals'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'મંજૂર કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.approve'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'નામંજૂર કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reject'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'કતારનું પુનઃસંતુલન કરો', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.rebalance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'ઑડિટ નોંધ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.audit_trail'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'gu', 'આ જૂથમાં કોઈ અધિકારી નથી', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.no_officers'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'gu');

-- ── ur ──
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اپیل آپ کے سپرد کی گئی ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'معمول کی کام کی حد سے زیادہ اپیل سپرد کی گئی ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned_under_grace'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'زبان سے متعلق کارروائی کے لیے اپیل آپ کے سپرد کی گئی ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.vernacular_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'منتظم نے اپیل آپ کے سپرد کی ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.manual_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اس وقت سپردگی کے لیے کوئی افسر دستیاب نہیں ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_empty'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'تمام افسران اپنی کام کی حد تک پہنچ چکے ہیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_exhausted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'دستی سپردگی کے لیے وجہ بتانا لازمی ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'منتخب افسر سپردگی گروپ میں شامل نہیں ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_not_in_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'منتخب افسر غیر فعال ہے یا رخصت پر ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_unavailable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'کام کی حد تازہ کر دی گئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'کام کی حد تازہ کر دی گئی۔ توازن کی بحالی منتظم کی کارروائی کے لیے زیرِ التوا ہے۔', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated_rebalance_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'زبان کی مہارتیں تازہ کر دی گئیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.skills_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'افسر کی حالت تازہ کر دی گئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'افسر کی حالت پہلے ہی اسی قیمت پر مقرر تھی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_unchanged'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'منتخب کردہ تمام افسران تازہ کر دیے گئے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'بعض افسران کو تازہ نہیں کیا جا سکا', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_partial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'قطار کا توازن بحال کر دیا گیا', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.rebalance_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اس افسر کے پاس اب بھی زیرِ التوا شکایات سپرد ہیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_warning_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اس افسر کے پاس کوئی زیرِ التوا شکایت نہیں ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_no_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'زیرِ التوا شکایات موجود ہونے کے باوجود ایک افسر کو غیر فعال کر دیا گیا', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.notify_deactivated_with_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'سپردگی گروپ میں افسر نہیں ملا', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_officer_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'کام کی حد منفی نہیں ہو سکتی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_negative'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'کام کی حد کی قیمت دینا لازمی ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'کم از کم ایک افسر منتخب کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_no_officers_selected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اس افسر کے پاس زیرِ التوا شکایات ہونے کی وجہ سے تصدیق لازمی ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_confirmation_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'کردار گروپ دینا لازمی ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_role_group_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'یہ کارروائی مکمل نہیں کی جا سکی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_unknown'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'دوبارہ سپردگی کی درخواست منظوری کے لیے پیش کر دی گئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.submitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'دوبارہ سپردگی کی درخواست خود بخود منظور ہو گئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.auto_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'دوبارہ سپردگی کی درخواست منظور کر لی گئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'دوبارہ سپردگی کی درخواست مسترد کر دی گئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'دوبارہ سپردگی کی درخواست واپس لے لی گئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.withdrawn'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'آپ کی دوبارہ سپردگی کی درخواست منظور کر لی گئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'آپ کی دوبارہ سپردگی کی درخواست مسترد کر دی گئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'ایک دوبارہ سپردگی کی درخواست آپ کی منظوری کی منتظر ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_pending_approval'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اپیل نمبر دینا لازمی ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_appeal_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'دوبارہ سپردگی کی درخواست کے لیے وجہ بتانا لازمی ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'آپ کی شناخت ثابت نہیں کی جا سکی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_identity_unresolved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'یہ اپیل اس وقت کسی کے سپرد نہیں ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'آپ صرف اپنے سپرد کی گئی اپیل کی دوبارہ سپردگی کی درخواست کر سکتے ہیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اس اپیل کے لیے ایک دوبارہ سپردگی کی درخواست پہلے ہی زیرِ التوا ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_already_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'دوبارہ سپردگی کی درخواست نہیں ملی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_request_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اس درخواست پر پہلے ہی فیصلہ ہو چکا ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'درخواست صرف وہی افسر واپس لے سکتا ہے جس نے اسے پیش کیا تھا', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_requester'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'ایک مسودہ مقررہ مدت سے زیادہ عرصے تک غیر منظور شدہ پڑا رہا', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.unclaimed_draft'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'مسودہ وصول شدہ کے طور پر نشان زد کیا گیا', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claimed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'یہ مسودہ آپ کے سپرد نہیں ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claim_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'بالا سطح پر ارسال کی جانچ مکمل ہوئی', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.sweep_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اپیلٹ اتھارٹی سپردگی انتظامیہ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.console_title'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'افسران کا گروپ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.officer_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'کام کی حد', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'موجودہ کام کا بوجھ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.current_workload'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'فعال', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_active'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'غیر فعال', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_inactive'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'رخصت پر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_on_leave'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'حد پر', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.at_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اہل', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.eligible'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'منتخب کردہ کو فعال کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_activate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'منتخب کردہ کو غیر فعال کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_deactivate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'حد میں ترمیم کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.edit_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'زبان کی مہارتیں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.skill_languages'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'وجہ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reason'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'تصدیق کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.confirm'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'منسوخ کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.cancel'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'سب منتخب کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.select_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'زیرِ التوا دوبارہ سپردگی کی منظوریاں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.pending_approvals'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'منظور کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.approve'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'مسترد کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reject'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'قطار کا توازن بحال کریں', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.rebalance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'آڈٹ ریکارڈ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.audit_trail'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ur', 'اس گروپ میں کوئی افسر نہیں ہے', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.no_officers'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ur');

-- ── kn ──
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮೇಲ್ಮನವಿಯನ್ನು ನಿಮಗೆ ವಹಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಸಾಮಾನ್ಯ ಕೆಲಸದ ಹೊರೆ ಮಿತಿಗಿಂತ ಹೆಚ್ಚಾಗಿ ಮೇಲ್ಮನವಿ ವಹಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned_under_grace'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಭಾಷಾ ಸಂಬಂಧಿತ ನಿರ್ವಹಣೆಗಾಗಿ ಮೇಲ್ಮನವಿಯನ್ನು ನಿಮಗೆ ವಹಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.vernacular_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ನಿರ್ವಾಹಕರು ಮೇಲ್ಮನವಿಯನ್ನು ನಿಮಗೆ ವಹಿಸಿದ್ದಾರೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.manual_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಸದ್ಯಕ್ಕೆ ವಹಿಸಲು ಯಾವುದೇ ಅಧಿಕಾರಿ ಲಭ್ಯವಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_empty'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಎಲ್ಲಾ ಅಧಿಕಾರಿಗಳು ತಮ್ಮ ಕೆಲಸದ ಹೊರೆ ಮಿತಿಯನ್ನು ತಲುಪಿದ್ದಾರೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_exhausted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕೈಯಾರೆ ವಹಿಸುವಿಕೆಗೆ ಕಾರಣ ನೀಡುವುದು ಕಡ್ಡಾಯ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಆಯ್ಕೆ ಮಾಡಿದ ಅಧಿಕಾರಿ ವಹಿಸುವಿಕೆ ಗುಂಪಿನಲ್ಲಿ ಇಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_not_in_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಆಯ್ಕೆ ಮಾಡಿದ ಅಧಿಕಾರಿ ನಿಷ್ಕ್ರಿಯರಾಗಿದ್ದಾರೆ ಅಥವಾ ರಜೆಯಲ್ಲಿದ್ದಾರೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_unavailable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕೆಲಸದ ಹೊರೆ ಮಿತಿಯನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕೆಲಸದ ಹೊರೆ ಮಿತಿಯನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ. ಮರುಸಮತೋಲನ ನಿರ್ವಾಹಕರ ಕ್ರಮಕ್ಕಾಗಿ ಬಾಕಿ ಉಳಿದಿದೆ.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated_rebalance_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಭಾಷಾ ಕೌಶಲ್ಯಗಳನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.skills_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಅಧಿಕಾರಿಯ ಸ್ಥಿತಿಯನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಅಧಿಕಾರಿಯ ಸ್ಥಿತಿ ಈಗಾಗಲೇ ಇದೇ ಮೌಲ್ಯಕ್ಕೆ ನಿಗದಿಯಾಗಿತ್ತು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_unchanged'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಆಯ್ಕೆ ಮಾಡಿದ ಎಲ್ಲಾ ಅಧಿಕಾರಿಗಳನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕೆಲವು ಅಧಿಕಾರಿಗಳನ್ನು ನವೀಕರಿಸಲು ಸಾಧ್ಯವಾಗಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_partial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಸರತಿಯನ್ನು ಮರುಸಮತೋಲನಗೊಳಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.rebalance_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಈ ಅಧಿಕಾರಿಗೆ ಇನ್ನೂ ಬಾಕಿ ಇರುವ ದೂರುಗಳನ್ನು ವಹಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_warning_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಈ ಅಧಿಕಾರಿಗೆ ಯಾವುದೇ ಬಾಕಿ ದೂರುಗಳಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_no_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಬಾಕಿ ದೂರುಗಳು ಇದ್ದರೂ ಒಬ್ಬ ಅಧಿಕಾರಿಯನ್ನು ನಿಷ್ಕ್ರಿಯಗೊಳಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.notify_deactivated_with_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ವಹಿಸುವಿಕೆ ಗುಂಪಿನಲ್ಲಿ ಅಧಿಕಾರಿ ಕಂಡುಬಂದಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_officer_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕೆಲಸದ ಹೊರೆ ಮಿತಿ ಋಣಾತ್ಮಕವಾಗಿರಲು ಸಾಧ್ಯವಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_negative'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕೆಲಸದ ಹೊರೆ ಮಿತಿಯ ಮೌಲ್ಯ ನೀಡುವುದು ಕಡ್ಡಾಯ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕನಿಷ್ಠ ಒಬ್ಬ ಅಧಿಕಾರಿಯನ್ನು ಆಯ್ಕೆ ಮಾಡಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_no_officers_selected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಈ ಅಧಿಕಾರಿಗೆ ಬಾಕಿ ದೂರುಗಳು ಇರುವುದರಿಂದ ದೃಢೀಕರಣ ಅಗತ್ಯ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_confirmation_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಪಾತ್ರ ಗುಂಪು ನೀಡುವುದು ಕಡ್ಡಾಯ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_role_group_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಈ ಕ್ರಮವನ್ನು ಪೂರ್ಣಗೊಳಿಸಲು ಸಾಧ್ಯವಾಗಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_unknown'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ಅನುಮೋದನೆಗಾಗಿ ಸಲ್ಲಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.submitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆ ಸ್ವಯಂಚಾಲಿತವಾಗಿ ಅನುಮೋದನೆಯಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.auto_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ಅನುಮೋದಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ತಿರಸ್ಕರಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ಹಿಂಪಡೆಯಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.withdrawn'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ನಿಮ್ಮ ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ಅನುಮೋದಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ನಿಮ್ಮ ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಯನ್ನು ತಿರಸ್ಕರಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಒಂದು ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆ ನಿಮ್ಮ ಅನುಮೋದನೆಗಾಗಿ ಕಾಯುತ್ತಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_pending_approval'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮೇಲ್ಮನವಿ ಸಂಖ್ಯೆ ನೀಡುವುದು ಕಡ್ಡಾಯ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_appeal_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆಗೆ ಕಾರಣ ನೀಡುವುದು ಕಡ್ಡಾಯ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ನಿಮ್ಮ ಗುರುತನ್ನು ಸ್ಥಾಪಿಸಲು ಸಾಧ್ಯವಾಗಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_identity_unresolved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಈ ಮೇಲ್ಮನವಿಯನ್ನು ಸದ್ಯಕ್ಕೆ ಯಾರಿಗೂ ವಹಿಸಲಾಗಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ನಿಮಗೆ ವಹಿಸಲಾದ ಮೇಲ್ಮನವಿಯ ಮರುವಹಿಸುವಿಕೆಯನ್ನು ಮಾತ್ರ ನೀವು ಕೋರಬಹುದು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಈ ಮೇಲ್ಮನವಿಗಾಗಿ ಒಂದು ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆ ಈಗಾಗಲೇ ಬಾಕಿ ಇದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_already_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮರುವಹಿಸುವಿಕೆ ಕೋರಿಕೆ ಕಂಡುಬಂದಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_request_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಈ ಕೋರಿಕೆಯ ಮೇಲೆ ಈಗಾಗಲೇ ನಿರ್ಧಾರ ತೆಗೆದುಕೊಳ್ಳಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕೋರಿಕೆಯನ್ನು ಸಲ್ಲಿಸಿದ ಅಧಿಕಾರಿ ಮಾತ್ರ ಅದನ್ನು ಹಿಂಪಡೆಯಬಹುದು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_requester'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಒಂದು ಕರಡು ಅನುಮತಿಸಿದ ಅವಧಿಯನ್ನು ಮೀರಿಯೂ ಸ್ವೀಕರಿಸದೆ ಉಳಿದಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.unclaimed_draft'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕರಡನ್ನು ಸ್ವೀಕರಿಸಲಾಗಿದೆ ಎಂದು ಗುರುತಿಸಲಾಗಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claimed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಈ ಕರಡನ್ನು ನಿಮಗೆ ವಹಿಸಲಾಗಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claim_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮೇಲಿನ ಹಂತಕ್ಕೆ ಕಳುಹಿಸುವ ಪರಿಶೀಲನೆ ಪೂರ್ಣಗೊಂಡಿದೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.sweep_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮೇಲ್ಮನವಿ ಪ್ರಾಧಿಕಾರ ವಹಿಸುವಿಕೆ ನಿರ್ವಹಣೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.console_title'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಅಧಿಕಾರಿಗಳ ಗುಂಪು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.officer_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕೆಲಸದ ಹೊರೆ ಮಿತಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಪ್ರಸ್ತುತ ಕೆಲಸದ ಹೊರೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.current_workload'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಸಕ್ರಿಯ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_active'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ನಿಷ್ಕ್ರಿಯ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_inactive'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ರಜೆಯಲ್ಲಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_on_leave'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮಿತಿಯಲ್ಲಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.at_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಅರ್ಹ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.eligible'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಆಯ್ಕೆ ಮಾಡಿದವರನ್ನು ಸಕ್ರಿಯಗೊಳಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_activate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಆಯ್ಕೆ ಮಾಡಿದವರನ್ನು ನಿಷ್ಕ್ರಿಯಗೊಳಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_deactivate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಮಿತಿಯನ್ನು ಸಂಪಾದಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.edit_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಭಾಷಾ ಕೌಶಲ್ಯಗಳು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.skill_languages'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಕಾರಣ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reason'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ದೃಢೀಕರಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.confirm'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ರದ್ದುಗೊಳಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.cancel'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಎಲ್ಲವನ್ನೂ ಆಯ್ಕೆ ಮಾಡಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.select_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಬಾಕಿ ಇರುವ ಮರುವಹಿಸುವಿಕೆ ಅನುಮೋದನೆಗಳು', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.pending_approvals'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಅನುಮೋದಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.approve'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ತಿರಸ್ಕರಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reject'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಸರತಿಯನ್ನು ಮರುಸಮತೋಲನಗೊಳಿಸಿ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.rebalance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಲೆಕ್ಕಪರಿಶೋಧನಾ ದಾಖಲೆ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.audit_trail'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'kn', 'ಈ ಗುಂಪಿನಲ್ಲಿ ಯಾವುದೇ ಅಧಿಕಾರಿಗಳಿಲ್ಲ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.no_officers'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'kn');

-- ── ml ──
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'അപ്പീൽ നിങ്ങൾക്ക് നൽകിയിട്ടുണ്ട്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'സാധാരണ ജോലിഭാര പരിധിക്ക് മുകളിൽ അപ്പീൽ നൽകിയിട്ടുണ്ട്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.assigned_under_grace'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഭാഷാ സംബന്ധമായ കൈകാര്യത്തിനായി അപ്പീൽ നിങ്ങൾക്ക് നൽകിയിട്ടുണ്ട്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.vernacular_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'അഡ്മിനിസ്ട്രേറ്റർ അപ്പീൽ നിങ്ങൾക്ക് നൽകിയിട്ടുണ്ട്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.manual_override'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'നിലവിൽ ചുമതല നൽകാൻ ഒരു ഓഫീസറും ലഭ്യമല്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_empty'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'എല്ലാ ഓഫീസർമാരും അവരുടെ ജോലിഭാര പരിധിയിൽ എത്തിയിട്ടുണ്ട്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.pool_exhausted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'സ്വമേധയാ ചുമതല നൽകുന്നതിന് കാരണം നൽകേണ്ടത് നിർബന്ധമാണ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'തിരഞ്ഞെടുത്ത ഓഫീസർ ചുമതലാ സംഘത്തിൽ ഇല്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_not_in_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'തിരഞ്ഞെടുത്ത ഓഫീസർ നിഷ്ക്രിയമാണ് അല്ലെങ്കിൽ അവധിയിലാണ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.assignment.error_target_unavailable'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ജോലിഭാര പരിധി പുതുക്കി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ജോലിഭാര പരിധി പുതുക്കി. പുനഃസന്തുലനം അഡ്മിനിസ്ട്രേറ്ററുടെ നടപടിക്കായി കാത്തിരിക്കുന്നു.', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.threshold_updated_rebalance_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഭാഷാ വൈദഗ്ധ്യം പുതുക്കി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.skills_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഓഫീസറുടെ നില പുതുക്കി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_updated'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഓഫീസറുടെ നില ഇതിനകം ഈ മൂല്യത്തിൽ തന്നെ നിശ്ചയിച്ചിരുന്നു', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.activation_unchanged'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'തിരഞ്ഞെടുത്ത എല്ലാ ഓഫീസർമാരും പുതുക്കി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ചില ഓഫീസർമാരെ പുതുക്കാൻ കഴിഞ്ഞില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.bulk_activation_partial'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ക്യൂ പുനഃസന്തുലിതമാക്കി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.rebalance_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഈ ഓഫീസർക്ക് ഇപ്പോഴും തീർപ്പാകാത്ത പരാതികൾ നൽകിയിട്ടുണ്ട്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_warning_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഈ ഓഫീസർക്ക് തീർപ്പാകാത്ത പരാതികൾ ഇല്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.deactivate_no_pending_work'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'തീർപ്പാകാത്ത പരാതികൾ ഉണ്ടായിരുന്നിട്ടും ഒരു ഓഫീസറെ നിഷ്ക്രിയമാക്കി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.notify_deactivated_with_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ചുമതലാ സംഘത്തിൽ ഓഫീസറെ കണ്ടെത്തിയില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_officer_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ജോലിഭാര പരിധി ന്യൂനമായിരിക്കാൻ കഴിയില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_negative'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ജോലിഭാര പരിധിയുടെ മൂല്യം നൽകേണ്ടത് നിർബന്ധമാണ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_threshold_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ചുരുങ്ങിയത് ഒരു ഓഫീസറെ തിരഞ്ഞെടുക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_no_officers_selected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഈ ഓഫീസർക്ക് തീർപ്പാകാത്ത പരാതികൾ ഉള്ളതിനാൽ സ്ഥിരീകരണം ആവശ്യമാണ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_confirmation_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ചുമതലാ വിഭാഗം നൽകേണ്ടത് നിർബന്ധമാണ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_role_group_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഈ നടപടി പൂർത്തിയാക്കാൻ കഴിഞ്ഞില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.pool.error_unknown'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'പുനർചുമതലാ അഭ്യർത്ഥന അനുമതിക്കായി സമർപ്പിച്ചു', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.submitted'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'പുനർചുമതലാ അഭ്യർത്ഥന സ്വയമേവ അനുവദിച്ചു', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.auto_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'പുനർചുമതലാ അഭ്യർത്ഥന അനുവദിച്ചു', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'പുനർചുമതലാ അഭ്യർത്ഥന നിരസിച്ചു', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'പുനർചുമതലാ അഭ്യർത്ഥന പിൻവലിച്ചു', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.withdrawn'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'നിങ്ങളുടെ പുനർചുമതലാ അഭ്യർത്ഥന അനുവദിച്ചു', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_approved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'നിങ്ങളുടെ പുനർചുമതലാ അഭ്യർത്ഥന നിരസിച്ചു', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_rejected'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഒരു പുനർചുമതലാ അഭ്യർത്ഥന നിങ്ങളുടെ അനുമതിക്കായി കാത്തിരിക്കുന്നു', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.notify_pending_approval'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'അപ്പീൽ നമ്പർ നൽകേണ്ടത് നിർബന്ധമാണ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_appeal_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'പുനർചുമതലാ അഭ്യർത്ഥനയ്ക്ക് കാരണം നൽകേണ്ടത് നിർബന്ധമാണ്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_reason_required'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'നിങ്ങളുടെ വ്യക്തിത്വം സ്ഥാപിക്കാൻ കഴിഞ്ഞില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_identity_unresolved'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഈ അപ്പീൽ നിലവിൽ ആർക്കും നൽകിയിട്ടില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_assigned'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'നിങ്ങൾക്ക് നൽകിയ അപ്പീലിന്റെ പുനർചുമതല മാത്രമേ നിങ്ങൾ അഭ്യർത്ഥിക്കാൻ കഴിയൂ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഈ അപ്പീലിനായി ഒരു പുനർചുമതലാ അഭ്യർത്ഥന ഇതിനകം തീർപ്പാകാതെ ഉണ്ട്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_already_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'പുനർചുമതലാ അഭ്യർത്ഥന കണ്ടെത്തിയില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_request_not_found'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഈ അഭ്യർത്ഥനയിൽ ഇതിനകം തീരുമാനമെടുത്തിട്ടുണ്ട്', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_pending'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'അഭ്യർത്ഥന ഉന്നയിച്ച ഓഫീസർക്ക് മാത്രമേ അത് പിൻവലിക്കാൻ കഴിയൂ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.reassign.error_not_requester'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഒരു കരട് അനുവദിച്ച സമയത്തിനു ശേഷവും സ്വീകരിക്കപ്പെടാതെ കിടന്നു', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.unclaimed_draft'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'കരട് സ്വീകരിച്ചതായി അടയാളപ്പെടുത്തി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claimed'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഈ കരട് നിങ്ങൾക്ക് നൽകിയിട്ടില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.claim_not_holder'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഉയർന്ന തലത്തിലേക്ക് കൈമാറ്റ പരിശോധന പൂർത്തിയായി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.escalation.sweep_complete'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'അപ്പീൽ അധികാരി ചുമതലാ ഭരണനിർവഹണം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.console_title'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഓഫീസർ സംഘം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.officer_pool'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ജോലിഭാര പരിധി', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'നിലവിലുള്ള ജോലിഭാരം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.current_workload'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'സജീവം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_active'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'നിഷ്ക്രിയം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_inactive'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'അവധിയിൽ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.status_on_leave'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'പരിധിയിൽ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.at_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'യോഗ്യൻ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.eligible'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'തിരഞ്ഞെടുത്തവ സജീവമാക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_activate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'തിരഞ്ഞെടുത്തവ നിഷ്ക്രിയമാക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.bulk_deactivate'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'പരിധി തിരുത്തുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.edit_threshold'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഭാഷാ വൈദഗ്ധ്യം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.skill_languages'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'കാരണം', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reason'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'സ്ഥിരീകരിക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.confirm'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'റദ്ദാക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.cancel'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'എല്ലാം തിരഞ്ഞെടുക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.select_all'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'തീർപ്പാകാത്ത പുനർചുമതലാ അനുമതികൾ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.pending_approvals'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'അനുവദിക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.approve'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'നിരസിക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.reject'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ക്യൂ പുനഃസന്തുലിതമാക്കുക', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.rebalance'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഓഡിറ്റ് രേഖ', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.audit_trail'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');
INSERT INTO translations (translation_key_id, locale, `value`, updated_at)
SELECT k.id, 'ml', 'ഈ സംഘത്തിൽ ഓഫീസർമാരില്ല', NOW(6) FROM translation_keys k
 WHERE k.code = 'aa.admin.no_officers'
   AND NOT EXISTS (SELECT 1 FROM translations t
                    WHERE t.translation_key_id = k.id AND t.locale = 'ml');

