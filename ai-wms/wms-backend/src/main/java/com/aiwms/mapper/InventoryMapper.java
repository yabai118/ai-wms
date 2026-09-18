package com.aiwms.mapper;

import com.aiwms.entity.Inventory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 库存 Mapper
 *
 * <p><b>本类的核心是「条件更新」——防超卖的关键</b>
 */
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 入库增加库存（原子操作）
     */
    @Update("""
            UPDATE inventory
            SET qty = qty + #{qty},
                qty_available = qty_available + #{qty},
                version = version + 1,
                updated_at = NOW()
            WHERE sku_id = #{skuId} AND location_id = #{locationId}
            """)
    int increaseQty(@Param("skuId") Long skuId,
                    @Param("locationId") Long locationId,
                    @Param("qty") Integer qty);

    /**
     * ★ 分配库存（条件更新，防超卖）
     *
     * <p>关键在 WHERE 里的 <code>qty_available >= #{qty}</code>：
     * 数据库在执行这条 UPDATE 时会加行锁，「判断够不够」和「扣减」是**一个原子操作**，
     * 不会出现「两个请求都读到 100，各自扣 80，最后超卖」的情况。
     *
     * <p>返回值说明：
     * <ul>
     *   <li>1 → 分配成功</li>
     *   <li>0 → 可用库存不足，分配失败</li>
     * </ul>
     */
    @Update("""
            UPDATE inventory
            SET qty_allocated = qty_allocated + #{qty},
                qty_available = qty_available - #{qty},
                version = version + 1,
                updated_at = NOW()
            WHERE sku_id = #{skuId}
              AND location_id = #{locationId}
              AND qty_available >= #{qty}
            """)
    int allocateQty(@Param("skuId") Long skuId,
                    @Param("locationId") Long locationId,
                    @Param("qty") Integer qty);

    /**
     * 拣货确认：已分配 → 已拣出（总数不变）
     */
    @Update("""
            UPDATE inventory
            SET qty_allocated = qty_allocated - #{qty},
                qty_picked = qty_picked + #{qty},
                version = version + 1,
                updated_at = NOW()
            WHERE sku_id = #{skuId}
              AND location_id = #{locationId}
              AND qty_allocated >= #{qty}
            """)
    int pickQty(@Param("skuId") Long skuId,
                @Param("locationId") Long locationId,
                @Param("qty") Integer qty);

    /**
     * 发货确认：真正扣减库存总数
     */
    @Update("""
            UPDATE inventory
            SET qty = qty - #{qty},
                qty_picked = qty_picked - #{qty},
                version = version + 1,
                updated_at = NOW()
            WHERE sku_id = #{skuId}
              AND location_id = #{locationId}
              AND qty_picked >= #{qty}
            """)
    int shipQty(@Param("skuId") Long skuId,
                @Param("locationId") Long locationId,
                @Param("qty") Integer qty);

    /**
     * 释放分配（超时未拣货时调用，把占用的库存还回去）
     */
    @Update("""
            UPDATE inventory
            SET qty_allocated = qty_allocated - #{qty},
                qty_available = qty_available + #{qty},
                version = version + 1,
                updated_at = NOW()
            WHERE sku_id = #{skuId}
              AND location_id = #{locationId}
              AND qty_allocated >= #{qty}
            """)
    int releaseQty(@Param("skuId") Long skuId,
                   @Param("locationId") Long locationId,
                   @Param("qty") Integer qty);

    /**
     * 查询某 SKU 有可用库存的库位（按可用量降序）
     *
     * <p>用于分配时挑选库位。优先拣货区。
     */
    @Select("""
            SELECT i.id            AS id,
                   i.sku_id        AS skuId,
                   i.location_id   AS locationId,
                   i.qty_available AS qtyAvailable,
                   l.location_code AS locationCode,
                   l.location_type AS locationType
            FROM inventory i
            JOIN location l ON l.id = i.location_id
            WHERE i.sku_id = #{skuId}
              AND i.qty_available > 0
            ORDER BY l.location_type DESC, i.qty_available DESC
            """)
    List<Map<String, Object>> findAvailableLocations(@Param("skuId") Long skuId);

    /**
     * 冻结库存（从可用量挪到冻结量）
     */
    @Update("""
            UPDATE inventory
            SET qty_onhold = qty_onhold + #{qty},
                qty_available = qty_available - #{qty},
                version = version + 1,
                updated_at = NOW()
            WHERE id = #{inventoryId}
              AND qty_available >= #{qty}
            """)
    int freezeQty(@Param("inventoryId") Long inventoryId, @Param("qty") Integer qty);

    /**
     * 解冻库存（从冻结量还回可用量）
     */
    @Update("""
            UPDATE inventory
            SET qty_onhold = qty_onhold - #{qty},
                qty_available = qty_available + #{qty},
                version = version + 1,
                updated_at = NOW()
            WHERE id = #{inventoryId}
              AND qty_onhold >= #{qty}
            """)
    int unfreezeQty(@Param("inventoryId") Long inventoryId, @Param("qty") Integer qty);

    /**
     * 库存总览统计
     */
    @Select("""
            SELECT COUNT(*)                        AS recordCount,
                   COUNT(DISTINCT sku_id)          AS skuCount,
                   COUNT(DISTINCT location_id)     AS locationCount,
                   COALESCE(SUM(qty), 0)           AS totalQty,
                   COALESCE(SUM(qty_allocated), 0) AS totalAllocated,
                   COALESCE(SUM(qty_picked), 0)    AS totalPicked,
                   COALESCE(SUM(qty_onhold), 0)    AS totalOnhold,
                   COALESCE(SUM(qty_available), 0) AS totalAvailable
            FROM inventory
            """)
    Map<String, Object> summary();

    /**
     * 按业务类型统计流水
     */
    @Select("""
            SELECT biz_type AS bizType, COUNT(*) AS cnt, COALESCE(SUM(qty_delta),0) AS delta
            FROM inventory_transaction
            GROUP BY biz_type
            ORDER BY cnt DESC
            """)
    List<Map<String, Object>> countByBizType();

    /**
     * 查某 SKU 的可用库存总量（跨所有库位求和）——供 Redis 缓存服务回源用
     *
     * @return 可用量；SKU 不存在或无库存时返回 null
     */
    @Select("""
            SELECT SUM(i.qty_available)
            FROM inventory i
            JOIN product_sku s ON s.id = i.sku_id
            WHERE s.sku_code = #{skuCode}
            """)
    Integer sumAvailableBySkuCode(@Param("skuCode") String skuCode);

    /**
     * ★ 库存对账：找出「库存表的 qty」与「流水累加值」不一致的记录
     *
     * <p>正常情况下两者应该相等（每一笔库存变动都有流水）。
     * <p>如果真的查出不一致，说明有操作改了库存却没记流水——这是严重的 bug 信号。
     */
    @Select("""
            SELECT i.id            AS id,
                   s.sku_code      AS skuCode,
                   l.location_code AS locationCode,
                   i.qty           AS stockQty,
                   COALESCE(t.ledger_qty, 0) AS ledgerQty,
                   (i.qty - COALESCE(t.ledger_qty, 0)) AS diff
            FROM inventory i
            JOIN product_sku s ON s.id = i.sku_id
            JOIN location l ON l.id = i.location_id
            LEFT JOIN (
                SELECT sku_id, location_id, SUM(qty_delta) AS ledger_qty
                FROM inventory_transaction
                GROUP BY sku_id, location_id
            ) t ON t.sku_id = i.sku_id AND t.location_id = i.location_id
            WHERE i.qty != COALESCE(t.ledger_qty, 0)
            LIMIT 50
            """)
    List<Map<String, Object>> reconcile();
}
