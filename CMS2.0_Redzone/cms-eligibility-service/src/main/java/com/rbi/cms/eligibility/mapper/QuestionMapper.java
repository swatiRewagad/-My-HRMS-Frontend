package com.rbi.cms.eligibility.mapper;

import com.rbi.cms.eligibility.dto.QuestionResponse;
import com.rbi.cms.eligibility.entity.QuestionMaster;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface QuestionMapper {

    @Mapping(target = "options", source = "options", qualifiedByName = "parseOptions")
    QuestionResponse toResponse(QuestionMaster entity);

    List<QuestionResponse> toResponseList(List<QuestionMaster> entities);

    @Named("parseOptions")
    default List<String> parseOptions(String options) {
        if (options == null || options.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(options.split("\\|"));
    }
}
