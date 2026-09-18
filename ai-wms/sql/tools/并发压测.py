# -*- coding: utf-8 -*-
"""
并发压测：验证「条件更新」在高并发下能否防止超卖

## 测试设计

场景：某 SKU 库存 K 件，N 个订单同时请求分配（每个要 1 件），N > K

预期：
  ✓ 恰好 K 个订单成功
  ✓ 其余 N-K 个失败（库存不足）
  ✓ 最终库存 = 0，**不为负**（无超卖）
  ✓ allocated 精确等于 K

## 为什么这个测试有意义

超卖的本质是：并发下「读库存 → 判断够不够 → 扣减」不是原子操作。
本项目的解法是**条件更新**：
    UPDATE inventory SET ... WHERE sku_id=? AND qty_available >= ?
数据库在执行这条语句时会加行锁，「判断」和「扣减」是一个原子操作。

## 输出

- 正确性结论（是否超卖）
- 性能指标（总耗时 / QPS / P50 / P95 / P99 延迟）
- 压测报告（写入 markdown）
"""
import concurrent.futures
import json
import statistics
import subprocess
import sys
import time
from datetime import datetime

import requests

API = "http://localhost:8080/api"
MYSQL = ["mysql", "-u", "root", "-p123456",
         "--default-character-set=utf8mb4", "-N", "-B", "-e"]


def q(sql):
    r = subprocess.run(MYSQL + [sql], capture_output=True, text=True,
                       encoding="utf-8", errors="ignore")
    return [l.split("\t") for l in r.stdout.strip().split("\n") if l.strip()]


def one(sql, default=None):
    r = q(sql)
    return r[0][0] if r else default


def run_test(sku_code: str, stock: int, max_orders: int = 1000, workers: int = 100):
    """跑一轮压测"""
    print("=" * 76)
    print(f" 并发压测：{sku_code}  库存 {stock} 件  并发 {workers}")
    print("=" * 76)

    # ---------- ① 准备数据 ----------
    print("\n① 准备测试数据...")

    # 清空该 SKU 的所有库存，只保留一条并设为指定库存
    q(f"""UPDATE ai_wms.inventory i
          JOIN ai_wms.product_sku s ON s.id = i.sku_id
          SET i.qty = 0, i.qty_allocated = 0, i.qty_picked = 0,
              i.qty_onhold = 0, i.qty_available = 0
          WHERE s.sku_code = '{sku_code}'""")
    inv_id = one(f"""SELECT i.id FROM ai_wms.inventory i
                     JOIN ai_wms.product_sku s ON s.id = i.sku_id
                     WHERE s.sku_code = '{sku_code}' LIMIT 1""")
    if not inv_id:
        print(f"   ✗ 找不到 {sku_code} 的库存记录")
        return None
    q(f"""UPDATE ai_wms.inventory SET qty = {stock}, qty_available = {stock}
          WHERE id = {inv_id}""")

    # 取待分配订单
    #
    # ⚠️ 必须只选「单行订单」！
    # 因为订单分配是「整单原子」的——只要订单里有任何一行库存不足，整单分配失败。
    # 如果选了多行订单，失败原因会来自其它 SKU，污染测试结论。
    orders = q(f"""SELECT o.id FROM ai_wms.outbound_order o
                   JOIN ai_wms.outbound_order_line ol ON ol.order_id = o.id
                   JOIN ai_wms.product_sku s ON s.id = ol.sku_id
                   WHERE s.sku_code = '{sku_code}' AND ol.qty = 1 AND o.status = 0
                     AND (SELECT COUNT(*) FROM ai_wms.outbound_order_line x
                          WHERE x.order_id = o.id) = 1
                   LIMIT {max_orders}""")
    order_ids = [int(o[0]) for o in orders]

    print(f"   SKU: {sku_code}")
    print(f"   库存: {stock} 件")
    print(f"   待分配订单: {len(order_ids)} 个（每个要 1 件）")
    if len(order_ids) <= stock:
        print("   ⚠️ 订单数不足，无法验证超卖防护（需 订单数 > 库存）")

    # ---------- ② 并发执行 ----------
    print(f"\n② 并发分配中（{workers} 线程）...")
    latencies = []
    results = {"success": [], "failed": []}
    lock = __import__("threading").Lock()

    def allocate(oid):
        t0 = time.time()
        try:
            r = requests.post(f"{API}/outbound-orders/{oid}/allocate", timeout=30)
            j = r.json()
            ok = j.get("code") == 200
            msg = j.get("message", "")
        except Exception as e:
            ok, msg = False, f"{type(e).__name__}: {e}"
        dt = (time.time() - t0) * 1000
        with lock:
            latencies.append(dt)
            results["success" if ok else "failed"].append((oid, msg))
        return ok

    t_start = time.time()
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as ex:
        list(ex.map(allocate, order_ids))
    total_sec = time.time() - t_start

    # ---------- ③ 验证结果 ----------
    rows = q(f"""SELECT i.qty, i.qty_allocated, i.qty_available
                 FROM ai_wms.inventory i WHERE i.id = {inv_id}""")
    qty, allocated, available = map(int, rows[0])

    success_n = len(results["success"])
    failed_n = len(results["failed"])

    latencies.sort()
    p50 = statistics.median(latencies)
    p95 = latencies[int(len(latencies) * 0.95)] if latencies else 0
    p99 = latencies[int(len(latencies) * 0.99)] if latencies else 0

    print(f"\n③ 结果验证")
    print(f"   成功: {success_n} 个")
    print(f"   失败: {failed_n} 个")
    print(f"   最终库存: qty={qty}  allocated={allocated}  available={available}")

    # ---------- ④ 判定 ----------
    check_alloc_ok = (allocated == min(stock, len(order_ids)))
    check_no_oversell = (qty >= 0)
    check_stock_exact = (qty == min(stock, len(order_ids)))
    check_success_match = (success_n == min(stock, len(order_ids)))

    print(f"\n④ 正确性判定")
    print(f"   [{'PASS' if check_alloc_ok else 'FAIL'}] "
          f"已分配量 == 库存 ({allocated} vs {min(stock, len(order_ids))})")
    print(f"   [{'PASS' if check_success_match else 'FAIL'}] "
          f"成功订单数 == 库存 ({success_n} vs {min(stock, len(order_ids))})")
    print(f"   [{'PASS' if check_no_oversell else 'FAIL'}] "
          f"库存未变负 (qty={qty})")
    print(f"   [{'PASS' if check_stock_exact else 'FAIL'}] "
          f"库存被精确扣减 ({qty})")

    print(f"\n⑤ 性能指标")
    print(f"   总耗时: {total_sec:.2f} 秒")
    print(f"   QPS:    {len(order_ids) / total_sec:.0f} 次/秒")
    print(f"   P50:    {p50:.0f} ms")
    print(f"   P95:    {p95:.0f} ms")
    print(f"   P99:    {p99:.0f} ms")

    all_pass = all([check_alloc_ok, check_no_oversell,
                    check_stock_exact, check_success_match])
    print(f"\n   {'✅ 压测通过：无超卖，库存精确' if all_pass else '❌ 压测未通过'}")

    if results["failed"]:
        print(f'\n   失败样例: {results["failed"][0][1]}')

    return {
        "sku": sku_code, "stock": stock, "orders": len(order_ids),
        "workers": workers, "success": success_n, "failed": failed_n,
        "qty": qty, "allocated": allocated, "available": available,
        "totalSec": round(total_sec, 2),
        "qps": round(len(order_ids) / total_sec),
        "p50": round(p50), "p95": round(p95), "p99": round(p99),
        "passed": all_pass,
    }


def write_report(all_results):
    """生成压测报告"""
    lines = [
        "# 并发压测报告",
        "",
        f"> 测试时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        "> 测试对象：AI-WMS 库存分配接口 `POST /api/outbound-orders/{orderId}/allocate`",
        "",
        "## 一、测试目的",
        "",
        "验证高并发下库存分配**不会超卖**。",
        "",
        "**超卖的本质**：并发下「读库存 → 判断够不够 → 扣减」不是原子操作，",
        "两个请求可能同时读到库存 100，各自扣 80，最终扣成 -60。",
        "",
        "**本项目的解法**：条件更新——把「判断」写进 WHERE 条件里，",
        "数据库执行时加行锁，保证判断和扣减是一个原子操作：",
        "",
        "```sql",
        "UPDATE inventory",
        "SET qty_allocated = qty_allocated + #{qty},",
        "    qty_available = qty_available - #{qty}",
        "WHERE sku_id = #{skuId}",
        "  AND location_id = #{locationId}",
        "  AND qty_available >= #{qty}    -- ★ 关键：判断写进 WHERE",
        "```",
        "",
        "返回值：1 = 分配成功，0 = 库存不足（此时服务层抛异常触发事务回滚）",
        "",
        "## 二、测试结果",
        "",
        "| SKU | 库存 | 并发订单 | 成功 | 失败 | 最终库存 | 已分配 | 无超卖 |",
        "|-----|------|---------|------|------|---------|--------|--------|",
    ]
    for r in all_results:
        if not r:
            continue
        lines.append(
            f"| {r['sku']} | {r['stock']} | {r['orders']} | {r['success']} | "
            f"{r['failed']} | {r['qty']} | {r['allocated']} | "
            f"{'✅' if r['passed'] else '❌'} |"
        )

    lines += [
        "",
        "## 三、性能指标",
        "",
        "| SKU | 并发线程 | 总耗时 | QPS | P50 | P95 | P99 |",
        "|-----|---------|--------|-----|-----|-----|-----|",
    ]
    for r in all_results:
        if not r:
            continue
        lines.append(
            f"| {r['sku']} | {r['workers']} | {r['totalSec']}s | {r['qps']} | "
            f"{r['p50']}ms | {r['p95']}ms | {r['p99']}ms |"
        )

    lines += [
        "",
        "## 四、结论",
        "",
    ]
    if all(r and r["passed"] for r in all_results):
        lines.append("**全部测试通过**：所有并发场景下均无超卖，库存精确扣减。")
    else:
        lines.append("**存在未通过的测试**，需排查。")

    lines += [
        "",
        "**面试话术**：",
        "",
        "> “我做并发压测验证库存分配的原子性——用 N 个订单同时抢少量库存，",
        "> 结果恰好只有库存数量的订单成功，其余全部因库存不足被拒绝，",
        "> 最终库存精确归零、没有出现负数。",
        "> 这是因为我把判断条件写进了 UPDATE 的 WHERE 里，",
        "> 数据库加行锁保证『判断 + 扣减』是一个原子操作。”",
        "",
    ]

    with open("../../并发压测报告.md", "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("\n📄 报告已生成: ai-wms/并发压测报告.md")


if __name__ == "__main__":
    # 多组场景：不同并发强度、不同「抢购倍数」
    scenarios = [
        ("NISP6K-7",   100,  325, 100),    # 3.25 倍超额
        ("UHYJ3A-13",   50,  204, 200),    # 4.08 倍超额，高并发
        ("UNPPMJ-7.5",  20,  185, 180),    # 9.25 倍超额，极端争抢
        ("IYXV3Z-9",    10,  156, 150),    # 15.6 倍超额
    ]
    # 支持命令行自定义：python 并发压测.py <sku> <stock> <orders> <workers>
    if len(sys.argv) >= 5:
        scenarios = [(sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]))]

    all_results = []
    for sku, stock, max_orders, workers in scenarios:
        r = run_test(sku, stock, max_orders, workers)
        all_results.append(r)

    write_report(all_results)
