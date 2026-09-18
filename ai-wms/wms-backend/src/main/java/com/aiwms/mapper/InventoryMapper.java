package com.aiwms.mapper;

import com.aiwms.entity.Inventory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 入库增加库存（原子操作）
     *
     * <p>用原生 UPDATE 而不是先查再改：避免并发下的丢失更新。
     * <p>qty_available 同步增加（可用量 = 现有 - 已分配 - 冻结）。
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
}
