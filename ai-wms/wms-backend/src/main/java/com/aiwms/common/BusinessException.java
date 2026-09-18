package com.aiwms.common;

import lombok.Getter;

/**
 * 业务异常
 *
 * <p>用于表达「业务规则不满足」的情况，例如：
 * <ul>
 *   <li>库存不足</li>
 *   <li>库位容量已满</li>
 *   <li>单据状态不允许该操作</li>
 * </ul>
 *
 * <p>与系统异常（NullPointerException 等）区分：
 * 业务异常是「预期内的」，需要给用户友好提示；
 * 系统异常是「bug」，需要记录日志并排查。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
