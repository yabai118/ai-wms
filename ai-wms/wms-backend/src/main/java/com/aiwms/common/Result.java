package com.aiwms.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果
 *
 * <p>所有接口都返回这个结构，前端只需判断 code 字段：
 * <pre>
 * {
 *   "code": 200,
 *   "message": "操作成功",
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p>为什么需要统一响应：
 * ① 前端只需处理一套结构，不用管每个接口返回什么
 * ② 错误码统一管理，便于排查
 * ③ 便于以后加 traceId、耗时等公共字段
 */
@Data
public class Result<T> implements Serializable {

    /** 状态码：200 成功，其他为失败 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ---------- 成功 ----------

    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    // ---------- 失败 ----------

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
