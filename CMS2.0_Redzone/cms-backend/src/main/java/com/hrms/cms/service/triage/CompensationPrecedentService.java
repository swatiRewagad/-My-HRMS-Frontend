package com.hrms.cms.service.triage;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.service.SimilarCasesService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationPrecedentService {

    private static final BigDecimal MAX_CONSEQUENTIAL_LOSS = new BigDecimal("3000000");
    private static final BigDecimal MAX_TIME_HARASSMENT = new BigDecimal("300000");
    private static final BigDecimal MAX_TOTAL_COMPENSATION = MAX_CONSEQUENTIAL_LOSS.add(MAX_TIME_HARASSMENT);

    private final ComplaintRepository complaintRepository;
    private final SimilarCasesService similarCasesService;

    @Cacheable(value = "copilot-precedent", key = "'comp-band-' + #complaint.id")
    public CompensationBand getCompensationBand(Complaint complaint) {
        List<BigDecimal> historicalAwards = findHistoricalAwards(complaint);

        if (historicalAwards.isEmpty()) {
            return CompensationBand.builder()
                    .available(false)
                    .message("No comparable awarded cases found for precedent analysis")
                    .maxConsequentialLoss(MAX_CONSEQUENTIAL_LOSS)
                    .maxTimeHarassment(MAX_TIME_HARASSMENT)
                    .build();
        }

        BigDecimal minAward = historicalAwards.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxAward = historicalAwards.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal avgAward = historicalAwards.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(historicalAwards.size()), 2, RoundingMode.HALF_UP);

        BigDecimal medianAward = calculateMedian(historicalAwards);

        return CompensationBand.builder()
                .available(true)
                .minAward(minAward)
                .maxAward(maxAward.min(MAX_TOTAL_COMPENSATION))
                .averageAward(avgAward)
                .medianAward(medianAward)
                .sampleSize(historicalAwards.size())
                .maxConsequentialLoss(MAX_CONSEQUENTIAL_LOSS)
                .maxTimeHarassment(MAX_TIME_HARASSMENT)
                .message(String.format("Based on %d comparable awarded cases (range: ₹%s – ₹%s)",
                        historicalAwards.size(), formatAmount(minAward), formatAmount(maxAward)))
                .build();
    }

    private List<BigDecimal> findHistoricalAwards(Complaint complaint) {
        List<BigDecimal> awards = new ArrayList<>();

        if (complaint.getCategoryId() != null) {
            List<Complaint> similarByCat = complaintRepository.findByCategoryIdOrderByCreatedAtDesc(complaint.getCategoryId());
            similarByCat.stream()
                    .filter(c -> c.getAwardAmount() != null && c.getAwardAmount().compareTo(BigDecimal.ZERO) > 0)
                    .map(Complaint::getAwardAmount)
                    .forEach(awards::add);
        }

        if (complaint.getBankId() != null && awards.size() < 5) {
            List<Complaint> similarByBank = complaintRepository.findByBankIdOrderByCreatedAtDesc(complaint.getBankId());
            similarByBank.stream()
                    .filter(c -> c.getAwardAmount() != null && c.getAwardAmount().compareTo(BigDecimal.ZERO) > 0)
                    .map(Complaint::getAwardAmount)
                    .limit(20)
                    .forEach(awards::add);
        }

        return awards.stream().distinct().sorted().toList();
    }

    private BigDecimal calculateMedian(List<BigDecimal> sorted) {
        if (sorted.isEmpty()) return BigDecimal.ZERO;
        List<BigDecimal> s = sorted.stream().sorted().toList();
        int mid = s.size() / 2;
        if (s.size() % 2 == 0) {
            return s.get(mid - 1).add(s.get(mid)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }
        return s.get(mid);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("100000")) >= 0) {
            return amount.divide(new BigDecimal("100000"), 2, RoundingMode.HALF_UP) + " lakh";
        }
        return amount.toPlainString();
    }

    @Getter
    @Builder
    @NoArgsConstructor(force = true)
    @AllArgsConstructor
    public static class CompensationBand implements Serializable {
        private final boolean available;
        private final BigDecimal minAward;
        private final BigDecimal maxAward;
        private final BigDecimal averageAward;
        private final BigDecimal medianAward;
        private final int sampleSize;
        private final BigDecimal maxConsequentialLoss;
        private final BigDecimal maxTimeHarassment;
        private final String message;
    }
}
