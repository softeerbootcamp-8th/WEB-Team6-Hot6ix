package com.hot6ix.upbid.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

class ErrorTypeCodeUniquenessTest {

    private static final String BASE_PACKAGE = "com.hot6ix.upbid";

    @Test
    @DisplayName("모든 ErrorType 구현체의 errorCode는 서로 중복되지 않는다")
    void allErrorCodesAreUnique() throws ClassNotFoundException {

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorType.class));

        Map<Integer, String> codeToOwner = new HashMap<>();

        for (BeanDefinition beanDefinition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> clazz = Class.forName(beanDefinition.getBeanClassName());
            if (!clazz.isEnum() || clazz.getEnclosingClass() != null) {
                continue; // 중첩 클래스는 테스트 픽스처(예: GlobalExceptionHandlerTest.TestErrorType)로 보고 제외
            }

            for (Object constant : clazz.getEnumConstants()) {
                ErrorType errorType = (ErrorType) constant;
                String owner = clazz.getSimpleName() + "." + constant;
                String duplicateOwner = codeToOwner.putIfAbsent(errorType.getErrorCode(), owner);

                assertThat(duplicateOwner)
                        .as("errorCode %d가 %s와 %s에서 중복됩니다.", errorType.getErrorCode(), duplicateOwner, owner)
                        .isNull();
            }
        }
    }
}
