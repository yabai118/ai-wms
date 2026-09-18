package com.aiwms.service;

import com.aiwms.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 库存缓存服务（Cache-Aside 模式）
 *
 * ## 缓存什么
 *
 * 缓存**单个 SKU 的可用库存总量**（跨所有库位求和）。
 * 这是出库前校验的高频读取——每次分配库存都要判断"这个 SKU 还有多少可用"。
 *
 * ## 为什么用 Cache-Aside（旁路缓存）
 *
 * <pre>
 * 读：查缓存 → 命中直接返回；未命中 → 查 DB → 回填缓存
 * 写：**先更新 DB，再删除缓存**（不是更新缓存）
 * </pre>
 *
 * **为什么写的时候删缓存而不是更新缓存**：
 * 更新缓存有并发问题——两个请求先后更新 DB 和缓存，顺序错乱会导致
 * 缓存里是旧值。删除缓存则让下次读自然回源，更简单也更不容易出错。
 *
 * ## 三个防护
 *
 * | 问题 | 现象 | 这里的做法 |
 * |------|------|-----------|
 * | **缓存穿透** | 查不存在的 key，每次都打到 DB | 空结果也缓存（短 TTL） |
 * | **缓存雪崩** | 大量 key 同时过期，DB 被瞬时打垮 | TTL 加**随机偏移** |
 * | **缓存击穿** | 热点 key 过期瞬间，大量请求同时回源 | 本场景数据量小、回源快，暂不处理（见注释） |
 *
 * ## 缓存什么时候失效
 *
 * 库存变动的地方（入库上架 / 分配 / 拣货 / 发货 / 冻结解冻）都要**主动删缓存**，
 * 保证「下次读能拿到最新值」。见各 Service 里的 {@code stockCacheService.evict(...)} 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final InventoryMapper inventoryMapper;
    private final com.aiwms.mapper.ProductSkuMapper skuMapper;

    /** 缓存 key 前缀 */
    private static final String KEY_PREFIX = "wms:stock:sku:";

    /** 基础 TTL（秒） */
    private static final long BASE_TTL_SECONDS = 60;

    /** 空值 TTL（秒）——防穿透用，短一点 */
    private static final long NULL_TTL_SECONDS = 15;

    /**
     * 查 SKU 的可用库存总量
     *
     * @param skuCode SKU 编码，如 8N10W9-11
     * @return 可用量；SKU 不存在时返回 0
     */
    public int getAvailableStock(String skuCode) {
        String key = KEY_PREFIX + skuCode;

        // ① 查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            // 空值标记：说明之前查过、确实没数据（防穿透）
            if ("NULL".equals(cached)) {
                return 0;
            }
            return ((Number) cached).intValue();
        }

        // ② 未命中 → 查数据库
        Integer total = inventoryMapper.sumAvailableBySkuCode(skuCode);

        // ③ 回填缓存
        if (total == null) {
            // 防穿透：空结果也缓存，但 TTL 短
            redisTemplate.opsForValue().set(key, "NULL",
                    Duration.ofSeconds(NULL_TTL_SECONDS));
            return 0;
        }
        // 防雪崩：TTL 加随机偏移（60~90 秒）
        long ttl = BASE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(0, 30);
        redisTemplate.opsForValue().set(key, total, Duration.ofSeconds(ttl));
        return total;
    }

    /**
     * 删除缓存（库存变动后必须调用）
     *
     * <p>注意是**删除**不是更新——让下次读自然回源，避免并发下的更新顺序问题。
     */
    public void evict(String skuCode) {
        if (skuCode == null) return;
        Boolean deleted = redisTemplate.delete(KEY_PREFIX + skuCode);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("库存缓存已删除: {}", skuCode);
        }
    }

    /**
     * 按 skuId 删除缓存（供业务 Service 调用——它们手里只有 skuId）
     *
     * <p>写路径多查一次 sku_code 可以接受（本来就在改数据库）；
     * 换来的是**缓存 key 用可读的 sku_code**，排查问题时在 redis-cli 里一眼能看懂。
     */
    public void evictBySkuId(Long skuId) {
        if (skuId == null) return;
        var sku = skuMapper.selectById(skuId);
        if (sku != null) {
            evict(sku.getSkuCode());
        }
    }

    /**
     * ★ 在**事务提交后**再删缓存（业务 Service 应该调这个）
     *
     * <p><b>为什么不直接在方法里删</b>：
     * 若在事务内删缓存，会出现这样的竞态：
     * <pre>
     * T1: 更新 DB（未提交）→ 删缓存
     * T2: 读缓存 miss → 读 DB（隔离级别下看到的是旧值）→ 回填缓存【旧值】
     * T1: 事务提交（DB 已是新值）
     * 结果：缓存里是旧值，和 DB 不一致 ❌
     * </pre>
     * 改成**事务提交后**再删，上述窗口就不存在了。
     *
     * <p>若当前不在事务中（无同步激活），直接删即可。
     */
    public void evictAfterCommit(Long skuId) {
        if (skuId == null) return;
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    evictBySkuId(skuId);
                                }
                            });
        } else {
            evictBySkuId(skuId);
        }
    }
}
