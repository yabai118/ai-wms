# -*- coding: utf-8 -*-
"""
并发压测 · 负面对照实验

## 为什么要做这个

普通的压测只能证明「**跑完了没报错**」——
但如果测试本身压力不够、或者校验不到位，**错的实现也可能"通过"**。

所以这里做一组**负面对照**：同一批订单、同一个脚本、同一时刻并发起跑，
**只换接口实现**：

    A 组（正式版）  POST /outbound-orders/{id}/allocate
                    判断写进 WHERE（qty_available >= qty），数据库行锁保证原子
    B 组（对照组）  POST /outbound-orders/{id}/allocate-naive
                    先 SELECT 查够不够，再无条件 UPDATE

**预期**：A 组零超卖、库存精确；**B 组超卖、库存被扣成负数**。

**如果 B 组也不超卖，说明测试方法本身有问题**（压力不够 / 校验不到位）——
这组对照的价值就在这里：它检验的是「测试」，不只是「实现」。

## 两个和普通压测不同的设计

1. **`threading.Barrier` 同步起跑**：所有线程卡在起跑线，一起冲，
   而不是被线程池陆续提交（那样有 ramp-up，峰值压力到不了）
2. **两组跑前都重置环境**：库存、订单状态、分配明细全部还原，
   保证 A / B 两次的初始条件**完全相同**

用法（仓库根目录）：
    python ai-wms/sql/tools/并发压测_对照实验.py [库存数] [订单数]

⚠️ 对照组接口（allocate-naive）是为这个实验专门加的，业务代码不要调用。
"""
import concurrent.futures
import statistics
import subprocess
import sys
import threading
import time

import requests

API = "http://localhost:8080/api"
MYSQL = ["mysql", "-u", "root", "-p123456",
         "--default-character-set=utf8mb4", "-N", "-B", "-e"]

SKU = "IYXV3Z-9"          # 沿用正式压测报告里争抢最激烈的那支 SKU


def q(sql):
    r = subprocess.run(MYSQL + [sql], capture_output=True, text=True,
                       encoding="utf-8", errors="ignore")
    return [l.split("\t") for l in r.stdout.strip().split("\n") if l.strip()]


def one(sql, default=None):
    rows = q(sql)
    return rows[0][0] if rows else default


def reset_env(stock, order_ids):
    """把库存、订单状态、分配明细全部还原 —— 保证两组初始条件完全相同"""
    q(f"""UPDATE ai_wms.inventory i
          JOIN ai_wms.product_sku s ON s.id = i.sku_id
          SET i.qty = 0, i.qty_allocated = 0, i.qty_picked = 0,
              i.qty_onhold = 0, i.qty_available = 0
          WHERE s.sku_code = '{SKU}'""")
    inv_id = one(f"""SELECT i.id FROM ai_wms.inventory i
                     JOIN ai_wms.product_sku s ON s.id = i.sku_id
                     WHERE s.sku_code = '{SKU}' LIMIT 1""")
    q(f"UPDATE ai_wms.inventory SET qty={stock}, qty_available={stock} WHERE id={inv_id}")

    ids = ",".join(str(i) for i in order_ids)
    if ids:
        q(f"""DELETE FROM ai_wms.outbound_allocation
              WHERE order_line_id IN (
                SELECT id FROM ai_wms.outbound_order_line WHERE order_id IN ({ids}))""")
        q(f"UPDATE ai_wms.outbound_order SET status=0 WHERE id IN ({ids})")
    return int(inv_id)


def run_round(label, path, stock, order_ids, inv_id, workers):
    """跑一组。用 Barrier 让所有线程同时起跑。"""
    print(f"\n{'=' * 74}")
    print(f"  {label}")
    print(f"  接口：POST /outbound-orders/{{id}}{path}")
    print(f"{'=' * 74}")

    reset_env(stock, order_ids)

    lock = threading.Lock()
    stat = {"ok": 0, "fail": 0}
    latencies = []

    def allocate(oid):
        # ★ 起跑线：等本批所有线程都就位，再一起冲
        barrier.wait()
        t0 = time.time()
        try:
            r = requests.post(f"{API}/outbound-orders/{oid}{path}", timeout=60)
            ok = r.json().get("code") == 200
        except Exception:
            ok = False
        dt = (time.time() - t0) * 1000
        with lock:
            stat["ok" if ok else "fail"] += 1
            latencies.append(dt)

    t_all = time.time()
    # 按 workers 分批，每批用 Barrier 同步起跑（批内线程数 == Barrier 方数）
    for i in range(0, len(order_ids), workers):
        chunk = order_ids[i:i + workers]
        if len(chunk) < 2:
            break
        barrier = threading.Barrier(len(chunk))
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(chunk)) as ex:
            list(ex.map(allocate, chunk))
    elapsed = time.time() - t_all

    row = q(f"SELECT qty, qty_allocated, qty_available FROM ai_wms.inventory WHERE id={inv_id}")[0]
    qty, allocated, available = map(int, row)
    sold_out = stat["ok"]
    # ★ 超卖口径：**实际分配出去的量**超出库存多少（不是"成功订单数 − 库存"）
    oversold = max(0, allocated - stock)

    latencies.sort()
    print(f"\n  接口报成功 : {sold_out} 个")
    print(f"  接口报失败 : {stat['fail']} 个")
    print(f"  最终库存   : qty={qty}  allocated={allocated}  available={available}")
    print(f"  可用量变负 : {'⚠️ 是（' + str(available) + '）' if available < 0 else '否'}")
    print(f"  超卖（分配超出库存）: {oversold} 件")
    print(f"  QPS        : {len(order_ids) / elapsed:.0f}   "
          f"P50 {statistics.median(latencies) if latencies else 0:.0f}ms   "
          f"P99 {latencies[int(len(latencies) * 0.99)] if latencies else 0:.0f}ms")

    return {"sold": sold_out, "qty": qty, "allocated": allocated,
            "available": available, "oversold": oversold,
            "elapsed": elapsed, "p50": statistics.median(latencies) if latencies else 0}


def main():
    stock = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    max_orders = int(sys.argv[2]) if len(sys.argv) > 2 else 160
    workers = 150

    # 取候选订单：必须只选「单行且 qty=1」的订单
    # —— 订单分配是整单原子的，多行订单的失败可能来自其它 SKU，会污染结论
    #
    # ⚠️ 这里**故意不加 status=0 过滤**：A/B 两组各跑一遍之后订单会被消耗，
    #    下一次运行就选不到了。改成固定取一批，每轮跑前由 reset_env() 重置状态，
    #    保证多次运行可用、且两组条件完全相同。
    orders = q(f"""SELECT o.id FROM ai_wms.outbound_order o
                   JOIN ai_wms.outbound_order_line ol ON ol.order_id = o.id
                   JOIN ai_wms.product_sku s ON s.id = ol.sku_id
                   WHERE s.sku_code = '{SKU}' AND ol.qty = 1
                     AND (SELECT COUNT(*) FROM ai_wms.outbound_order_line x
                          WHERE x.order_id = o.id) = 1
                   ORDER BY o.id
                   LIMIT {max_orders}""")
    order_ids = [int(o[0]) for o in orders]

    print("=" * 74)
    print("  并发压测 · 负面对照实验")
    print("=" * 74)
    print(f"  SKU      : {SKU}")
    print(f"  库存     : {stock} 件")
    print(f"  并发订单 : {len(order_ids)} 个（每个要 1 件）→ 争抢 {len(order_ids)/stock:.1f} 倍")
    print(f"  线程数   : {workers}（Barrier 同步起跑）")
    if len(order_ids) <= stock:
        print("\n  ⚠️ 订单数不足（需 > 库存），无法验证超卖防护")

    inv_id = reset_env(stock, order_ids)
    a = run_round("A 组 · 正式版（条件更新）", "/allocate", stock, order_ids, inv_id, workers)
    b = run_round("B 组 · 对照组（先查后扣）", "/allocate-naive", stock, order_ids, inv_id, workers)

    print(f"\n{'=' * 74}")
    print("  对照结论")
    print(f"{'=' * 74}")
    print(f"  {'':<22}{'报成功':>8}{'已分配':>8}{'可用量':>8}{'超卖':>7}")
    print(f"  {'-' * 53}")
    print(f"  {'A 正式版（条件更新）':<20}{a['sold']:>8}{a['allocated']:>8}"
          f"{a['available']:>8}{a['oversold']:>7}")
    print(f"  {'B 对照组（先查后扣）':<20}{b['sold']:>8}{b['allocated']:>8}"
          f"{b['available']:>8}{b['oversold']:>7}")
    print()
    if a["oversold"] == 0 and b["oversold"] > 0:
        print("  ✅ 对照成立：")
        print(f"     正式版零超卖（只有 {a['sold']} 个订单成功，库存精确 {stock}）；")
        print(f"     对照组**超卖 {b['oversold']} 件**（{b['sold']} 个订单都成功了，"
              f"库存被超扣、可用量变成负数）。")
        print("     → **这证明压测方法真的能发现问题**，正式版的通过不是侥幸。")
    elif a["oversold"] == 0 and b["oversold"] == 0:
        print("  ⚠️ 两组都没超卖 —— 说明测试压力不够或校验不到位，")
        print("     这个压测结论**不可信**，需要加大并发或检查校验逻辑。")
    else:
        print("  ❌ 正式版也超卖了 —— 实现有问题。")


if __name__ == "__main__":
    main()
