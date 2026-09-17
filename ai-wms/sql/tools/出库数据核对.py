# -*- coding: utf-8 -*-
"""出库数据导入后的完整性核对"""
import subprocess

MYSQL = ['mysql', '-u', 'root', '-p123456', '--default-character-set=utf8mb4',
         '-N', '-B', '-e']


def q(sql):
    r = subprocess.run(MYSQL + [sql], capture_output=True, text=True,
                       encoding='utf-8', errors='ignore')
    return [l.split('\t') for l in r.stdout.strip().split('\n') if l.strip()]


def one(sql):
    r = q(sql)
    return r[0][0] if r else '?'


print('=' * 78)
print(' 一、数据量')
print('=' * 78)
for t, expect in [('outbound_order', 32621), ('outbound_order_line', 115654),
                  ('picking_wave', 9707), ('picking_task', 112977)]:
    n = int(one('SELECT COUNT(*) FROM ai_wms.%s' % t))
    print('  %-22s %-8d %s' % (t, n, '[OK]' if n == expect else '[DIFF]'))

print()
print('=' * 78)
print(' 二、关系完整性（悬空引用检查）')
print('=' * 78)
rels = [
    ('outbound_order.customer_id',
     "SELECT COUNT(*) FROM ai_wms.outbound_order o LEFT JOIN ai_wms.customer c ON o.customer_id=c.id WHERE c.id IS NULL"),
    ('outbound_order_line.order_id',
     "SELECT COUNT(*) FROM ai_wms.outbound_order_line l LEFT JOIN ai_wms.outbound_order o ON l.order_id=o.id WHERE o.id IS NULL"),
    ('outbound_order_line.sku_id',
     "SELECT COUNT(*) FROM ai_wms.outbound_order_line l LEFT JOIN ai_wms.product_sku s ON l.sku_id=s.id WHERE s.id IS NULL"),
    ('picking_wave.operator_id',
     "SELECT COUNT(*) FROM ai_wms.picking_wave w LEFT JOIN ai_wms.operator p ON w.operator_id=p.id WHERE w.operator_id IS NOT NULL AND p.id IS NULL"),
    ('picking_task.wave_id',
     "SELECT COUNT(*) FROM ai_wms.picking_task t LEFT JOIN ai_wms.picking_wave w ON t.wave_id=w.id WHERE w.id IS NULL"),
    ('picking_task.sku_id',
     "SELECT COUNT(*) FROM ai_wms.picking_task t LEFT JOIN ai_wms.product_sku s ON t.sku_id=s.id WHERE s.id IS NULL"),
    ('picking_task.location_id',
     "SELECT COUNT(*) FROM ai_wms.picking_task t LEFT JOIN ai_wms.location lc ON t.location_id=lc.id WHERE lc.id IS NULL"),
]
allok = True
for name, sql in rels:
    n = int(one(sql))
    flag = '[OK]' if n == 0 else '[NG]'
    if n:
        allok = False
    print('  %-32s 悬空: %-6d %s' % (name, n, flag))
print()
print('  → %s' % ('全部关系完整' if allok else '存在悬空引用！'))

print()
print('=' * 78)
print(' 三、业务逻辑一致性')
print('=' * 78)

r = one("""SELECT COUNT(*) FROM ai_wms.picking_task t
           JOIN ai_wms.picking_wave w ON w.id=t.wave_id
           WHERE w.total_tasks != (SELECT COUNT(*) FROM ai_wms.picking_task t2 WHERE t2.wave_id=w.id)""")
print('  波次的实际任务数 vs total_tasks 不一致: %s 个波次' % r)

r = one("""SELECT COUNT(*) FROM ai_wms.picking_task t
           JOIN ai_wms.picking_wave w ON w.id=t.wave_id
           WHERE w.total_qty != (SELECT SUM(qty_plan) FROM ai_wms.picking_task t2 WHERE t2.wave_id=w.id)""")
print('  波次的实际件数 vs total_qty 不一致: %s 个波次' % r)

r = q("""SELECT MIN(t.total_qty), MAX(t.total_qty), AVG(t.total_qty)
         FROM ai_wms.picking_wave t""")[0]
print('  波次件数: min=%s max=%s avg=%.1f  (载具容量 27)' % (r[0], r[1], float(r[2])))

r = one("SELECT COUNT(*) FROM ai_wms.picking_wave WHERE total_qty > 27")
print('  ⚠️ 超过容量 27 的波次: %s 个' % r)

print()
print('=' * 78)
print(' 四、抽样数据')
print('=' * 78)
print('出库单:')
for row in q("SELECT id, order_no, customer_id, order_time FROM ai_wms.outbound_order ORDER BY id LIMIT 3"):
    print('   ', row)
print('拣货波次:')
for row in q("SELECT id, wave_no, operator_id, total_tasks, total_qty FROM ai_wms.picking_wave ORDER BY id LIMIT 3"):
    print('   ', row)
print('拣货任务:')
for row in q("""SELECT t.id, w.wave_no, s.sku_code, l.location_code, t.qty_plan
                FROM ai_wms.picking_task t
                JOIN ai_wms.picking_wave w ON w.id=t.wave_id
                JOIN ai_wms.product_sku s ON s.id=t.sku_id
                JOIN ai_wms.location l ON l.id=t.location_id
                ORDER BY t.id LIMIT 5"""):
    print('   ', row)
