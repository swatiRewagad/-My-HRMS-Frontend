package com.hrms.cms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeoLocationServiceTest {

    private GeoLocationService service;

    @BeforeEach
    void setup() {
        service = new GeoLocationService();
        ReflectionTestUtils.setField(service, "enabled", false);
        ReflectionTestUtils.setField(service, "maxmindDbPath", null);
    }

    @Test
    @DisplayName("should return empty when service is disabled")
    void shouldReturnEmptyWhenDisabled() {
        Optional<GeoLocationService.GeoResult> result = service.lookup("8.8.8.8");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should not be available when disabled")
    void shouldNotBeAvailableWhenDisabled() {
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("should return empty for loopback address")
    void shouldReturnEmptyForLoopback() {
        ReflectionTestUtils.setField(service, "enabled", true);
        Optional<GeoLocationService.GeoResult> result = service.lookup("127.0.0.1");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("GeoResult toMap should contain expected keys")
    void geoResultShouldHaveExpectedKeys() {
        GeoLocationService.GeoResult result = new GeoLocationService.GeoResult();
        result.setCountry("India");
        result.setCountryCode("IN");
        result.setState("Maharashtra");
        result.setStateCode("MH");
        result.setCity("Mumbai");
        result.setPostalCode("400001");

        var map = result.toMap();
        assertThat(map).containsEntry("country", "India");
        assertThat(map).containsEntry("countryCode", "IN");
        assertThat(map).containsEntry("state", "Maharashtra");
        assertThat(map).containsEntry("city", "Mumbai");
        assertThat(map).containsEntry("postalCode", "400001");
    }

    @Test
    @DisplayName("GeoResult toMap should handle null values")
    void geoResultShouldHandleNulls() {
        GeoLocationService.GeoResult result = new GeoLocationService.GeoResult();
        var map = result.toMap();
        assertThat(map).containsEntry("country", "");
        assertThat(map).containsEntry("state", "");
        assertThat(map).containsEntry("city", "");
    }
}
