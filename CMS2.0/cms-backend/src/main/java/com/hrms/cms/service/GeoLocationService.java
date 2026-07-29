package com.hrms.cms.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;

@Service
public class GeoLocationService {

    private static final Logger log = LoggerFactory.getLogger(GeoLocationService.class);

    @Value("${cms.geo.maxmind-db-path:#{null}}")
    private String maxmindDbPath;

    @Value("${cms.geo.enabled:false}")
    private boolean enabled;

    private DatabaseReader reader;

    @PostConstruct
    void init() {
        if (!enabled || maxmindDbPath == null || maxmindDbPath.isBlank()) {
            log.info("GeoLocation service disabled (cms.geo.enabled=false or no DB path)");
            return;
        }

        File dbFile = new File(maxmindDbPath);
        if (!dbFile.exists()) {
            log.warn("MaxMind GeoLite2 DB not found at: {}. GeoLocation disabled.", maxmindDbPath);
            return;
        }

        try {
            reader = new DatabaseReader.Builder(dbFile).build();
            log.info("GeoLocation service initialized with MaxMind DB: {}", maxmindDbPath);
        } catch (Exception e) {
            log.error("Failed to load MaxMind DB: {}", e.getMessage());
        }
    }

    @PreDestroy
    void destroy() {
        if (reader != null) {
            try { reader.close(); } catch (Exception ignored) {}
        }
    }

    public Optional<GeoResult> lookup(String ipAddress) {
        if (reader == null) return Optional.empty();

        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()) {
                return Optional.empty();
            }

            CityResponse response = reader.city(addr);

            GeoResult result = new GeoResult();
            result.setCountry(response.getCountry() != null ? response.getCountry().getName() : null);
            result.setCountryCode(response.getCountry() != null ? response.getCountry().getIsoCode() : null);
            result.setState(response.getMostSpecificSubdivision() != null ? response.getMostSpecificSubdivision().getName() : null);
            result.setStateCode(response.getMostSpecificSubdivision() != null ? response.getMostSpecificSubdivision().getIsoCode() : null);
            result.setCity(response.getCity() != null ? response.getCity().getName() : null);
            result.setPostalCode(response.getPostal() != null ? response.getPostal().getCode() : null);

            if (response.getLocation() != null) {
                result.setLatitude(response.getLocation().getLatitude());
                result.setLongitude(response.getLocation().getLongitude());
            }

            return Optional.of(result);
        } catch (Exception e) {
            log.debug("GeoIP lookup failed for {}: {}", ipAddress, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isAvailable() {
        return reader != null;
    }

    public static class GeoResult {
        private String country;
        private String countryCode;
        private String state;
        private String stateCode;
        private String city;
        private String postalCode;
        private Double latitude;
        private Double longitude;

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        public String getStateCode() { return stateCode; }
        public void setStateCode(String stateCode) { this.stateCode = stateCode; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }

        public Map<String, Object> toMap() {
            return Map.of(
                "country", country != null ? country : "",
                "countryCode", countryCode != null ? countryCode : "",
                "state", state != null ? state : "",
                "stateCode", stateCode != null ? stateCode : "",
                "city", city != null ? city : "",
                "postalCode", postalCode != null ? postalCode : ""
            );
        }
    }
}
