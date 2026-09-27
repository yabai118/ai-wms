package com.aiwms.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

/**
 * 全局异常处理
 *
 * <h3>★ 为什么每个 handler 都记日志</h3>
 *
 * <p>改之前的兜底 handler 只回一句「系统繁忙，请稍后重试」，<b>什么线索都没有</b>。
 * 曾经踩过一次：Mapper 里一条 SQL 写的是改名前的表名，结果首页看板一直报
 * 「系统繁忙」——前端只看到这四个字，服务端日志被管道吞了，
 * 定位花了不少功夫。
 *
 * <p>所以现在：<b>每个 handler 都记录「方法 + 路径 + 原因」</b>。
 *
 * <h3>★ 为什么兜底要返回一个 traceId</h3>
 *
 * <p>两难：
 * <ul>
 *   <li>把堆栈/SQL 返回给前端 → <b>信息泄露</b>（表名、字段名、依赖版本全暴露），
 *       攻击者最喜欢这种报错</li>
 *   <li>什么都不返回 → 用户和客服都无从下手，只能说"就是报错了"</li>
 * </ul>
 *
 * <p>解决办法是<b>给用户一个编号，把细节留在服务端</b>：
 * <pre>
 *   用户看到：系统繁忙，请稍后重试（错误编号 a1b2c3d4）
 *   日志里是：系统异常 [traceId=a1b2c3d4] POST /api/waves/generate
 *            org.springframework.jdbc.BadSqlGrammarException: ...
 * </pre>
 * 拿到编号就能在日志里精确找到那一条，既不泄露内部细节，又能对上账。
 *
 * <h3>关于 HTTP 状态码</h3>
 *
 * <p>全部返回 <b>HTTP 200 + body 里的 code</b>，与项目既有约定一致
 * （{@link BusinessException} 也是这么做的）。所以前端判断成败
 * <b>必须看 body 里的 code，不能看 HTTP 状态码</b>——
 * 唯一例外是拦截器抛的 401/403，那个走真实状态码。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：规则不满足（库存不足、状态不允许等），不是 bug，用 warn */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(HttpServletRequest req, BusinessException e) {
        log.warn("业务异常 [{} {}] code={} msg={}",
                req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    // ==================================================================
    //  参数与请求相关的异常 —— 都是调用方的问题，给出明确提示
    // ==================================================================

    /** @Valid 校验 RequestBody 失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(HttpServletRequest req,
                                             MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        log.warn("参数校验失败 [{} {}] {}", req.getMethod(), req.getRequestURI(), msg);
        return Result.fail(400, msg);
    }

    /** 表单 / 路径参数绑定失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(HttpServletRequest req, BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数绑定失败");
        log.warn("参数绑定失败 [{} {}] {}", req.getMethod(), req.getRequestURI(), msg);
        return Result.fail(400, msg);
    }

    /** 参数类型不对，如把 abc 传给 Long */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(HttpServletRequest req,
                                           MethodArgumentTypeMismatchException e) {
        String msg = "参数 " + e.getName() + " 类型不正确";
        log.warn("参数类型不正确 [{} {}] {}", req.getMethod(), req.getRequestURI(), msg);
        return Result.fail(400, msg);
    }

    /** 少传了必填的 query 参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(HttpServletRequest req,
                                           MissingServletRequestParameterException e) {
        String msg = "缺少必填参数: " + e.getParameterName();
        log.warn("缺少参数 [{} {}] {}", req.getMethod(), req.getRequestURI(), msg);
        return Result.fail(400, msg);
    }

    /** 请求体不是合法 JSON */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpServletRequest req,
                                         HttpMessageNotReadableException e) {
        log.warn("请求体解析失败 [{} {}]", req.getMethod(), req.getRequestURI());
        return Result.fail(400, "请求体格式不正确，应为合法的 JSON");
    }

    /**
     * ★ 接口不存在
     *
     * <p><b>这条以前被兜底成 500「系统繁忙」，</b>导致调错接口时完全看不出是路径写错了。
     * 现在明确返回 404 并带上真实路径。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(HttpServletRequest req, NoResourceFoundException e) {
        String path = req.getRequestURI();
        log.warn("接口不存在 [{} {}]", req.getMethod(), path);
        return Result.fail(404, "接口不存在: " + req.getMethod() + " " + path);
    }

    /** 请求方法不对，如用 GET 调了 POST 接口 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpServletRequest req,
                                                 HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持 [{} {}] 支持: {}",
                req.getMethod(), req.getRequestURI(), e.getSupportedHttpMethods());
        return Result.fail(405, "该接口不支持 " + req.getMethod()
                + " 方法，支持: " + e.getSupportedHttpMethods());
    }

    // ==================================================================
    //  数据访问异常 —— 是服务端的问题，但可以给出比"系统繁忙"更有用的信息
    // ==================================================================

    /**
     * 唯一约束冲突
     *
     * <p>典型场景：新建账号时登录名重复、库存表 (sku, location) 撞唯一键。
     * 这类错误<b>本身是业务语义</b>（"这个已经存在了"），不该报 500。
     *
     * <p>不过多数 Service 已经先查重再插（给出更友好的提示），
     * 这里只是兜底：并发下"查重"和"插入"之间的窗口仍可能撞上。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(HttpServletRequest req, DuplicateKeyException e) {
        log.warn("唯一约束冲突 [{} {}] {}", req.getMethod(), req.getRequestURI(),
                e.getMostSpecificCause().getMessage());
        return Result.fail(400, "数据已存在，请勿重复提交");
    }

    /**
     * 其他数据库异常（SQL 写错、表不存在、字段改名等）
     *
     * <p><b>这类几乎都是代码 bug，</b>所以给一个能直接从提示里看出问题的文案
     * （"数据访问失败"而不是"系统繁忙"），细节进日志。
     */
    @ExceptionHandler(DataAccessException.class)
    public Result<Void> handleDataAccess(HttpServletRequest req, DataAccessException e) {
        String traceId = newTraceId();
        log.error("数据访问异常 [traceId={}] [{} {}]",
                traceId, req.getMethod(), req.getRequestURI(), e);
        return Result.fail(500, "数据访问失败，请稍后重试（错误编号 " + traceId + "）");
    }

    // ==================================================================
    //  兜底
    // ==================================================================

    /**
     * 其他未预期的异常
     *
     * <p>返回给用户的只有一个编号 —— <b>不返回异常类型和堆栈</b>，
     * 那会泄露内部实现（类名、依赖、代码结构）。编号用来和日志对账。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(HttpServletRequest req, Exception e) {
        String traceId = newTraceId();
        log.error("系统异常 [traceId={}] [{} {}]",
                traceId, req.getMethod(), req.getRequestURI(), e);
        return Result.fail(500, "系统繁忙，请稍后重试（错误编号 " + traceId + "）");
    }

    /** 8 位短编号 —— 够用且好念，用户报障时可以直接读出来 */
    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
