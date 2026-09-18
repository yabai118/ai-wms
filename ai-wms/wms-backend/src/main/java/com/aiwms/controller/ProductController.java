package com.aiwms.controller;

import com.aiwms.common.Result;
import com.aiwms.dto.ProductQuery;
import com.aiwms.dto.ProductVO;
import com.aiwms.service.ProductService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品管理接口
 *
 * <p>RESTful 风格：资源用名词复数（/products），方法用 HTTP 动词表达语义
 */
@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 分页查询商品列表
     * <p>GET /api/products?pageNum=1&pageSize=10&abcClass=A
     */
    @GetMapping
    public Result<IPage<ProductVO>> page(ProductQuery query) {
        return Result.success(productService.pageProducts(query));
    }

    /**
     * 查询商品详情（含尺码列表）
     * <p>GET /api/products/{id}
     */
    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.success(productService.getProductDetail(id));
    }

    /**
     * 查询某个商品的尺码编码列表
     * <p>GET /api/products/{id}/skus
     */
    @GetMapping("/{id}/skus")
    public Result<List<String>> skus(@PathVariable Long id) {
        return Result.success(productService.listSkuCodes(id));
    }

    /**
     * 商品款总数
     * <p>GET /api/products/count
     */
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(productService.countProducts());
    }
}
