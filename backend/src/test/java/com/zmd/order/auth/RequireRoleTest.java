package com.zmd.order.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RequireRole 注解测试")
class RequireRoleTest {

    @Test
    @DisplayName("注解可以标记在方法上并读取值")
    void shouldReadAnnotationValue() throws NoSuchMethodException {
        // 用 TestController 的方法来验证注解可读取
        RequireRole annotation = TestController.class
                .getMethod("adminOnly")
                .getAnnotation(RequireRole.class);
        assertNotNull(annotation);
        String[] roles = annotation.value();
        assertEquals(1, roles.length);
        assertEquals("ADMIN", roles[0]);
    }

    @Test
    @DisplayName("注解支持多角色")
    void shouldSupportMultipleRoles() throws NoSuchMethodException {
        RequireRole annotation = TestController.class
                .getMethod("multiRole")
                .getAnnotation(RequireRole.class);
        assertNotNull(annotation);
        String[] roles = annotation.value();
        assertEquals(2, roles.length);
        assertEquals("ADMIN", roles[0]);
        assertEquals("APPROVER", roles[1]);
    }

    @Test
    @DisplayName("无注解的方法返回 null")
    void shouldReturnNull_whenNoAnnotation() throws NoSuchMethodException {
        RequireRole annotation = TestController.class
                .getMethod("noAnnotation")
                .getAnnotation(RequireRole.class);
        assertNull(annotation);
    }

    /** 测试用 Controller */
    static class TestController {
        @RequireRole("ADMIN")
        public void adminOnly() {}

        @RequireRole({"ADMIN", "APPROVER"})
        public void multiRole() {}

        public void noAnnotation() {}
    }
}
