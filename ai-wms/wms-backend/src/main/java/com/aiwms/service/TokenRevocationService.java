package com.aiwms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

/**
 * 令牌撤销
 *
 * <h3>为什么需要它</h3>
 *
 * <p>JWT 是<b>无状态</b>的：签发之后服务端不留记录，所以<b>无法单方面作废</b>。
 * 如果不做撤销，"改密码"就只是改了个字符串 —— 旧 token 在过期前（本项目 8 小时）
 * 照样能用，被盗的令牌等于没被收回。
 *
 * <h3>怎么做</h3>
 *
 * <p>不去维护"哪些 token 失效了"（那要存下每一个 token），
 * 而是记一个<b>时间点</b>：
 *
 * <pre>
 *   wms:auth:revoked-before:{username} = &lt;epoch 秒&gt;
 *
 *   之后每次请求：token 的签发时间 &lt; 这个时间点  →  已失效，401
 * </pre>
 *
 * <p>只需一个 key 就能让该用户**所有旧 token 一起作废**，
 * 而且 TTL 设成 token 的最长寿命即可 —— 过期后没有 token 还能活那么久，
 * 标记留着也没意义，自动清理。
 *
 * <h3>★ Redis 挂了会怎样（重要取舍）</h3>
 *
 * <p><b>fail-open：放行</b>，并打 warn 日志。理由：
 * <ul>
 *   <li>这里是"鉴权路上的一环"，Redis 抖动就让全站登不上，代价太大</li>
 *   <li>Redis 重启会丢失撤销标记，被撤销的 token 会"复活"到自然过期为止</li>
 * </ul>
 *
 * <p>要更严格就得<b>落库</b>（加一列 {@code password_changed_at}）+ 每请求查一次 DB，
 * 用可用性和性能换准确性。本项目选前者，因为这个取舍在面试里说得清，
 * 而真实系统里两种做法都很常见（取决于对"立即失效"的要求有多硬）。
 */
@Slf4j
@Service
public class TokenRevocationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final long expireHours;

    private static final String KEY_PREFIX = "wms:auth:revoked-before:";

    public TokenRevocationService(RedisTemplate<String, Object> redisTemplate,
                                  @Value("${jwt.expire-hours:8}") long expireHours) {
        this.redisTemplate = redisTemplate;
        this.expireHours = expireHours;
    }

    /**
     * 撤销某用户在此时刻之前签发的所有 token
     *
     * <p>改密码、管理员重置密码之后必须调用。
     */
    public void revokeTokensBefore(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        try {
            // TTL = token 最长寿命：更早的 token 本来就都过期了，标记无需再留
            redisTemplate.opsForValue().set(KEY_PREFIX + username, now,
                    Duration.ofHours(expireHours));
            log.info("已撤销 {} 在 {} 之前签发的令牌", username, now);
        } catch (Exception e) {
            log.error("撤销令牌失败（Redis 不可用），旧令牌将继续有效: user={} err={}",
                    username, e.getMessage());
        }
    }

    /**
     * 该 token 是否已被撤销
     *
     * @param issuedAtEpochSecond token 的签发时间（秒）
     * @return true = 已失效
     */
    public boolean isRevoked(String username, Long issuedAtEpochSecond) {
        if (!StringUtils.hasText(username) || issuedAtEpochSecond == null) {
            return false;
        }
        try {
            Object v = redisTemplate.opsForValue().get(KEY_PREFIX + username);
            if (v == null) {
                return false;
            }
            long revokedBefore = ((Number) v).longValue();
            // 严格小于：同一秒内刚签发的新 token 应当有效
            return issuedAtEpochSecond < revokedBefore;
        } catch (Exception e) {
            // fail-open，见类注释
            log.warn("撤销检查失败（Redis 不可用），放行: user={} err={}", username, e.getMessage());
            return false;
        }
    }
}
