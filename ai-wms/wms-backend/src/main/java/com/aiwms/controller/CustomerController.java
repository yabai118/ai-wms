package com.aiwms.controller;

import com.aiwms.common.Result;
import com.aiwms.dto.CustomerVO;
import com.aiwms.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户接口
 *
 * <p>只有一个下拉查询——新建出库单时要选客户。
 * 不做分页：这是查找型接口，按关键字返回少量结果即可。
 */
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * GET /api/customers?keyword=xxx&limit=50
     */
    @GetMapping
    public Result<List<CustomerVO>> search(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Integer limit) {
        return Result.success(customerService.search(keyword, limit));
    }
}
