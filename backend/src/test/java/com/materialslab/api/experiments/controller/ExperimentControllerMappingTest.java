package com.materialslab.api.experiments.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

/** 验证实验版本写接口将缺失 If-Match 统一转换为业务 428。 */
class ExperimentControllerMappingTest {
    @Test
    void 版本写接口的请求头应允许进入业务校验() {
        for (String methodName : List.of("update", "transition")) {
            var method = Arrays.stream(ExperimentController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            RequestHeader header = method.getParameters()[1].getAnnotation(RequestHeader.class);
            assertThat(header).isNotNull();
            assertThat(header.required()).isFalse();
        }
    }
}
