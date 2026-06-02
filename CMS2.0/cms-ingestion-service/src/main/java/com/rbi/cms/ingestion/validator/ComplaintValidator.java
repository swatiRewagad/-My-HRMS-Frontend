package com.rbi.cms.ingestion.validator;

import com.rbi.cms.common.enums.Channel;
import com.rbi.cms.common.enums.ComplaintCategory;
import com.rbi.cms.common.exception.CmsException;
import com.rbi.cms.ingestion.dto.ComplaintRegistrationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ComplaintValidator {

    public void validate(ComplaintRegistrationRequest request) {
        validateChannel(request.getChannel());
        validateCategory(request.getCategory());
        validateContactInfo(request);
    }

    private void validateChannel(String channel) {
        boolean valid = Arrays.stream(Channel.values())
                .anyMatch(c -> c.name().equals(channel));
        if (!valid) {
            throw new CmsException("Invalid channel: " + channel, HttpStatus.BAD_REQUEST, "INVALID_CHANNEL");
        }
    }

    private void validateCategory(String category) {
        boolean valid = Arrays.stream(ComplaintCategory.values())
                .anyMatch(c -> c.name().equals(category));
        if (!valid) {
            throw new CmsException("Invalid category: " + category, HttpStatus.BAD_REQUEST, "INVALID_CATEGORY");
        }
    }

    private void validateContactInfo(ComplaintRegistrationRequest request) {
        if ((request.getComplainantEmail() == null || request.getComplainantEmail().isBlank())
                && (request.getComplainantPhone() == null || request.getComplainantPhone().isBlank())) {
            throw new CmsException("At least one contact method (email or phone) is required",
                    HttpStatus.BAD_REQUEST, "CONTACT_REQUIRED");
        }
    }
}
