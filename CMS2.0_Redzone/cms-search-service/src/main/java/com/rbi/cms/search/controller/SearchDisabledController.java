package com.rbi.cms.search.controller;

import com.rbi.cms.common.dto.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * What answers the search endpoints when {@code cms.search.enabled=false}.
 *
 * Without this the kill switch would remove the real controller and leave the routes 404ing, which a
 * caller cannot distinguish from a wrong URL. 503 says "deliberately off, retry later", which is the
 * honest answer and the one the UI already handles.
 */
@RestController
@ConditionalOnProperty(name = "cms.search.enabled", havingValue = "false")
public class SearchDisabledController {

    @RequestMapping(value = "/api/v1/search/**",
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<ApiResponse<Void>> disabled() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Search is disabled by configuration."));
    }
}
