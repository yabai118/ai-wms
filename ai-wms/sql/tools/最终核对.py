# -*- coding: utf-8 -*-
"""最终核对：数据库 vs 源数据集，逐表逐字段验证"""
import pandas as pd
import subprocess
import os

DATA = '../../data/footwear'
MYSQL = ['mysql', '-u', 'root', '-p123456', '--default-character-set=utf8mb4',
         '-N', '-B', '-e']


def q(sql):
    r = subprocess.run(MYSQL + [sql], capture_output=True, text=True,
                       encoding='utf-8', errors='ignore')
    return [l.split('\t') for l in r.stdout.strip().split('\n') if l.strip()]


print('=' * 80)
print(' 一、数据量核对（数据库 vs 源文件）')
print('=' * 80)

# 源文件
prod = pd.read_csv(os.path.join(DATA, 'Product.csv'), sep=';')
prod.columns = ['reference', 'abc_class', 'sector']
for c in prod.columns:
    prod[c] = prod[c].astype(str).str.strip()

order = pd.read_csv(os.path.join(DATA, 'Customer_Order.csv'), sep=';')
order['ref'] = order['Reference'].astype(str).str.strip()
order_valid = order.dropna(subset=['Size (US)'])
order_valid = order_valid[order_valid['quantity (units)'] > 0]
order_valid['sz'] = order_valid['Size (US)'].apply(lambda v: v / 10 if v > 20 else v)
sku_src = order_valid[['ref', 'sz']].drop_duplicates()

loc = pd.read_csv(os.path.join(DATA, 'Storage_Location.csv'))
cust_src = order['codCustomer'].astype(str).str.strip().nunique()

checks = [
    ('product', 'SELECT COUNT(*) FROM ai_wms.product', len(prod)),
    ('product_sku', 'SELECT COUNT(*) FROM ai_wms.product_sku', len(sku_src)),
    ('warehouse_area', 'SELECT COUNT(*) FROM ai_wms.warehouse_area', 18),
    ('location(标准)', "SELECT COUNT(*) FROM ai_wms.location WHERE location_code REGEXP '^[A-Z]-[0-9]+-[0-9]+$'", len(loc)),
    ('location(全部)', 'SELECT COUNT(*) FROM ai_wms.location', None),
    ('customer', 'SELECT COUNT(*) FROM ai_wms.customer', cust_src),
    ('operator', 'SELECT COUNT(*) FROM ai_wms.operator', None),
]
for name, sql, expect in checks:
    got = int(q(sql)[0][0])
    if expect is None:
        print('  %-18s 数据库=%-8d 源文件=—' % (name, got))
    else:
        flag = 'OK ' if got == expect else 'DIFF'
        print('  %-18s 数据库=%-8d 源文件=%-8d [%s]' % (name, got, expect, flag))

print()
print('=' * 80)
print(' 二、字段值核对（抽样比对）')
print('=' * 80)

# 商品款抽样
print('商品款抽样核对:')
db = q("SELECT reference, abc_class, sector FROM ai_wms.product ORDER BY id LIMIT 3")
for r in db:
    print('   数据库: %-10s %-3s %-4s' % tuple(r))
for _, r in prod.head(3).iterrows():
    print('   源文件: %-10s %-3s %-4s' % (r.reference, r.abc_class, r.sector))

print()
print('SKU 抽样核对（验证尺码还原）:')
db = q("SELECT sku_code, size_us FROM ai_wms.product_sku ORDER BY id LIMIT 5")
for r in db:
    print('   %s' % r)
print()
print('  尺码范围核对:')
db = q("SELECT MIN(size_us), MAX(size_us), COUNT(DISTINCT size_us) FROM ai_wms.product_sku")
print('   数据库: min=%s max=%s 取值=%s' % tuple(db[0]))
print('   源文件: min=%.1f max=%.1f 取值=%d' % (sku_src.sz.min(), sku_src.sz.max(), sku_src.sz.nunique()))

print()
print('库位抽样核对:')
db = q("SELECT location_code, x_coord, y_coord, z_coord FROM ai_wms.location ORDER BY id LIMIT 3")
for r in db:
    print('   数据库: %s' % r)
for _, r in loc.head(3).iterrows():
    print('   源文件: %s %d %d %d' % (r.originalLocation.strip(), r.x, r.y, r.z))

print()
print('=' * 80)
print(' 三、关系完整性检查')
print('=' * 80)
r1 = q("""SELECT COUNT(*) FROM ai_wms.product_sku s
          LEFT JOIN ai_wms.product p ON p.id=s.product_id WHERE p.id IS NULL""")[0][0]
print('  product_sku -> product      悬空引用: %s' % r1)

r2 = q("""SELECT COUNT(*) FROM ai_wms.location l
          LEFT JOIN ai_wms.warehouse_area a ON a.id=l.area_id WHERE a.id IS NULL""")[0][0]
print('  location -> warehouse_area  悬空引用: %s' % r2)

print()
print('=' * 80)
print(' 四、已知的"非真实数据"（必须诚实标注）')
print('=' * 80)
r = q("SELECT COUNT(*) FROM ai_wms.location WHERE location_type=1 AND location_code REGEXP '^(RC|EX|C|ZN)'")[0][0]
print('  1) 功能库位（RC/EX/C/ZN）坐标是【推断值】: %s 个' % r)
r = q("SELECT COUNT(*) FROM ai_wms.location WHERE location_code IN "
      "('E-25-20','G-25-10','I-25-20','J-25-20','K-25-20','K-26-20')")[0][0]
print('  2) 补的 6 个缺失库位坐标为【按规律推断】: %s 个' % r)
print('  3) location_type（存储区/拣货区）是【基于使用率的推断】，非数据标注')
