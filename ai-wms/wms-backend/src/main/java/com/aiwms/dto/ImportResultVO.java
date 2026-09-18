package com.aiwms.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据导入结果
 *
 * <p>导入不是"要么全成功要么全失败"——真实场景里往往是
 * 「大部分成功、少数行有问题」，所以要把**失败明细**返回给用户，
 * 让他能修正后重新上传那几行。
 */
@Data
public class ImportResultVO {

    /** 导入类型 */
    private String type;

    /** 文件总行数 */
    private int totalRows;

    /** 成功行数 */
    private int successRows;

    /** 失败行数 */
    private int failedRows;

    /** 跳过的重复行（已存在） */
    private int skippedRows;

    /** 失败明细（最多返回 50 条，避免响应过大） */
    private List<ErrorItem> errors = new ArrayList<>();

    /** 是否有错误 */
    public boolean hasError() {
        return failedRows > 0;
    }

    public void addError(int rowNum, String message) {
        failedRows++;
        if (errors.size() < 50) {
            errors.add(new ErrorItem(rowNum, message));
        }
    }

    @Data
    public static class ErrorItem {
        /** Excel 行号（从 1 开始，含表头） */
        private int row;
        private String message;

        public ErrorItem(int row, String message) {
            this.row = row;
            this.message = message;
        }
    }
}
