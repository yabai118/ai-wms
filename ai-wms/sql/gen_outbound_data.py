# -*- coding: utf-8 -*-
"""
生成【出库数据】的 INSERT SQL
生成：outbound_order / outbound_order_line / picking_wave / picking_task

映射规则：
  orderNumber         -> outbound_order.order_no
  codCustomer         -> customer.id (cust_code)
  Reference + 尺码还原 -> product_sku.sku_code
  waveNumber          -> picking_wave.wave_no
  locations           -> location.location_code
  operator            -> operator.op_code
"""
import pandas as pd
import numpy as np
import os
import subprocess

DATA = '../data/footwear'
OUT = '03_init_outbound.sql'
MYSQL = ['mysql', '-u', 'root', '-p123456', '--default-character-set=utf8mb4',
         '-N', '-B', '-e']


def q(sql):
    r = subprocess.run(MYSQL + [sql], capture_output=True, text=True,
                       encoding='utf-8', errors='ignore')
    return [l.split('\t') for l in r.stdout.strip().split('\n') if l.strip()]


def esc(s):
    if pd.isna(s):
        return 'NULL'
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def norm_size(v):
    if pd.isna(v):
        return np.nan
    return v / 10 if v > 20 else v


def sku_code(ref, sz):
    return '%s-%g' % (ref, sz)


def write_batch(f, table, cols, rows, batch=1000):
    for i in range(0, len(rows), batch):
        chunk = rows[i:i + batch]
        vals = ','.join(
            '(' + ','.join('NULL' if v is None else str(v) for v in chunk[j])
            + ')' for j in range(len(chunk)))
        f.write(f"INSERT INTO {table} ({','.join(cols)}) VALUES {vals};\n")
    print('  %-22s %d 行' % (table, len(rows)))


# ============ 读取 ID 映射 ============
print('读取数据库 ID 映射...')
cust_map = {r[0]: int(r[1]) for r in q("SELECT cust_code, id FROM ai_wms.customer")}
sku_map = {r[0]: int(r[1]) for r in q("SELECT sku_code, id FROM ai_wms.product_sku")}
loc_map = {r[0]: int(r[1]) for r in q("SELECT location_code, id FROM ai_wms.location")}
op_map = {r[0]: int(r[1]) for r in q("SELECT op_code, id FROM ai_wms.operator")}
print('  客户 %d / SKU %d / 库位 %d / 拣货员 %d'
      % (len(cust_map), len(sku_map), len(loc_map), len(op_map)))

# ============ 读源数据 ============
order = pd.read_csv(os.path.join(DATA, 'Customer_Order.csv'), sep=';')
for c in ['codCustomer', 'Reference', 'operator']:
    order[c] = order[c].astype(str).str.strip()
order['dt'] = pd.to_datetime(order['creationDate'], format='%d/%m/%Y %H:%M', errors='coerce')

wave = pd.read_csv(os.path.join(DATA, 'Picking_Wave.csv'), sep=';')
for c in ['reference', 'locations', 'operator']:
    wave[c] = wave[c].astype(str).str.strip()

# ============ 过滤 + 尺码还原 ============
o = order.dropna(subset=['Size (US)']).copy()
o = o[o['quantity (units)'] > 0].copy()
o['sz'] = o['Size (US)'].apply(norm_size)
o['sku'] = [sku_code(r, s) for r, s in zip(o['Reference'], o['sz'])]
o = o[o['sku'].isin(sku_map)]
print('订单行（过滤后）: %d' % len(o))

w = wave.dropna(subset=['Size (US)']).copy()
w['sz'] = w['Size (US)'].apply(norm_size)
w['sku'] = [sku_code(r, s) for r, s in zip(w['reference'], w['sz'])]
w = w[w['sku'].isin(sku_map)]
w = w[w['locations'].isin(loc_map)]
print('拣货行（过滤后）: %d' % len(w))

# ============ 生成 SQL ============
with open(OUT, 'w', encoding='utf-8') as f:
    f.write("USE ai_wms;\nSET NAMES utf8mb4;\n\n")
    for t in ['picking_task', 'outbound_allocation', 'picking_wave',
              'outbound_order_line', 'outbound_order']:
        f.write(f"DELETE FROM {t};\n")
    f.write("\n")

    # ---------- 1. outbound_order ----------
    ords = o.groupby('orderNumber').agg(
        cust=('codCustomer', 'first'),
        t=('dt', 'min')).reset_index().sort_values('orderNumber')
    order_map, rows = {}, []
    for i, r in enumerate(ords.itertuples()):
        oid = i + 1
        order_map[r.orderNumber] = oid
        rows.append([oid, esc(str(r.orderNumber)), cust_map.get(r.cust, 1),
                     esc(r.t.strftime('%Y-%m-%d %H:%M:%S'))])
    write_batch(f, 'outbound_order',
                ['id', 'order_no', 'customer_id', 'order_time'], rows)

    # ---------- 2. outbound_order_line ----------
    # 同一订单内同一个 SKU 合并数量
    ol = o.groupby(['orderNumber', 'sku'], as_index=False)['quantity (units)'].sum()
    rows = []
    for i, (ono, skc, qty) in enumerate(
            zip(ol['orderNumber'], ol['sku'], ol['quantity (units)'])):
        rows.append([i + 1, order_map[ono], sku_map[skc], int(qty)])
    write_batch(f, 'outbound_order_line', ['id', 'order_id', 'sku_id', 'qty'], rows)
    n_lines = len(rows)

    # ---------- 3. 先算拣货任务（按 波次×SKU×库位 聚合）----------
    pt = w.groupby(['waveNumber', 'sku', 'locations'], as_index=False).size()
    pt.columns = ['waveNumber', 'sku', 'loc', 'qty']

    # ---------- 4. picking_wave（需要统计每波任务数）----------
    wv = w.groupby('waveNumber', as_index=False).agg(
        op=('operator', 'first'),
        qty=('quantityToPick (units)', 'sum'))
    task_cnt = pt.groupby('waveNumber').size().to_dict()

    wave_map, rows = {}, []
    for i, (wn, op, qty) in enumerate(zip(wv['waveNumber'], wv['op'], wv['qty'])):
        wid = i + 1
        wave_map[wn] = wid
        rows.append([wid, esc(str(wn)), op_map.get(op),
                     int(task_cnt.get(wn, 0)), int(qty)])
    write_batch(f, 'picking_wave',
                ['id', 'wave_no', 'operator_id', 'total_tasks', 'total_qty'], rows)

    # ---------- 5. picking_task ----------
    rows = []
    for i, (wn, skc, lc, qty) in enumerate(
            zip(pt['waveNumber'], pt['sku'], pt['loc'], pt['qty'])):
        rows.append([i + 1, wave_map[wn], sku_map[skc], loc_map[lc], int(qty)])
    write_batch(f, 'picking_task',
                ['id', 'wave_id', 'sku_id', 'location_id', 'qty_plan'], rows)
    n_tasks = len(rows)

print()
print('=' * 60)
print('汇总:')
print('  订单行聚合后: %d 行' % n_lines)
print('  拣货任务聚合: %d 行（原始 %d 行）' % (n_tasks, len(w)))
print('  文件: %s (%.1f MB)' % (OUT, os.path.getsize(OUT) / 1024 / 1024))
