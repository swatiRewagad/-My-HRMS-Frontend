package com.rbi.cms.search.config;

import com.rbi.cms.common.enums.DepartmentConstants;
import com.rbi.cms.common.exception.CmsException;
import com.rbi.cms.search.dto.OfficerPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentOfficerArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String LOCAL_PROFILE = "dev-local";

    private final Environment environment;

    @Value("${cms.dev.officer-username:rbio_do_user1}")
    private String devUserName;

    @Value("${cms.dev.officer-department:" + DepartmentConstants.DEPT_RBIO + "}")
    private String devDepartment;

    @Value("${cms.dev.officer-regional-office:Bangalore}")
    private String devRegionalOffice;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentOfficer.class)
                && OfficerPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof OfficerAuthenticationToken token) {
            return token.getOfficer();
        }

        // Local profiles run a permitAll chain, so there is no token to convert. Substituting a fixed
        // principal keeps the endpoint usable there without a second code path in the service.
        if (isLocalProfile()) {
            log.warn("No authenticated officer; substituting the '{}' development principal for department {}, regional office {}",
                    devUserName, devDepartment, devRegionalOffice);
            return OfficerPrincipal.builder()
                    .userName(devUserName)
                    .subject(devUserName)
                    .displayName(devUserName)
                    .roles(List.of())
                    .department(devDepartment)
                    .regionalOffice(devRegionalOffice)
                    .build();
        }

        // Never fall back to an unscoped principal: department is the tenancy boundary, so an
        // unresolved officer must end the request rather than search every department's complaints.
        throw new CmsException("Authentication is required to search complaints.", HttpStatus.UNAUTHORIZED);
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains(LOCAL_PROFILE);
    }
}
