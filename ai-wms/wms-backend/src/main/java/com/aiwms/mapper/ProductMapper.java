package com.aiwms.mapper;

import com.aiwms.entity.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 商品款 Mapper
 *
 * <p>继承 BaseMapper 后自动获得 CRUD 方法（selectById / insert / updateById / deleteById …），
 * 不用写一行 SQL。复杂查询可以在这里自定义方法，配合 resources/mapper/ProductMapper.xml
 */
public interface ProductMapper extends BaseMapper<Product> {
}
