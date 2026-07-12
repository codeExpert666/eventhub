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

    /**
     * MessageDigest 使用的摘要算法名称。
     *
     * <p>
     * SHA-256 是 Java 标准库内置的安全哈希算法，适合把 refresh token 这类随机凭证
     * 转换成不可逆摘要后再入库。这里保存算法名称，是为了避免在代码中散落硬编码字符串。
     * </p>
     */
    private static final String ALGORITHM = "SHA-256";

    /**
     * 存储到数据库中的哈希值前缀。
     *
     * <p>
     * 前缀记录当前哈希算法，后续如果升级为 HMAC、带 pepper 的方案或更换算法，
     * 可以通过前缀判断旧 token 应该用哪一种方式校验。
     * </p>
     */
    private static final String STORED_PREFIX = "sha256:";

    /**
     * 对 refresh token 明文生成稳定哈希。
     *
     * <p>
     * 这里的“稳定”指同一个 refresh token 明文每次都会生成同一个哈希结果，
     * 便于后续登录续期时用用户提交的 token 重新计算哈希并与数据库记录比对。
     * </p>
     *
     * @param refreshToken refresh token 明文
     * @return 带算法前缀的哈希值
     */
    public String hash(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }

        try {
            /*
             * MessageDigest 是 JDK 提供的通用“消息摘要”工具。
             * getInstance(ALGORITHM) 会根据算法名创建 SHA-256 摘要器，
             * 后续 digest(...) 会把输入字节计算成固定长度的 32 字节哈希结果。
             */
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);

            /*
             * Java 字符串本身不是字节序列，哈希算法处理的是 byte[]。
             * 显式使用 UTF-8 可以保证不同操作系统、不同 JVM 默认编码下结果一致。
             */
            byte[] hashed = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));

            /*
             * digest(...) 返回的是原始二进制字节，不适合直接存数据库或打印。
             * HexFormat 会把每个字节转换为两个十六进制字符，得到可读、可存储的字符串。
             */
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
