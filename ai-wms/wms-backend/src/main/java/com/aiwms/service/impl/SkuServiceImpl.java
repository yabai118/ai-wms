package com.aiwms.service.impl;

import com.aiwms.dto.SkuVO;
import com.aiwms.entity.Product;
import com.aiwms.entity.ProductSku;
import com.aiwms.mapper.ProductMapper;
import com.aiwms.mapper.ProductSkuMapper;
import com.aiwms.service.SkuService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {

    private final ProductSkuMapper skuMapper;
    private final ProductMapper productMapper;

    @Override
    public List<SkuVO> search(String keyword, int limit) {
        LambdaQueryWrapper<ProductSku> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 支持按 SKU 编码搜索（如 8N10W9-41）或按款号搜索（如 8N10W9）
            wrapper.like(ProductSku::getSkuCode, keyword);
        }
        wrapper.orderByAsc(ProductSku::getId);
        wrapper.last("LIMIT " + Math.min(limit, 100));

        List<ProductSku> skus = skuMapper.selectList(wrapper);
        if (skus.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> productMap = productMapper.selectBatchIds(
                        skus.stream().map(ProductSku::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Product::getId, p -> p));

        return skus.stream().map(s -> {
            SkuVO vo = new SkuVO();
            vo.setId(s.getId());
            vo.setSkuCode(s.getSkuCode());
            vo.setSizeUs(s.getSizeUs());
            Product p = productMap.get(s.getProductId());
            if (p != null) {
                vo.setReference(p.getReference());
                vo.setAbcClass(p.getAbcClass());
            }
            return vo;
        }).toList();
    }
}
