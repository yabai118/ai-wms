package com.aiwms.service;

import com.aiwms.dto.ProductQuery;
import com.aiwms.dto.ProductVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 商品服务接口
 *
 * <p>为什么用接口 + 实现类分离：
 * ① 便于以后换实现（如加缓存时写个 ProductServiceCacheImpl）
 * ② 便于单元测试时 mock
 * ③ 符合「面向接口编程」
 */
public interface ProductService {

    /** 分页查询商品列表 */
    IPage<ProductVO> pageProducts(ProductQuery query);

    /** 查询商品详情（含尺码列表） */
    ProductVO getProductDetail(Long id);

    /** 查询某个商品的所有尺码编码 */
    List<String> listSkuCodes(Long productId);

    /** 统计商品款总数 */
    long countProducts();
}
