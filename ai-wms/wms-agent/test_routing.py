# -*- coding: utf-8 -*-
"""用真实波次数据测试路径优化算法"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))

import pymysql
from app.services.routing import PickTask, compare_all

# 连数据库读波次任务
conn = pymysql.connect(host='localhost', user='root', password='123456',
                       database='ai_wms', charset='utf8mb4')
cur = conn.cursor()

WAVE_ID = int(sys.argv[1]) if len(sys.argv) > 1 else 9708
cur.execute("""
    SELECT t.id, s.sku_code, l.location_code, l.x_coord, l.y_coord, t.qty_plan
    FROM picking_task t
    JOIN product_sku s ON s.id = t.sku_id
    JOIN location l ON l.id = t.location_id
    WHERE t.wave_id = %s
    ORDER BY t.id
""", (WAVE_ID,))
rows = cur.fetchall()
conn.close()

tasks = [PickTask(task_id=r[0], sku_code=r[1], location_code=r[2],
                  x=r[3], y=r[4], qty=r[5]) for r in rows]

print('=' * 78)
print(' 波次 %d 路径优化对比' % WAVE_ID)
print('=' * 78)
print('任务数: %d' % len(tasks))
print()
print('货位分布:')
for t in tasks:
    print('  %-10s (%4d, %4d)  通道 x=%-4d 深度 %d' %
          (t.location_code, t.x, t.y, t.corridor, t.depth))
print()

results = compare_all(tasks)
print('=' * 78)
print(' 策略对比')
print('=' * 78)
print('%-14s %10s %10s %10s %10s' % ('策略', '总距离', '通道内', '横向', '取货深度'))
print('-' * 78)
base = results['baseline'].total_distance
for name, r in results.items():
    diff = '' if name == 'baseline' else ('  (%+.1f%%)' % (100 * (r.total_distance - base) / base))
    print('%-14s %8d 米 %8d 米 %8d 米 %8d 米%s' %
          (r.strategy_name, r.total_distance, r.corridor_distance,
           r.horizontal_distance, r.depth_distance, diff))
print()

best = min(results.values(), key=lambda r: r.total_distance)
print('最优策略: %s (%d 米)' % (best.strategy_name, best.total_distance))
print('相比基线节省: %.1f%%' % (100 * (base - best.total_distance) / base))
print()
print('最优拣货顺序:')
for i, t in enumerate(best.sequence, 1):
    print('  %2d. %-10s (%4d, %4d)' % (i, t.location_code, t.x, t.y))
