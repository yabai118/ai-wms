# -*- coding: utf-8 -*-
"""
生成【开发环境 · 自造主数据】的 SQL

生成：product / product_sku / customer / operator
★ 保留：location / warehouse_area —— 真实仓库布局，绝不改动

## 为什么要有这个脚本

数据集有 32,621 订单、导入 SQL 6.7MB，每改一次表结构就要重导一次，迭代太慢。
开发阶段改用「真实库位 + 自造主数据 + 自造单据」，重建只要几秒。

**只保留仓库布局**，是因为：
  ① `routing.py` 里 AISLE_YS / CROSS_AISLES / 起点是硬编码常量，依赖真实坐标
  ② 仓库的物理结构本来就不该变 —— 变的是每天进出的货

商品、SKU、客户、作业人员全部自造，且**用中文业务名**，演示时像个真实业务系统。

## 用法

    python sql/generator/gen_dev_data.py              # 生成 sql/dev/dev_data.sql
    mysql -u root -p"$DB_PASSWORD" < sql/dev/dev_data.sql   # 执行

## 数据规模（精简档）

    商品款 20 个 / SKU 156 个 / 客户 20 个 / 作业人员 10 人

## 可复现

全部数据写死在下面的常量里，**不用随机数**——重跑得到完全一样的结果。
"""
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   '..', 'dev', 'dev_data.sql')


# =====================================================================
#  一、商品款（20 个）
#     字段：reference 款号 / name 名称 / sector 分区 / abc ABC 分类
#
#  ABC 分级有业务含义：A = 高频出库（放靠近出货口），C = 低频。
#  本表 A/B/C = 5/6/9，比例接近真实数据的 21%/34%/45%。
# =====================================================================
PRODUCTS = [
    # ---------- 运动（6）----------
    ('RUN-001', '轻量竞速跑鞋',   '运动', 'A'),
    ('RUN-002', '缓震训练跑鞋',   '运动', 'A'),
    ('RUN-003', '越野跑鞋',       '运动', 'B'),
    ('RUN-004', '马拉松竞速鞋',   '运动', 'B'),
    ('BKT-001', '实战篮球鞋',     '运动', 'A'),
    ('BKT-002', '中帮篮球鞋',     '运动', 'C'),
    # ---------- 休闲（7）----------
    ('CAS-001', '经典休闲板鞋',   '休闲', 'A'),
    ('CAS-002', '一脚蹬便鞋',     '休闲', 'A'),
    ('CAS-003', '复古慢跑鞋',     '休闲', 'B'),
    ('CAN-001', '高帮帆布鞋',     '休闲', 'B'),
    ('CAN-002', '低帮帆布鞋',     '休闲', 'B'),
    ('SKT-001', '街头滑板鞋',     '休闲', 'C'),
    ('SKT-002', '耐磨滑板鞋',     '休闲', 'C'),
    # ---------- 户外（3）----------
    ('HIK-001', '专业登山鞋',     '户外', 'B'),
    ('HIK-002', '轻量徒步鞋',     '户外', 'C'),
    ('HIK-003', '防水徒步靴',     '户外', 'C'),
    # ---------- 正装（2）----------
    ('FML-001', '商务正装皮鞋',   '正装', 'C'),
    ('FML-002', '德比正装鞋',     '正装', 'C'),
    # ---------- 童鞋（2）----------
    ('KID-001', '儿童运动鞋',     '童鞋', 'C'),
    ('KID-002', '儿童帆布鞋',     '童鞋', 'C'),
]

# 成人码（美国码）——取 8 个常用码
ADULT_SIZES = ['6', '6.5', '7', '7.5', '8', '8.5', '9', '9.5']
# 儿童码——另一条尺码带
KID_SIZES = ['10.5', '11', '11.5', '12', '12.5', '13']

# SKU 总数 = 18 个成人款 × 8 码 + 2 个童鞋款 × 6 码 = 144 + 12 = 156
# =====================================================================
#  二、客户（20 个）
# =====================================================================
CUSTOMERS = [
    ('KH-001', '华北鞋业贸易有限公司'),
    ('KH-002', '华东百货供应链有限公司'),
    ('KH-003', '华南运动用品连锁'),
    ('KH-004', '西南商贸集团有限公司'),
    ('KH-005', '东北鞋服批发中心'),
    ('KH-006', '华中仓储物流有限公司'),
    ('KH-007', '京津冀零售集团'),
    ('KH-008', '长三角电商供应链'),
    ('KH-009', '珠三角贸易有限公司'),
    ('KH-010', '成渝百货批发城'),
    ('KH-011', '西北运动品牌代理'),
    ('KH-012', '闽南鞋业出口公司'),
    ('KH-013', '齐鲁商贸有限公司'),
    ('KH-014', '湘江零售连锁'),
    ('KH-015', '云贵百货供应站'),
    ('KH-016', '海西跨境贸易公司'),
    ('KH-017', '中原鞋服集散中心'),
    ('KH-018', '辽东商业连锁'),
    ('KH-019', '粤东批发市场管理处'),
    ('KH-020', '皖江供应链有限公司'),
]

# =====================================================================
#  三、作业人员（10 人）
#     这是「现场作业人员档案」——谁在仓库里干活。
#     与「登录账号」（sys_user）分开，账号通过 operator_id 关联到人。
# =====================================================================
OPERATORS = [
    ('OP001', '张伟'),
    ('OP002', '李娜'),
    ('OP003', '王强'),
    ('OP004', '刘洋'),
    ('OP005', '陈静'),
    ('OP006', '杨帆'),
    ('OP007', '赵磊'),
    ('OP008', '孙敏'),
    ('OP009', '周涛'),
    ('OP010', '吴倩'),
]


def esc(s):
    """转义单引号，拼 SQL 用"""
    return str(s).replace("\\", "\\\\").replace("'", "''")


def main():
    lines = []
    w = lines.append

    w("-- =====================================================================")
    w("--  AI-WMS 开发环境 · 自造主数据")
    w("--  由 sql/generator/gen_dev_data.py 生成 —— 不要手工编辑")
    w("--")
    w("--  生成：product / product_sku / customer / operator")
    w("--  保留：location / warehouse_area（真实仓库布局，本脚本不碰）")
    w("--")
    w("--  规模：商品款 %d / SKU %d / 客户 %d / 作业人员 %d"
      % (len(PRODUCTS), _sku_count(), len(CUSTOMERS), len(OPERATORS)))
    w("-- =====================================================================")
    w("")
    w("USE ai_wms;")
    w("SET NAMES utf8mb4;")
    w("")
    w("-- 先清掉旧的主数据（子表在前）")
    w("DELETE FROM product_sku;")
    w("DELETE FROM product;")
    w("DELETE FROM customer;")
    w("DELETE FROM operator;")
    w("ALTER TABLE product      AUTO_INCREMENT = 1;")
    w("ALTER TABLE product_sku  AUTO_INCREMENT = 1;")
    w("ALTER TABLE customer     AUTO_INCREMENT = 1;")
    w("ALTER TABLE operator     AUTO_INCREMENT = 1;")
    w("")

    # ---------- 商品款 ----------
    w("-- ---------- 商品款（%d 个）----------" % len(PRODUCTS))
    for i, (ref, name, sector, abc) in enumerate(PRODUCTS, start=1):
        w("INSERT INTO product (id, reference, abc_class, sector) "
          "VALUES (%d, '%s', '%s', '%s');" % (i, esc(ref), esc(abc), esc(sector)))
    w("")

    # ---------- SKU = 款 × 尺码 ----------
    sku_rows = []
    sku_id = 1
    for pid, (ref, name, sector, abc) in enumerate(PRODUCTS, start=1):
        sizes = KID_SIZES if sector == '童鞋' else ADULT_SIZES
        for size in sizes:
            code = "%s-%s" % (ref, size)
            sku_rows.append((sku_id, pid, size, code))
            sku_id += 1

    w("-- ---------- SKU（%d 个）----------" % len(sku_rows))
    for sid, pid, size, code in sku_rows:
        w("INSERT INTO product_sku (id, product_id, size_us, sku_code) "
          "VALUES (%d, %d, %s, '%s');" % (sid, pid, size, esc(code)))
    w("")

    # ---------- 客户 ----------
    w("-- ---------- 客户（%d 个）----------" % len(CUSTOMERS))
    for i, (code, name) in enumerate(CUSTOMERS, start=1):
        w("INSERT INTO customer (id, cust_code, cust_name) "
          "VALUES (%d, '%s', '%s');" % (i, esc(code), esc(name)))
    w("")

    # ---------- 作业人员 ----------
    w("-- ---------- 作业人员（%d 人）----------" % len(OPERATORS))
    for i, (code, name) in enumerate(OPERATORS, start=1):
        w("INSERT INTO operator (id, op_code, op_name) "
          "VALUES (%d, '%s', '%s');" % (i, esc(code), esc(name)))
    w("")

    # ---------- 核对 ----------
    w("-- ---------- 核对 ----------")
    w("SELECT 'product' AS 表, COUNT(*) AS 行数 FROM product")
    w("UNION ALL SELECT 'product_sku', COUNT(*) FROM product_sku")
    w("UNION ALL SELECT 'customer',    COUNT(*) FROM customer")
    w("UNION ALL SELECT 'operator',    COUNT(*) FROM operator")
    w("UNION ALL SELECT 'location（应保持不变）', COUNT(*) FROM location;")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as f:
        f.write("\n".join(lines) + "\n")

    print("已生成：%s" % os.path.abspath(OUT))
    print("  商品款 %d / SKU %d / 客户 %d / 作业人员 %d"
          % (len(PRODUCTS), len(sku_rows), len(CUSTOMERS), len(OPERATORS)))
    print("  location / warehouse_area 未被改动")


def _sku_count():
    return sum(len(KID_SIZES if s == '童鞋' else ADULT_SIZES)
               for _, _, s, _ in PRODUCTS)


if __name__ == '__main__':
    main()
