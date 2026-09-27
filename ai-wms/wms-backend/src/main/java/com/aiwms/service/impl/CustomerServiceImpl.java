package com.aiwms.service.impl;

import com.aiwms.dto.CustomerVO;
import com.aiwms.entity.Customer;
import com.aiwms.mapper.CustomerMapper;
import com.aiwms.service.CustomerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 客户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;

    /** 下拉默认返回条数——客户有 588 个，不能一次性全返回 */
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    @Override
    public List<CustomerVO> search(String keyword, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);

        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 编码或名称命中即可——下拉框里用户可能记得哪个就输哪个
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Customer::getCustCode, kw)
                    .or().like(Customer::getCustName, kw));
        }
        wrapper.orderByAsc(Customer::getId).last("LIMIT " + size);

        return customerMapper.selectList(wrapper).stream().map(c -> {
            CustomerVO vo = new CustomerVO();
            vo.setId(c.getId());
            vo.setCustCode(c.getCustCode());
            vo.setCustName(c.getCustName());
            return vo;
        }).toList();
    }
}
