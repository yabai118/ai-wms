# -*- coding: utf-8 -*-
"""
从鞋厂数据集生成【基础数据】的 INSERT SQL
生成：product / product_sku / warehouse_area / location / customer / operator

★ 已应用数据质量修正（见 ../数据质量问题与处理.md）：
  1. Size (US) 格式混用：>20 的值 ÷10 还原（85 -> 8.5）
  2. 补 6 个波次用到但库位表缺失的标准库位
  3. 补 16 个非标准编码的功能库位（RC/EX/C/ZN）
"""
import pandas as pd
import numpy as np
import os

DATA = '../data/footwear'
OUT = '02_init_base.sql'

# 波次用到、但 Storage_Location.csv 里没有的库位（坐标按编码规律推断）
MISSING_LOCS = {
    # 编码: (区, 排, seg)   -> 按 seg 规律推 x/z，y 取同区同排已知值
    'E-25-20': ('E', 25, '20'),
    'G-25-10': ('G', 25, '10'),
    'I-25-20': ('I', 25, '20'),
    'J-25-20': ('J', 25, '20'),
    'K-25-20': ('K', 25, '20'),
    'K-26-20': ('K', 26, '20'),
}
# seg -> (x, z)  实测规律
SEG_MAP = {'11': (368, 1), '12': (352, 1), '13': (336, 1),
           '21': (368, 2), '22': (352, 2), '23': (336, 2),
           '30': (352, 3), '40': (352, 4),
           # 下面两个是推断值（数据里没有，按 pos=0 推）
           '10': (352, 1), '20': (352, 2)}

# 非标准编码的功能库位（坐标为推断值，分布在拣货区）
FUNC_LOCS = {
    'RC-01': None, 'RC-03': None, 'RC-04': None, 'RC-05': None,
    'EX-01': None, 'EX-04': None, 'EX-11': None,
    'C-014': None, 'C-021': None, 'C-022': None, 'C-023': None,
    'C-024': None, 'C-026': None, 'C-061': None, 'C-102': None,
    'ZN-COR': None,
}


def esc(s):
    if pd.isna(s):
        return 'NULL'
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def norm_size(v):
    """尺码还原：>20 则 ÷10"""
    if pd.isna(v):
        return np.nan
    return v / 10 if v > 20 else v


def write_batch(f, table, cols, rows, batch=500):
    for i in range(0, len(rows), batch):
        chunk = rows[i:i + batch]
        vals = ','.join(
            '(' + ','.join('NULL' if v is None else str(v) for v in r) + ')'
            for r in chunk)
        f.write(f"INSERT INTO {table} ({','.join(cols)}) VALUES {vals};\n")
    print(f'  {table}: {len(rows)} 行')


with open(OUT, 'w', encoding='utf-8') as f:
    f.write("USE ai_wms;\nSET NAMES utf8mb4;\n\n")
    f.write("-- 清空（可重复执行）\n")
    for t in ['product_sku', 'product', 'warehouse_area', 'location', 'customer', 'operator']:
        f.write(f"DELETE FROM {t};\n")
    f.write("\n")

    # ---------- 1. 商品款 ----------
    prod = pd.read_csv(os.path.join(DATA, 'Product.csv'), sep=';')
    prod.columns = ['reference', 'abc_class', 'sector']
    for c in prod.columns:
        prod[c] = prod[c].astype(str).str.strip()
    rows = [[i + 1, esc(r.reference), esc(r.abc_class), esc(r.sector)]
            for i, r in enumerate(prod.itertuples())]
    write_batch(f, 'product', ['id', 'reference', 'abc_class', 'sector'], rows)
    prod_map = {r.reference: i + 1 for i, r in enumerate(prod.itertuples())}

    # ---------- 2. SKU = 款 × 尺码（★ 尺码已还原）----------
    order = pd.read_csv(os.path.join(DATA, 'Customer_Order.csv'), sep=';')
    order['ref'] = order['Reference'].astype(str).str.strip()
    order = order.dropna(subset=['Size (US)'])
    order = order[order['quantity (units)'] > 0]
    order['sz'] = order['Size (US)'].apply(norm_size)

    skus = order[['ref', 'sz']].drop_duplicates().sort_values(['ref', 'sz'])
    rows, sku_map = [], {}
    for i, r in enumerate(skus.itertuples()):
        pid = prod_map.get(r.ref)
        if pid is None:
            continue
        code = ('%s-%g' % (r.ref, r.sz))
        sku_map[(r.ref, r.sz)] = i + 1
        rows.append([i + 1, pid, r.sz, esc(code)])
    write_batch(f, 'product_sku', ['id', 'product_id', 'size_us', 'sku_code'], rows)
    print('    (尺码已还原: >20 的值 ÷10)')

    # ---------- 3. 库区 ----------
    loc = pd.read_csv(os.path.join(DATA, 'Storage_Location.csv'))
    loc['code'] = loc.originalLocation.astype(str).str.strip()
    loc['zone'] = loc.code.str.split('-').str[0]
    zones = sorted(set(loc['zone'].unique()) |
                   {v[0] for v in MISSING_LOCS.values()})
    rows = [[i + 1, esc(z), esc(f'{z} 区')] for i, z in enumerate(zones)]
    write_batch(f, 'warehouse_area', ['id', 'area_code', 'area_name'], rows)
    zone_map = {z: i + 1 for i, z in enumerate(zones)}

    # ---------- 4. 库位 ----------
    # 4.1 判定区类型（用过=拣货区，没用过=存储区）
    wv = pd.read_csv(os.path.join(DATA, 'Picking_Wave.csv'), sep=';')
    wv['lc'] = wv['locations'].astype(str).str.strip()
    used_codes = set(wv.loc[wv['lc'].str.match(r'^[A-Z]-\d+-\d+$', na=False), 'lc'].unique())
    loc['used'] = loc['code'].isin(used_codes)
    zone_used = loc.groupby('zone')['used'].mean()
    zone_type = {z: (1 if r > 0 else 0) for z, r in zone_used.items()}
    print('  库位类型: 存储区 %d 个区 / 拣货区 %d 个区'
          % (sum(1 for v in zone_type.values() if v == 0),
             sum(1 for v in zone_type.values() if v == 1)))

    rows = []
    nid = 0
    # 4.2 标准库位
    for r in loc.itertuples():
        nid += 1
        rows.append([nid, esc(r.code), zone_map[r.zone], zone_type[r.zone],
                     int(r.x), int(r.y), int(r.z), 18, 0, 0])
    n_std = nid

    # 4.3 补充缺失的标准库位
    loc['row'] = loc['code'].str.split('-').str[1].astype(int)
    y_by_zone_row = loc.groupby(['zone', 'row'])['y'].first().to_dict()
    for code, (z, row, seg) in MISSING_LOCS.items():
        x, zz = SEG_MAP[seg]
        y = y_by_zone_row.get((z, row))
        if y is None:
            ys = loc[loc.zone == z].y
            y = int(ys.median()) if len(ys) else 900
        nid += 1
        rows.append([nid, esc(code), zone_map[z], 1, x, int(y), zz, 18, 0, 0])
    n_missing = nid - n_std

    # 4.4 补充功能库位（坐标推断，分布在拣货区）
    fx, fy, fi = 118, 1000, 0
    for code in FUNC_LOCS:
        nid += 1
        fi += 1
        x = 118 + (fi % 5) * 68
        y = 1000 + (fi // 5) * 88
        rows.append([nid, esc(code), zone_map.get('R', 1), 1, x, y, 1, 18, 0, 0])
    n_func = nid - n_std - n_missing

    write_batch(f, 'location',
                ['id', 'location_code', 'area_id', 'location_type',
                 'x_coord', 'y_coord', 'z_coord',
                 'capacity', 'used_slots', 'status'], rows)
    print('    (标准 %d + 缺失补充 %d + 功能库位 %d = %d)'
          % (n_std, n_missing, n_func, nid))

    # ---------- 5. 客户 ----------
    custs = sorted(order['codCustomer'].dropna().astype(str).str.strip().unique())
    rows = [[i + 1, esc(c), esc(c)] for i, c in enumerate(custs)]
    write_batch(f, 'customer', ['id', 'cust_code', 'cust_name'], rows)

    # ---------- 6. 拣货员（订单表 + 波次表并集）----------
    ops = sorted(set(order['operator'].dropna().astype(str).str.strip()) |
                 set(wv['operator'].dropna().astype(str).str.strip()))
    rows = [[i + 1, esc(o), esc(o)] for i, o in enumerate(ops)]
    write_batch(f, 'operator', ['id', 'op_code', 'op_name'], rows)

print(f'\n已生成: {OUT}  (%.1f KB)' % (os.path.getsize(OUT) / 1024))
