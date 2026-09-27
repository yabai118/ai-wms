package com.aiwms.service;

import com.aiwms.dto.ResetPasswordRequest;
import com.aiwms.dto.StaffOptionVO;
import com.aiwms.dto.SysUserCreateRequest;
import com.aiwms.dto.SysUserQuery;
import com.aiwms.dto.SysUserVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 账号管理
 *
 * <p>替代"加个账号要写 SQL"的现状。
 *
 * <p><b>不做物理删除</b>——用 {@code status=0} 停用。
 * 库存流水、波次都引用了操作人，删了审计链就断了。
 */
public interface UserService {

    /** 分页查询账号 */
    IPage<SysUserVO> pageUsers(SysUserQuery query);

    /**
     * 新建账号
     *
     * <p>只需要三个东西：<b>哪个员工 + 初始密码 + 什么角色</b>。
     * 登录名（工号）和显示名（姓名）由员工档案决定，不在这里填。
     *
     * @return 新账号 id
     */
    Long createUser(SysUserCreateRequest request);

    /** 启用 / 停用账号 */
    void updateStatus(Long id, Integer status);

    /**
     * 管理员重置他人密码
     *
     * <p>被重置的人此前签发的令牌会立即失效（否则重置密码拦不住已经登录的人）。
     */
    void resetPassword(Long id, ResetPasswordRequest request);

    /** 员工下拉（供新建账号时选择"这个账号对应哪个员工"） */
    List<StaffOptionVO> listStaff();
}
