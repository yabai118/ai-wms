package com.aiwms.service;

import com.aiwms.dto.LocationMapVO;
import com.aiwms.dto.LocationQuery;
import com.aiwms.dto.LocationVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

public interface LocationService {

    /** 分页查询库位列表 */
    IPage<LocationVO> pageLocations(LocationQuery query);

    /** 库位地图数据（全量点位） */
    List<LocationMapVO> listMapPoints(Integer locationType);

    /** 库区统计 */
    List<Map<String, Object>> countByArea();

    /** 库位类型统计 */
    List<Map<String, Object>> countByType();

    /** 库位总数 */
    long countLocations();
}
