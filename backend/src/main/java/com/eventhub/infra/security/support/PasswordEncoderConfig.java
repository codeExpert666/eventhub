package com.eventhub.infra.security.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 *
 * <p>
 * 业务代码只依赖 {@link PasswordEncoder} 接口，注册时调用 {@code encode} 保存哈希，
 * 登录时调用 {@code matches} 校验原始密码和数据库中的哈希是否匹配。
 * 不要在业务服务中直接 new {@link BCryptPasswordEncoder}，否则会让算法选择散落在业务代码里。
 * </p>
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt 密码编码器。
     *
     * <p>
     * BCrypt 会为每次加密生成随机盐，即使两个用户使用相同密码，最终哈希也不同。
     * 登录校验时必须使用 {@code matches}，不能把原始密码再次 {@code encode} 后做字符串比较。
     * </p>
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
