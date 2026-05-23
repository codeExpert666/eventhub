package com.eventhub.modules.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * refresh token 哈希工具。
 *
 * <p>
 * refresh token 具备长期凭证属性，数据库中只允许保存不可逆哈希值。
 * 当前使用标准库 SHA-256 并添加算法前缀，后续如果升级为 HMAC 或带 pepper 的方案，
 * 可以通过前缀区分旧数据和新数据的校验方式。
 * </p>
 */
@Component
public class RefreshTokenHasher {

    private static final String ALGORITHM = "SHA-256";
    private static final String STORED_PREFIX = "sha256:";

    /**
     * 对 refresh token 明文生成稳定哈希。
     *
     * @param refreshToken refresh token 明文
     * @return 带算法前缀的哈希值
     */
    public String hash(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashed = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return STORED_PREFIX + HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            /*
             * SHA-256 是 Java 标准算法，正常 JRE 必须提供。
             * 如果这里失败，说明运行时安全提供方异常，应快速失败而不是静默降级保存弱哈希。
             */
            throw new IllegalStateException("SHA-256 message digest is not available", exception);
        }
    }
}
