package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.event.ComplaintCreatedEvent;
import com.hrms.cms.service.ComplaintOfficeResolutionService.OfficeResolution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * The side effects every complaint-creation path must perform, kept in one place so the public
 * portal and the CRPC email intake cannot drift apart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintCreationFinalizer {

    private final ComplaintOfficeResolutionService officeResolutionService;
    private final NodalOfficerRecordService nodalOfficerRecordService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Stamps the owning Ombudsman office. Call before officer assignment — the returned office code
     * is what scopes the assignment to officers of that office.
     */
    public OfficeResolution applyOffice(Complaint complaint, String department) {
        OfficeResolution resolution = officeResolutionService.resolve(complaint, department);
        complaint.setRegionalOffice(resolution.officeName());
        return resolution;
    }

    /**
     * Call after the complaint row is saved, while still inside the creation transaction. The nodal
     * officer record is written now; the Kafka event is dispatched only once the transaction commits.
     */
    public void afterSave(Complaint saved) {
        nodalOfficerRecordService.createForComplaint(saved);
        applicationEventPublisher.publishEvent(new ComplaintCreatedEvent(saved));
    }
}
