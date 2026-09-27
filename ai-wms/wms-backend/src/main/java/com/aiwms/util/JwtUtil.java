package com.aiwms.util;

import com.aiwms.common.UserContext;
import com.aiwms.entity.Staff;
import com.aiwms.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与解析
 *
 * <p>JWT 是「自包含」的凭证——用户信息编码在 token 里并由服务端签名，
 * 所以校验时不用查库，直接验签 + 读 claim 即可。代价是<b>签发后无法单方面作废</b>，
 * 只能等它过期；真要做「立即踢下线」得引入黑名单（本项目暂不做，登出只清前端）。
 */
@Slf4j
@Component
public class JwtUtil {

    /** ⚠️ HS256 要求密钥至少 256 位（32 字节），短了 jjwt 会直接抛异常 */
    private final SecretKey key;

    private final long expireHours;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-hours:8}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    /**
     * 签发 token
     *
     * <p>登录名（subject）与显示名（claim {@code name}）都来自 <b>员工档案</b>——
     * 账号表里已经不再存这两个字段了。
     *
     * @param user  账号（提供 id / 角色 / 关联员工）
     * @param staff 该账号关联的员工（提供工号与姓名）
     */
    public String sign(SysUser user, Staff staff) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(staff.getStaffCode())          // 工号就是登录名
                .claim("uid", user.getId())
                .claim("sid", staff.getId())
                .claim("name", staff.getStaffName())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签
     *
     * @throws io.jsonwebtoken.JwtException 签名不对、过期、格式错误都会抛
     */
    public UserContext.CurrentUser parse(String token) {
        Claims c = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new UserContext.CurrentUser(
                c.get("uid", Long.class),
                c.get("sid", Long.class),
                c.getSubject(),                          // 工号
                c.get("name", String.class),             // 姓名
                c.get("role", String.class),
                c.getIssuedAt() == null ? null : c.getIssuedAt().getTime() / 1000);
    }
}
