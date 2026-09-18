package com.aiwms.service.impl;

import com.aiwms.dto.LocationMapVO;
import com.aiwms.dto.LocationQuery;
import com.aiwms.dto.LocationVO;
import com.aiwms.entity.Location;
import com.aiwms.entity.WarehouseArea;
import com.aiwms.mapper.LocationMapper;
import com.aiwms.mapper.WarehouseAreaMapper;
import com.aiwms.service.LocationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final LocationMapper locationMapper;
    private final WarehouseAreaMapper areaMapper;

    /** 库位类型名称 */
    private static final Map<Integer, String> TYPE_NAMES = Map.of(
            0, "存储区", 1, "拣货区", 2, "收货区", 3, "发货区");

    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "空闲", 1, "占用", 2, "锁定");

    @Override
    public IPage<LocationVO> pageLocations(LocationQuery query) {
        LambdaQueryWrapper<Location> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getLocationCode())) {
            wrapper.like(Location::getLocationCode, query.getLocationCode());
        }
        if (query.getAreaId() != null) {
            wrapper.eq(Location::getAreaId, query.getAreaId());
        }
        if (query.getLocationType() != null) {
            wrapper.eq(Location::getLocationType, query.getLocationType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Location::getStatus, query.getStatus());
        }
        // 按库区、排、层排序，符合仓库实际查看习惯
        wrapper.orderByAsc(Location::getAreaId, Location::getYCoord, Location::getXCoord);

        Page<Location> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Location> result = locationMapper.selectPage(page, wrapper);

        // 一次性把库区查出来做映射（避免 N+1）
        Map<Long, String> areaMap = areaMapper.selectList(null).stream()
                .collect(Collectors.toMap(WarehouseArea::getId, WarehouseArea::getAreaCode));

        List<LocationVO> voList = result.getRecords().stream()
                .map(l -> toVO(l, areaMap))
                .toList();

        Page<LocationVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public List<LocationMapVO> listMapPoints(Integer locationType) {
        LambdaQueryWrapper<Location> wrapper = new LambdaQueryWrapper<>();
        if (locationType != null) {
            wrapper.eq(Location::getLocationType, locationType);
        }
        wrapper.orderByAsc(Location::getId);

        Map<Long, String> areaMap = areaMapper.selectList(null).stream()
                .collect(Collectors.toMap(WarehouseArea::getId, WarehouseArea::getAreaCode));

        return locationMapper.selectList(wrapper).stream().map(l -> {
            LocationMapVO vo = new LocationMapVO();
            vo.setCode(l.getLocationCode());
            vo.setX(l.getXCoord());
            vo.setY(l.getYCoord());
            vo.setZ(l.getZCoord());
            vo.setType(l.getLocationType());
            vo.setArea(areaMap.get(l.getAreaId()));
            return vo;
        }).toList();
    }

    @Override
    public List<Map<String, Object>> countByArea() {
        return locationMapper.countByArea();
    }

    @Override
    public List<Map<String, Object>> countByType() {
        return locationMapper.countByType();
    }

    @Override
    public long countLocations() {
        return locationMapper.selectCount(null);
    }

    private LocationVO toVO(Location l, Map<Long, String> areaMap) {
        LocationVO vo = new LocationVO();
        vo.setId(l.getId());
        vo.setLocationCode(l.getLocationCode());
        vo.setAreaCode(areaMap.get(l.getAreaId()));
        vo.setLocationType(l.getLocationType());
        vo.setLocationTypeName(TYPE_NAMES.getOrDefault(l.getLocationType(), "未知"));
        vo.setXCoord(l.getXCoord());
        vo.setYCoord(l.getYCoord());
        vo.setZCoord(l.getZCoord());
        vo.setCapacity(l.getCapacity());
        vo.setUsedSlots(l.getUsedSlots());
        vo.setUsageRate(l.getCapacity() == null || l.getCapacity() == 0 ? 0
                : (int) Math.round(100.0 * l.getUsedSlots() / l.getCapacity()));
        vo.setStatus(l.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(l.getStatus(), "未知"));
        return vo;
    }
}
