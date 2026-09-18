package com.aiwms.controller;

import com.aiwms.common.Result;
import com.aiwms.dto.SkuVO;
import com.aiwms.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SKU 查询接口（供前端下拉选择）
 */
@RestController
@RequestMapping("/skus")
@RequiredArgsConstructor
public class SkuController {

    private final SkuService skuService;

    /**
     * 搜索 SKU
     * <p>GET /api/skus/search?keyword=8N10W9&limit=20
     */
    @GetMapping("/search")
    public Result<List<SkuVO>> search(@RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(skuService.search(keyword, limit));
    }
}
