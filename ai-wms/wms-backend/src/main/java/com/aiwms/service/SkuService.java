package com.aiwms.service;

import com.aiwms.dto.SkuVO;

import java.util.List;

public interface SkuService {

    /** 按关键字搜索 SKU（匹配 SKU 编码或款号） */
    List<SkuVO> search(String keyword, int limit);
}
