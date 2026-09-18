package com.aiwms.controller;

import com.aiwms.common.Result;
import com.aiwms.dto.LocationMapVO;
import com.aiwms.dto.LocationQuery;
import com.aiwms.dto.LocationVO;
import com.aiwms.service.LocationService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 库位管理接口
 */
@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /**
     * 分页查询库位
     * <p>GET /api/locations?areaId=1&locationType=1
     */
    @GetMapping
    public Result<IPage<LocationVO>> page(LocationQuery query) {
        return Result.success(locationService.pageLocations(query));
    }

    /**
     * 库位地图数据（全量点位，供前端绘图）
     * <p>GET /api/locations/map?locationType=1
     */
    @GetMapping("/map")
    public Result<List<LocationMapVO>> mapPoints(
            @RequestParam(required = false) Integer locationType) {
        return Result.success(locationService.listMapPoints(locationType));
    }

    /**
     * 库区统计
     * <p>GET /api/locations/areas
     */
    @GetMapping("/areas")
    public Result<List<Map<String, Object>>> areas() {
        return Result.success(locationService.countByArea());
    }

    /**
     * 库位类型统计
     * <p>GET /api/locations/types
     */
    @GetMapping("/types")
    public Result<List<Map<String, Object>>> types() {
        return Result.success(locationService.countByType());
    }

    /**
     * 库位总数
     * <p>GET /api/locations/count
     */
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(locationService.countLocations());
    }
}
