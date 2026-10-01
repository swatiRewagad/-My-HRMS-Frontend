package com.rbi.cms.workflow.service;

import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.event.ComplaintEvent;

public interface ComplaintWorkflowProcessor {
    String startComplaintWorkflow(ComplaintEvent event);
    void transitionState(String complaintId, ComplaintStatus targetStatus, String remarks);
    void escalateComplaint(String complaintId, String reason);
}
