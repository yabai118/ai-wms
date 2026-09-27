package com.aiwms.service;

import com.aiwms.dto.CustomerVO;

import java.util.List;

public interface CustomerService {

    /**
     * 查询客户列表（供下拉选择用）
     *
     * @param keyword 模糊匹配客户编码或名称，可为空
     * @param limit   返回条数上限
     */
    List<CustomerVO> search(String keyword, Integer limit);
}
