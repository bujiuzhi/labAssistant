package com.materialslab.api.projects.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** 验证项目收藏与取消收藏接口映射成对存在。 */
class ProjectControllerMappingTest {
    @Test
    void 收藏和取消收藏应分别支持Post与Delete() throws NoSuchMethodException {
        PostMapping follow = ProjectController.class
                .getMethod("follow", String.class)
                .getAnnotation(PostMapping.class);
        DeleteMapping unfollow = ProjectController.class
                .getMethod("unfollow", String.class)
                .getAnnotation(DeleteMapping.class);

        assertThat(follow.value()).containsExactly("/projects/{projectKey}/follow");
        assertThat(unfollow.value()).containsExactly("/projects/{projectKey}/follow");
    }

    @Test
    void 版本写接口应由控制器统一返回428() {
        for (String methodName : java.util.List.of("update", "archive")) {
            var method = java.util.Arrays.stream(ProjectController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            RequestHeader header = method.getParameters()[1].getAnnotation(RequestHeader.class);
            assertThat(header).isNotNull();
            assertThat(header.required()).isFalse();
        }
    }
}
