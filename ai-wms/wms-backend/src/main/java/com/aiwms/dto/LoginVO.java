package com.aiwms.dto;

import lombok.Data;

/**
 * 登录返回
 *
 * <p><b>⚠️ 绝不包含 password 字段</b>——哪怕哈希也不行。
 */
@Data
public class LoginVO {

    /** JWT，前端存 localStorage，后续请求放 Authorization 头 */
    private String token;

    private String username;

    private String displayName;

    /** 角色码，如 PICKER */
    private String role;

    /** 角色中文名，如「拣货员」——前端顶栏直接显示 */
    private String roleName;

    /** 关联的作业人员 id（管理岗为 null） */
    private Long operatorId;
}
