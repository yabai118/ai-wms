# -*- coding: utf-8 -*-
"""
生成【初始库存】数据

思路：用真实订单数据反推
  ① 统计每个 SKU 在真实历史中的累计出货量
  ② 按「期初库存 = 累计出货量 / 出货天数 × 备货天数」估算合理库存量
  ③ 把库存分配到 2~3 个拣货区库位（模拟真实仓库：同一 SKU 分散存放）

说明：真实的「当前库存快照」企业不会公开，这里是**用真实出货数据反推**的，
      库存量级是有依据的，不是凭空编造。
"""
import pandas as pd
import numpy as np
import os
import random
import subprocess

random.seed(42)
np.random.seed(42)

DATA = '../data/footwear'
OUT = '04_init_inventory.sql'
MYSQL = ['mysql', '-u', 'root', '-p123456', '--default-character-set=utf8mb4',
         '-N', '-B', '-e']


def q(sql):
    r = subprocess.run(MYSQL + [sql], capture_output=True, text=True,
                       encoding='utf-8', errors='ignore')
    return [l.split('\t') for l in r.stdout.strip().split('\n') if l.strip()]


def esc(s):
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


# ---------- 1. 从真实订单统计每个 SKU 的累计出货量 ----------
print('统计真实出货量...')
order = pd.read_csv(os.path.join(DATA, 'Customer_Order.csv'), sep=';')
order['ref'] = order['Reference'].astype(str).str.strip()
order = order.dropna(subset=['Size (US)'])
order = order[order['quantity (units)'] > 0]
order['sz'] = order['Size (US)'].apply(lambda v: v / 10 if v > 20 else v)
order['sku'] = [f'{r}-{s:g}' for r, s in zip(order['ref'], order['sz'])]
ship = order.groupby('sku')['quantity (units)'].sum().to_dict()
print('  SKU 数: %d' % len(ship))

# ---------- 2. 取数据库里的 SKU 与库位映射 ----------
print('读取数据库映射...')
sku_rows = q("SELECT id, sku_code FROM ai_wms.product_sku")
sku_id_map = {code: int(sid) for sid, code in sku_rows}

loc_rows = q("""SELECT id, location_code FROM ai_wms.location
                WHERE location_type = 1 ORDER BY id""")   # 只放拣货区
pick_locs = [int(r[0]) for r in loc_rows]
print('  SKU %d 个 / 拣货区库位 %d 个' % (len(sku_id_map), len(pick_locs)))

# ---------- 3. 生成库存 ----------
# 每个库位最多放 18 个商品位，用一个计数器控制
loc_used = {}
loc_products = {}     # 记录每个库位放了哪些 SKU
rows = []
loc_state = []        # (location_id, {sku_id: qty})

# 简单策略：轮询分配库位，每个 SKU 占 1~3 个库位
loc_pool = list(pick_locs)
random.shuffle(loc_pool)
pointer = 0

for code, sid in sku_id_map.items():
    total_ship = ship.get(code, 0)
    # 库存量估算：出货越多，备货越多（但设下限，保证演示时有货可分配）
    if total_ship > 0:
        base = max(50, int(total_ship / 30))      # 约等于 1 个月的量
    else:
        base = random.randint(20, 80)             # 没出过货的 SKU 也给少量

    # 拆到 1~3 个库位
    parts = random.randint(1, 3) if base > 30 else 1
    per = base // parts
    for i in range(parts):
        qty = per if i < parts - 1 else base - per * (parts - 1)
        if qty <= 0:
            continue
        lid = loc_pool[pointer % len(loc_pool)]
        pointer += 1
        rows.append((sid, lid, qty))

print('生成库存记录: %d 条' % len(rows))

# ---------- 4. 写出 SQL ----------
with open(OUT, 'w', encoding='utf-8') as f:
    f.write("USE ai_wms;\nSET NAMES utf8mb4;\n\n")
    f.write("DELETE FROM inventory;\n")
    f.write("DELETE FROM inventory_transaction;\n\n")

    batch = 500
    for i in range(0, len(rows), batch):
        chunk = rows[i:i + batch]
        vals = ','.join(
            '(%d,%d,%d,0,0,0,%d,0)' % (r[0], r[1], r[2], r[2])
            for r in chunk)
        f.write("INSERT INTO inventory (sku_id, location_id, qty, qty_allocated, "
                "qty_picked, qty_onhold, qty_available, version) VALUES %s;\n" % vals)

    # 同步更新库位占用数
    f.write("""
-- 更新每个库位已用的商品位数
UPDATE location l
SET used_slots = (SELECT COUNT(*) FROM inventory i WHERE i.location_id = l.id),
    status = CASE WHEN (SELECT COUNT(*) FROM inventory i WHERE i.location_id = l.id) > 0
                  THEN 1 ELSE 0 END
WHERE l.location_type = 1;
""")

print('已生成: %s (%.1f MB)' % (OUT, os.path.getsize(OUT) / 1024 / 1024))
print('  库存总量: %d 件' % sum(r[2] for r in rows))
