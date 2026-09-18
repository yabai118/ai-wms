# -*- coding: utf-8 -*-
"""
并发扣减测试：验证「条件更新」能否防止超卖

场景：
  某 SKU 可用库存设为 5 件
  20 个订单同时请求分配（每个要 1 件）
  预期：只有 5 个订单成功，其余 15 个因库存不足而失败
  且最终库存不能为负
"""
import subprocess
import requests
import concurrent.futures

MYSQL = ['mysql', '-u', 'root', '-p123456', '--default-character-set=utf8mb4',
         '-N', '-B', '-e']
API = 'http://localhost:8080/api'
SKU = '8N10W9-11'
STOCK = 5
ORDER_COUNT = 20


def q(sql):
    r = subprocess.run(MYSQL + [sql], capture_output=True, text=True,
                       encoding='utf-8', errors='ignore')
    return [l.split('\t') for l in r.stdout.strip().split('\n') if l.strip()]


print('=' * 70)
print(' 并发扣减测试')
print('=' * 70)

# ---------- 1. 准备：设置库存 = 5 ----------
print('\n① 准备测试数据...')
q(f"""UPDATE ai_wms.inventory i
      JOIN ai_wms.product_sku s ON s.id = i.sku_id
      SET i.qty_allocated = 0, i.qty_picked = 0,
          i.qty = 0, i.qty_available = 0
      WHERE s.sku_code = '{SKU}'""")
first_loc = q(f"""SELECT i.id FROM ai_wms.inventory i
                  JOIN ai_wms.product_sku s ON s.id = i.sku_id
                  WHERE s.sku_code = '{SKU}' LIMIT 1""")[0][0]
q(f"""UPDATE ai_wms.inventory SET qty = {STOCK}, qty_available = {STOCK}
      WHERE id = {first_loc}""")

# 找到 20 个待分配订单
orders = q(f"""SELECT o.id FROM ai_wms.outbound_order_line ol
               JOIN ai_wms.outbound_order o ON o.id = ol.order_id
               JOIN ai_wms.product_sku s ON s.id = ol.sku_id
               WHERE s.sku_code = '{SKU}' AND ol.qty = 1 AND o.status = 0
               LIMIT {ORDER_COUNT}""")
order_ids = [int(o[0]) for o in orders]

print(f'   SKU: {SKU}')
print(f'   库存: {STOCK} 件')
print(f'   待分配订单: {len(order_ids)} 个（每个要 1 件）')

# ---------- 2. 并发调用分配接口 ----------
print(f'\n② 并发分配中...')


def allocate(oid):
    try:
        r = requests.post(f'{API}/outbound-orders/{oid}/allocate', timeout=20)
        j = r.json()
        return oid, j.get('code') == 200, j.get('message', '')
    except Exception as e:
        return oid, False, str(e)


success, failed = [], []
with concurrent.futures.ThreadPoolExecutor(max_workers=ORDER_COUNT) as ex:
    for oid, ok, msg in ex.map(allocate, order_ids):
        (success if ok else failed).append((oid, msg))

print(f'   成功: {len(success)} 个')
print(f'   失败: {len(failed)} 个')

# ---------- 3. 验证结果 ----------
print('\n③ 结果验证:')
rows = q(f"""SELECT i.qty, i.qty_allocated, i.qty_available
             FROM ai_wms.inventory i
             JOIN ai_wms.product_sku s ON s.id = i.sku_id
             WHERE s.sku_code = '{SKU}' AND i.id = {first_loc}""")
qty, allocated, available = map(int, rows[0])

print(f'   最终库存: qty={qty}  allocated={allocated}  available={available}')

ok1 = len(success) == STOCK
ok2 = allocated == STOCK
ok3 = available >= 0
ok4 = qty == STOCK  # 分配不改总数

print()
print(f'   [{"PASS" if ok1 else "FAIL"}] 成功订单数 == 库存数 ({len(success)} vs {STOCK})')
print(f'   [{"PASS" if ok2 else "FAIL"}] 已分配量 == 库存数 ({allocated} vs {STOCK})')
print(f'   [{"PASS" if ok3 else "FAIL"}] 可用量未变负 ({available})')
print(f'   [{"PASS" if ok4 else "FAIL"}] 总量未被修改 ({qty} vs {STOCK})')
print()
if ok1 and ok2 and ok3 and ok4:
    print('   ✅ 并发测试通过：无超卖，库存精确')
else:
    print('   ❌ 并发测试失败')

if failed:
    print(f'\n   失败样例: {failed[0][1]}')
