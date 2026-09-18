package com.aiwms.mapper;

import com.aiwms.entity.PickingTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface PickingTaskMapper extends BaseMapper<PickingTask> {

    /**
     * 按波次查询拣货任务（含 SKU、库位、坐标信息）
     * <p>坐标用于后续的路径优化
     */
    @Select("""
            SELECT t.id            AS id,
                   t.wave_id       AS waveId,
                   t.sku_id        AS skuId,
                   s.sku_code      AS skuCode,
                   t.location_id   AS locationId,
                   l.location_code AS locationCode,
                   l.x_coord       AS xCoord,
                   l.y_coord       AS yCoord,
                   l.z_coord       AS zCoord,
                   t.qty_plan      AS qtyPlan,
                   t.qty_picked    AS qtyPicked,
                   t.seq_no        AS seqNo,
                   t.status        AS status
            FROM picking_task t
            JOIN product_sku s ON s.id = t.sku_id
            JOIN location l ON l.id = t.location_id
            WHERE t.wave_id = #{waveId}
            ORDER BY t.seq_no IS NULL, t.seq_no, t.id
            """)
    List<Map<String, Object>> listByWave(@Param("waveId") Long waveId);
}
