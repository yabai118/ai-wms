package com.aiwms.service;

import com.aiwms.dto.StaffOptionVO;
import com.aiwms.dto.StaffQuery;
import com.aiwms.dto.StaffSaveRequest;
import com.aiwms.dto.StaffVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 员工档案管理
 *
 * <p>补上这个页面之前，员工档案只能靠 SQL 维护 ——
 * 而账号又必须关联员工，等于「有个必须登记的规矩，却没有登记的地方」。
 */
public interface StaffService {

    /** 分页查询员工（带账号情况：已开 / 未开）*/
    IPage<StaffVO> pageStaff(StaffQuery query);

    /** 新建员工，返回新员工 id */
    Long createStaff(StaffSaveRequest request);

    /**
     * 编辑员工
     *
     * <p><b>工号不允许改</b> —— 它同时是登录名，改了等于换了用户名，
     * 而且这个人的历史记录会对不上。只允许改姓名。
     */
    void updateStaff(Long id, StaffSaveRequest request);

    /**
     * 在职 / 离职
     *
     * <p>★ 标记离职时**如果他有账号，账号会一并停用**（并撤销已签发的令牌）——
     * 这两件事在业务上是一次操作，不该让人分两步做、还可能漏掉一步。
     */
    void updateStatus(Long id, Integer status);

    /**
     * 员工下拉：**在职 + 还没开账号**的人
     *
     * <p>供「新建账号」选人用 —— 离职的不该再出现在这里，
     * 已经有账号的也不该重复选。
     */
    List<StaffOptionVO> listOptions();
}
