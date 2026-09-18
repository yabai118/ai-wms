package com.aiwms.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 首页看板统计查询
 *
 * <p>这些查询以聚合为主，直接写 SQL 比走 ORM 更清晰
 */
@Mapper
public interface DashboardMapper {

    /** 核心指标汇总 */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM product)                          AS productCount,
              (SELECT COUNT(*) FROM product_sku)                      AS skuCount,
              (SELECT COUNT(*) FROM location)                         AS locationCount,
              (SELECT COUNT(*) FROM customer)                         AS customerCount,
              (SELECT COUNT(*) FROM operator)                         AS operatorCount,
              (SELECT COUNT(*) FROM outbound_order)                   AS orderCount,
              (SELECT COUNT(*) FROM picking_wave)                     AS waveCount,
              (SELECT COUNT(*) FROM inbound_order)                    AS inboundCount,
              (SELECT COUNT(*) FROM inventory_transaction)            AS txCount,
              (SELECT COALESCE(SUM(qty),0) FROM inventory)            AS totalStock,
              (SELECT COALESCE(SUM(qty_available),0) FROM inventory)  AS totalAvailable,
              (SELECT COALESCE(SUM(qty_allocated),0) FROM inventory)  AS totalAllocated,
              (SELECT COALESCE(SUM(qty_picked),0) FROM inventory)     AS totalPicked,
              (SELECT COALESCE(SUM(qty_onhold),0) FROM inventory)     AS totalOnhold
            """)
    Map<String, Object> summary();

    /** 订单趋势（按天，取最近 30 天有数据的日期） */
    @Select("""
            SELECT DATE(order_time) AS date,
                   COUNT(*)         AS orderCount,
                   SUM((SELECT COALESCE(SUM(qty),0) FROM outbound_order_line l WHERE l.order_id = o.id)) AS qty
            FROM outbound_order o
            WHERE order_time IS NOT NULL
            GROUP BY DATE(order_time)
            ORDER BY date
            LIMIT 60
            """)
    List<Map<String, Object>> orderTrend();

    /**
     * ABC 分类分布
     * <p>注意：商品款数必须用 COUNT(DISTINCT p.id)，
     * 因为 JOIN 后一个商品款会对应多个 SKU，COUNT(*) 数的是 SKU 数。
     */
    @Select("""
            SELECT p.abc_class                  AS abc,
                   COUNT(DISTINCT p.id)         AS productCount,
                   COUNT(DISTINCT s.id)         AS skuCount,
                   COALESCE(SUM(i.qty),0)       AS stockQty
            FROM product p
            LEFT JOIN product_sku s ON s.product_id = p.id
            LEFT JOIN inventory i ON i.sku_id = s.id
            GROUP BY p.abc_class
            ORDER BY p.abc_class
            """)
    List<Map<String, Object>> abcDistribution();

    /** 库区占用情况 */
    @Select("""
            SELECT a.area_code  AS area,
                   COUNT(l.id)  AS total,
                   SUM(CASE WHEN l.used_slots > 0 THEN 1 ELSE 0 END) AS used,
                   SUM(CASE WHEN l.location_type = 1 THEN 1 ELSE 0 END) AS pickCount,
                   SUM(CASE WHEN l.location_type = 0 THEN 1 ELSE 0 END) AS storeCount
            FROM warehouse_area a
            LEFT JOIN location l ON l.area_id = a.id
            GROUP BY a.id, a.area_code
            ORDER BY a.area_code
            """)
    List<Map<String, Object>> areaUsage();

    /** 库位类型分布 */
    @Select("""
            SELECT location_type AS type, COUNT(*) AS cnt
            FROM location GROUP BY location_type ORDER BY location_type
            """)
    List<Map<String, Object>> locationType();

    /** 库存 TOP10（按库存量） */
    @Select("""
            SELECT s.sku_code      AS skuCode,
                   p.reference     AS reference,
                   p.abc_class     AS abcClass,
                   SUM(i.qty)      AS qty
            FROM inventory i
            JOIN product_sku s ON s.id = i.sku_id
            JOIN product p ON p.id = s.product_id
            GROUP BY s.id, s.sku_code, p.reference, p.abc_class
            ORDER BY SUM(i.qty) DESC
            LIMIT 10
            """)
    List<Map<String, Object>> topStock();

    /** 波次状态分布 */
    @Select("""
            SELECT status AS status, COUNT(*) AS cnt
            FROM picking_wave GROUP BY status ORDER BY status
            """)
    List<Map<String, Object>> waveStatus();
}
