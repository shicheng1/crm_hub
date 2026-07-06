package com.zmd.order.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    @Test
    void generateTokenShouldContainUserClaims() {
        JwtUtil jwtUtil = new JwtUtil("zmd-order-approval-test-secret-key-32-bytes", 2, 7);

        String token = jwtUtil.generateToken(1001L, "admin", "ADMIN");
        Claims claims = jwtUtil.parseToken(token);

        assertEquals(1001L, claims.get("userId", Long.class));
        assertEquals("admin", claims.get("username", String.class));
        assertEquals("ADMIN", claims.get("role", String.class));
        assertTrue(jwtUtil.isTokenValid(token));
    }

    @Test
    void isTokenValidShouldReturnFalseForMalformedToken() {
        JwtUtil jwtUtil = new JwtUtil("zmd-order-approval-test-secret-key-32-bytes", 2, 7);

        assertFalse(jwtUtil.isTokenValid("not-a-valid-token"));
    }

    @Test
    void shortSecretShouldBePaddedToSupportHs256() {
        JwtUtil jwtUtil = new JwtUtil("short-secret", 2, 7);

        String token = jwtUtil.generateToken(1002L, "user1", "USER");

        assertTrue(jwtUtil.isTokenValid(token));
    }

    @Test
    void generateRefreshTokenShouldContainUserClaims() {
        JwtUtil jwtUtil = new JwtUtil("zmd-order-approval-test-secret-key-32-bytes", 2, 7);

        String token = jwtUtil.generateRefreshToken(1003L, "approver", "APPROVER");
        Claims claims = jwtUtil.parseToken(token);

        assertEquals(1003L, claims.get("userId", Long.class));
        assertEquals("approver", claims.get("username", String.class));
        assertEquals("APPROVER", claims.get("role", String.class));
        assertTrue(jwtUtil.isTokenValid(token));
        assertEquals(7, jwtUtil.getRefreshExpireDays());
    }
}
