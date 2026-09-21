package com.hrms.cms.config;

import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.RegulatedEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The loader exists so the Entity Details section stops showing a dash for Entity Type, so the
 * assertions are about what a picked entity ends up displaying rather than about row counts.
 */
@DataJpaTest
@DisplayName("RegulatedEntityDetailDevDataLoader")
class RegulatedEntityDetailDevDataLoaderTest {

    @Autowired private RegulatedEntityRepository regulatedEntityRepository;

    private RegulatedEntityDetailDevDataLoader loader;

    @BeforeEach
    void setUp() {
        loader = new RegulatedEntityDetailDevDataLoader(regulatedEntityRepository);
    }

    private RegulatedEntity save(String name, String category) {
        return regulatedEntityRepository.save(RegulatedEntity.builder()
                .name(name).department("RBIO").entityType(category).portalEnabled(true).build());
    }

    private String detailOf(String name) {
        return regulatedEntityRepository.findAll().stream()
                .filter(e -> name.equals(e.getName()))
                .findFirst().orElseThrow()
                .getEntityTypeDetail();
    }

    @Nested
    @DisplayName("classification")
    class Classification {

        @Test
        @DisplayName("gives every bank category a sub-classification, so Entity Type is never blank")
        void classifiesBanks() {
            save("State Bank of India", "Public Sector Bank");
            save("HDFC Bank Limited", "Private Sector Bank");
            save("Citibank N.A.", "Foreign Bank");
            save("Au Small Finance Bank Limited", "Small Finance Bank");
            save("Airtel Payments Bank Limited", "Payments Bank");

            loader.run();

            assertThat(regulatedEntityRepository.findAll())
                    .extracting(RegulatedEntity::getEntityTypeDetail)
                    .containsOnly("Scheduled Commercial Bank");
        }

        @Test
        @DisplayName("reads an NBFC's bucket off its own name rather than asserting one")
        void classifiesNbfcsByName() {
            save("LIC Housing Finance Limited", "NBFC");
            save("CreditAccess Grameen Limited", "NBFC");
            save("Bajaj Finance Limited", "NBFC");

            loader.run();

            assertThat(detailOf("LIC Housing Finance Limited")).isEqualTo("Housing Finance Company");
            assertThat(detailOf("CreditAccess Grameen Limited")).isEqualTo("Microfinance Institution");
            assertThat(detailOf("Bajaj Finance Limited")).isEqualTo("Investment and Credit Company");
        }

        @Test
        @DisplayName("separates a state co-operative bank from an urban one")
        void classifiesCooperativeBanks() {
            save("Maharashtra State Co-operative Bank Limited", "Cooperative Bank");
            save("Cosmos Co-operative Bank Limited", "Cooperative Bank");

            loader.run();

            assertThat(detailOf("Maharashtra State Co-operative Bank Limited"))
                    .isEqualTo("State Co-operative Bank");
            assertThat(detailOf("Cosmos Co-operative Bank Limited"))
                    .isEqualTo("Urban Co-operative Bank");
        }

        @Test
        @DisplayName("leaves an unrecognised category alone, so Entity Type falls back to the category")
        void leavesUnknownCategoryNull() {
            save("Some New Kind of Entity", "Unlisted Category");

            loader.run();

            assertThat(detailOf("Some New Kind of Entity")).isNull();
            assertThat(RegulatedEntity.entityTypeDisplayFor("Unlisted Category", null))
                    .isEqualTo("Unlisted Category");
        }
    }

    @Nested
    @DisplayName("re-running")
    class ReRunning {

        @Test
        @DisplayName("does not overwrite a value an operator has already corrected")
        void preservesExistingValues() {
            RegulatedEntity entity = save("Bajaj Finance Limited", "NBFC");
            entity.setEntityTypeDetail("Infrastructure Finance Company");
            entity.setCity("Pune");
            entity.setState("Maharashtra");
            regulatedEntityRepository.save(entity);

            loader.run();

            assertThat(detailOf("Bajaj Finance Limited")).isEqualTo("Infrastructure Finance Company");
        }

        @Test
        @DisplayName("classifies an entity added after the first run")
        void classifiesLaterAdditions() {
            save("State Bank of India", "Public Sector Bank");
            loader.run();

            save("Muthoot Finance Ltd", "NBFC");
            loader.run();

            assertThat(detailOf("Muthoot Finance Ltd")).isEqualTo("Investment and Credit Company");
        }
    }

    @Nested
    @DisplayName("head office")
    class HeadOffice {

        @Test
        @DisplayName("fills city and state for the entities the dev complaints point at")
        void fillsKnownHeadOffices() {
            save("Canara Bank", "Public Sector Bank");

            loader.run();

            RegulatedEntity canara = regulatedEntityRepository.findAll().get(0);
            assertThat(canara.getCity()).isEqualTo("Bengaluru");
            assertThat(canara.getState()).isEqualTo("Karnataka");
        }

        @Test
        @DisplayName("leaves city null for an entity whose head office is not known here")
        void leavesUnknownHeadOfficeNull() {
            save("Nainital Bank Limited", "Private Sector Bank");

            loader.run();

            RegulatedEntity bank = regulatedEntityRepository.findAll().get(0);
            assertThat(bank.getCity()).isNull();
            assertThat(bank.getState()).isNull();
        }
    }
}
