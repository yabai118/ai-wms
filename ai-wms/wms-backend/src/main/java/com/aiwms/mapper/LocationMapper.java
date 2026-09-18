package com.aiwms.mapper;

import com.aiwms.entity.Location;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface LocationMapper extends BaseMapper<Location> {

    /**
     * 按库区统计库位数量与占用情况
     * <p>用于「库区概览」和地图页的统计面板
     */
    @Select("""
            SELECT a.area_code       AS areaCode,
                   a.area_name       AS areaName,
                   COUNT(l.id)       AS totalCount,
                   SUM(CASE WHEN l.location_type = 1 THEN 1 ELSE 0 END) AS pickCount,
                   SUM(CASE WHEN l.location_type = 0 THEN 1 ELSE 0 END) AS storeCount
            FROM warehouse_area a
            LEFT JOIN location l ON l.area_id = a.id
            GROUP BY a.id, a.area_code, a.area_name
            ORDER BY a.area_code
            """)
    List<Map<String, Object>> countByArea();

    /**
     * 库位类型统计
     */
    @Select("""
            SELECT location_type AS type, COUNT(*) AS cnt
            FROM location
            GROUP BY location_type
            ORDER BY location_type
            """)
    List<Map<String, Object>> countByType();
}
