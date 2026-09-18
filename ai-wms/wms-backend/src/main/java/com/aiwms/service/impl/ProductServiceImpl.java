package com.aiwms.service.impl;

import com.aiwms.common.BusinessException;
import com.aiwms.dto.ProductQuery;
import com.aiwms.dto.ProductVO;
import com.aiwms.entity.Product;
import com.aiwms.entity.ProductSku;
import com.aiwms.mapper.ProductMapper;
import com.aiwms.mapper.ProductSkuMapper;
import com.aiwms.service.ProductService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor          // Lombok：为 final 字段生成构造器，实现构造器注入
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    @Override
    public IPage<ProductVO> pageProducts(ProductQuery query) {
        // ① 构造查询条件
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getReference())) {
            wrapper.like(Product::getReference, query.getReference());
        }
        if (StringUtils.hasText(query.getAbcClass())) {
            wrapper.eq(Product::getAbcClass, query.getAbcClass());
        }
        wrapper.orderByAsc(Product::getId);

        // ② 分页查询
        Page<Product> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Product> productPage = productMapper.selectPage(page, wrapper);

        // ③ 批量查尺码数量（一次 SQL 查完，避免 N+1 问题）
        List<Long> productIds = productPage.getRecords().stream()
                .map(Product::getId).toList();
        Map<Long, Long> sizeCountMap = countSizesByProductIds(productIds);

        // ④ 转 VO
        List<ProductVO> voList = productPage.getRecords().stream()
                .map(p -> {
                    ProductVO vo = new ProductVO();
                    vo.setId(p.getId());
                    vo.setReference(p.getReference());
                    vo.setAbcClass(p.getAbcClass());
                    vo.setSector(p.getSector());
                    vo.setSizeCount(sizeCountMap.getOrDefault(p.getId(), 0L).intValue());
                    return vo;
                }).toList();

        // ⑤ 用原分页信息 + 转换后的数据，返回新 Page
        Page<ProductVO> result = new Page<>(productPage.getCurrent(),
                productPage.getSize(), productPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public ProductVO getProductDetail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在: id=" + id);
        }

        List<String> skuCodes = listSkuCodes(id);

        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setReference(product.getReference());
        vo.setAbcClass(product.getAbcClass());
        vo.setSector(product.getSector());
        vo.setSizes(skuCodes);
        vo.setSizeCount(skuCodes.size());
        return vo;
    }

    @Override
    public List<String> listSkuCodes(Long productId) {
        List<ProductSku> skus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getProductId, productId)
                        .orderByAsc(ProductSku::getSizeUs));
        return skus.stream().map(ProductSku::getSkuCode).toList();
    }

    @Override
    public long countProducts() {
        return productMapper.selectCount(null);
    }

    /**
     * 批量统计每个商品款有多少个尺码
     *
     * <p>为什么不在循环里逐个查：那是典型的 N+1 查询问题——
     * 一页 20 条就要查 20 次数据库。这里一次查完再分组。
     */
    private Map<Long, Long> countSizesByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ProductSku> all = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>()
                        .in(ProductSku::getProductId, productIds));
        return all.stream().collect(
                Collectors.groupingBy(ProductSku::getProductId, Collectors.counting()));
    }
}
