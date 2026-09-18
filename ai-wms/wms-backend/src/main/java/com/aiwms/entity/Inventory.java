package com.aiwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 即时库存（五字段模型）★
 *
 * <p>为什么需要五个数量字段：
 * <pre>
 * qty            现有总量      —— 货架上实际有多少（拣货时不变）
 * qty_allocated  已分配         —— 被订单占住、等待拣货
 * qty_picked     已拣出         —— 拣货员已取走、还没发货
 * qty_onhold     冻结           —— 质检不合格等异常占用
 * qty_available  可用           —— = qty - allocated - onhold
 * </pre>
 *
 * <p>关键：allocated（正常业务占用）与 onhold（异常冻结）必须分开。
 */
@Data
@TableName("inventory")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private Long locationId;

    /** 现有总量 */
    private Integer qty;

    /** 已分配给订单 */
    private Integer qtyAllocated;

    /** 已拣出未发货 */
    private Integer qtyPicked;

    /** 冻结量 */
    private Integer qtyOnhold;

    /** 可用量 = qty - allocated - onhold */
    private Integer qtyAvailable;

    /** 乐观锁版本号（MyBatis-Plus @Version 自动处理） */
    @Version
    private Integer version;

    private LocalDateTime updatedAt;
}
