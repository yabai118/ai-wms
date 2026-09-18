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
}
