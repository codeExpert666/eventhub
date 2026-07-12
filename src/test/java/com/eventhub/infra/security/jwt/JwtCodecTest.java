package com.eventhub.infra.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.eventhub.infra.security.config.AuthTokenProperties;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtCodecTest {

    private static final String SECRET = "eventhub-test-jwt-secret-for-jwt-codec-test";
    private static final String ISSUER = "eventhub-test";

    private AuthTokenProperties authTokenProperties;
    private JwtCodec jwtCodec;

    @BeforeEach
    void setUp() {
        authTokenProperties = new AuthTokenProperties();
        authTokenProperties.setIssuer(ISSUER);
        authTokenProperties.getAccessToken().setSigningSecret(SECRET);
        authTokenProperties.getAccessToken().setTtl(Duration.ofMinutes(30));
        authTokenProperties.getRefreshToken().setTtl(Duration.ofDays(30));
        jwtCodec = new JwtCodec(authTokenProperties);
    }

    @Test
    void generateAndParseAccessTokenShouldKeepRequiredClaims() {
        JwtClaims claims = new JwtClaims(
                1001L,
                "jwt-id-1001",
                "session-id-1001",
                JwtClaims.ACCESS_TOKEN_TYPE
        );

        JwtClaims parsedClaims = jwtCodec.parseAccessToken(jwtCodec.generateAccessToken(claims));

        assertThat(parsedClaims.subjectId()).isEqualTo(1001L);
        assertThat(parsedClaims.tokenId()).isEqualTo("jwt-id-1001");
        assertThat(parsedClaims.sessionId()).isEqualTo("session-id-1001");
        assertThat(parsedClaims.tokenType()).isEqualTo(JwtClaims.ACCESS_TOKEN_TYPE);
    }

    @Test
    void generateAccessTokenShouldRejectWrongTokenType() {
        JwtClaims claims = new JwtClaims(
                1001L,
                "jwt-id-1001",
                "session-id-1001",
                "refresh"
        );

        assertThatThrownBy(() -> jwtCodec.generateAccessToken(claims))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT typ must be access");
    }

    @Test
    void parseAccessTokenShouldRejectWrongTokenType() {
        String token = buildTokenWithTokenType("refresh");

        assertThatThrownBy(() -> jwtCodec.parseAccessToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT typ must be access");
    }

    @Test
    void parseAccessTokenShouldRejectMissingJwtId() {
        String token = buildTokenWithoutJwtId();

        assertThatThrownBy(() -> jwtCodec.parseAccessToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT jti is required");
    }

    @Test
    void parseAccessTokenShouldRejectMissingSessionId() {
        String token = buildTokenWithoutSessionId();

        assertThatThrownBy(() -> jwtCodec.parseAccessToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT sid is required");
    }

    private String buildTokenWithoutJwtId() {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject("1001")
                .claim("sid", "session-id-1001")
                .claim("typ", JwtClaims.ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(30))))
                .signWith(signingKey())
                .compact();
    }

    private String buildTokenWithoutSessionId() {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject("1001")
                .id("jwt-id-1001")
                .claim("typ", JwtClaims.ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(30))))
                .signWith(signingKey())
                .compact();
    }

    private String buildTokenWithTokenType(String tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject("1001")
                .id("jwt-id-1001")
                .claim("sid", "session-id-1001")
                .claim("typ", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(30))))
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
