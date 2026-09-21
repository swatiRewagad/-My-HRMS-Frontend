package com.hrms.cms.config;

import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fills in the REGULATED_ENTITIES columns that {@link DataInitializer} leaves null, so the complaint
 * screens have something to show when an officer picks an entity.
 *
 * <p>DataInitializer seeds each entity with a name, a department and a category only. The RBIO Entity
 * Details section shows four fields that follow the picked entity — Entity Name, Module Name, Entity
 * Category and Entity Type — and the first three are derived from the category, but Entity Type reads
 * {@code entityTypeDetail}, which nothing in the application ever writes. The result was a field that
 * rendered a dash for every entity in the system. The same is true of {@code city} and {@code state},
 * which the entity picker copies onto the complaint.
 *
 * <p><b>The classifications here are derived by rule, not taken from the RBI register.</b> Every bank
 * category maps to "Scheduled Commercial Bank", and an NBFC's bucket is read off its own name — a name
 * containing "Housing Finance" is a housing finance company, and so on. That keeps this from asserting a
 * regulatory classification about a named company that nobody here verified. Only {@code city} and
 * {@code state} are per-entity, and only for the four public sector banks whose head office city is
 * unambiguous; every other entity keeps a null city, which is the honest value for "not imported yet".
 * A real deployment gets all of this from the entity master import, not from here, which is why this
 * runs on dev-local only.
 *
 * <p>Writes only where the column is currently null, so a row an operator has corrected locally
 * survives a restart. Runs on every start rather than skipping when the table is non-empty, because
 * DataInitializer adds entities over time and a newly added one would otherwise never be classified.
 *
 * <p>Ordered after {@link ForwardTargetDevDataLoader} and before {@link RbioDevDataLoader}, so the
 * entities the dev complaints point at are already classified by the time those complaints exist.
 */
@Slf4j
@Component
@Profile("dev-local")
@Order(16)
@RequiredArgsConstructor
public class RegulatedEntityDetailDevDataLoader implements CommandLineRunner {

    private final RegulatedEntityRepository regulatedEntityRepository;

    private record HeadOffice(String city, String state) { }

    /** Head office city and state, for the entities the RBIO dev complaints are filed against. */
    private static final Map<String, HeadOffice> HEAD_OFFICES = Map.of(
            "State Bank of India", new HeadOffice("Mumbai", "Maharashtra"),
            "Punjab National Bank", new HeadOffice("New Delhi", "Delhi"),
            "Bank of Baroda", new HeadOffice("Vadodara", "Gujarat"),
            "Canara Bank", new HeadOffice("Bengaluru", "Karnataka"));

    @Override
    public void run(String... args) {
        List<RegulatedEntity> entities = regulatedEntityRepository.findAll();
        if (entities.isEmpty()) {
            log.warn("No regulated entities to classify — check that DataInitializer ran");
            return;
        }

        int classified = 0;
        int located = 0;

        for (RegulatedEntity entity : entities) {
            if (isBlank(entity.getEntityTypeDetail())) {
                String detail = entityTypeDetailFor(entity.getEntityType(), entity.getName());
                if (detail != null) {
                    entity.setEntityTypeDetail(detail);
                    classified++;
                }
            }

            HeadOffice office = HEAD_OFFICES.get(entity.getName());
            if (office != null && isBlank(entity.getCity()) && isBlank(entity.getState())) {
                entity.setCity(office.city());
                entity.setState(office.state());
                located++;
            }
        }

        if (classified == 0 && located == 0) {
            log.info("Regulated entity details already present — nothing to add");
            return;
        }

        regulatedEntityRepository.saveAll(entities);
        log.info("Classified {} regulated entity/entities and set a head office on {}. These are "
                        + "dev fixtures derived from the entity's category and name, not the RBI register.",
                classified, located);
    }

    /**
     * The sub-classification the Entity Type field shows, one level below the category.
     *
     * <p>Returns null for a category with no meaningful level below it, rather than repeating the
     * category — {@link RegulatedEntity#entityTypeDisplayFor} already falls back to the category, so a
     * null here shows the category instead of a duplicated string.
     */
    private String entityTypeDetailFor(String category, String name) {
        if (category == null) return null;
        String lower = name == null ? "" : name.toLowerCase();

        return switch (category) {
            case "Public Sector Bank", "Private Sector Bank", "Foreign Bank",
                 "Small Finance Bank", "Payments Bank", "Regional Rural Bank" -> "Scheduled Commercial Bank";
            case "Cooperative Bank" -> lower.contains("state co-operative")
                    ? "State Co-operative Bank"
                    : "Urban Co-operative Bank";
            case "NBFC" -> nbfcBucket(lower);
            case "Payment System Operator" -> "Non-bank Payment System Operator";
            case "Payment Infrastructure" -> "Authorised Payment System Operator";
            default -> null;
        };
    }

    /**
     * Read off the company's own name: a name that says "Housing Finance" is a housing finance company
     * and a name that says "Microfinance" is an MFI. Anything else falls to NBFC-ICC, which is RBI's
     * residual bucket for an NBFC that is not in a special category.
     */
    private String nbfcBucket(String lowerName) {
        if (lowerName.contains("housing finance") || lowerName.contains("home finance")
                || lowerName.contains("home first") || lowerName.contains("housing")
                || lowerName.contains("shelter")) {
            return "Housing Finance Company";
        }
        if (lowerName.contains("microfinance") || lowerName.contains("micro finance")
                || lowerName.contains("grameen") || lowerName.contains("creditcare")) {
            return "Microfinance Institution";
        }
        return "Investment and Credit Company";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
