package com.rbi.cms.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FiltersDTO {

    private List<String> states;
    private List<String> districts;
    private List<String> years;
    private List<String> quarters;
    private List<String> meetingTypes;
    private List<String> documentTypes;
}
