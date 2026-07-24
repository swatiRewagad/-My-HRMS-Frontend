package com.hrms.cms.service;

import java.util.List;
import java.util.Map;

public interface SimilarCasesProvider {

    List<Map<String, Object>> findSimilar(String complaintText, String category, int maxResults);

    boolean isAvailable();

    String getProviderName();
}
