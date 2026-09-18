# -*- coding: utf-8 -*-
"""
LLM Agent（大模型应用层）

## 定位：LLM 用在哪、不用在哪

| 场景 | 用什么 | 为什么 |
|------|--------|--------|
| **数值决策**（预测/货位/路径） | ❌ 不用 LLM | 要可解释、可复现、可验证，LLM 做不到 |
| **异常检测**（是不是异常） | ❌ 用统计规则 | 3σ 等统计方法确定性强 |
| **异常解释**（为什么、怎么办） | ✅ **用 LLM** | 把冷冰冰的异常数据翻译成人话，这是 LLM 的强项 |
| **自然语言查询** | ✅ **用 LLM** | 理解自然语言意图 → 调用工具查数据 → 组织回答 |

**核心判断**：LLM 适合**理解和表达**，不适合**计算和判断数值**。

## 自然语言查询的工作原理（Function Calling）

    用户："上周哪个拣货员效率最低？"
       ↓ ① LLM 理解意图，决定调用「查询拣货统计」工具
       ↓ ② 系统执行工具，查数据库
       ↓ ③ LLM 拿到结构化数据，组织成中文回答

**LLM 不直接算数**——它只决定"调哪个工具"，数据由系统查。
"""
from __future__ import annotations

import json
import logging
from typing import Any, Dict, List, Optional

from app.config import settings
from app.services import fallback

logger = logging.getLogger(__name__)


# =====================================================================
#  工具定义（供 LLM 调用）
# =====================================================================

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "query_warehouse_stats",
            "description": "查询仓库整体统计：商品数、SKU数、库位数、订单数、波次数、库存总量",
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "query_inventory",
            "description": "查询某个 SKU 的库存分布（在哪些库位、各有多少）",
            "parameters": {
                "type": "object",
                "properties": {
                    "skuCode": {"type": "string", "description": "SKU 编码，如 8N10W9-11"},
                },
                "required": ["skuCode"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "query_operator_performance",
            "description": "查询拣货员的作业效率排名（按处理的波次数量）",
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "query_low_stock",
            "description": "查询库存偏低的 SKU（可用量低于阈值的）",
            "parameters": {
                "type": "object",
                "properties": {
                    "threshold": {"type": "integer", "description": "可用量阈值，默认 20"},
                },
                "required": [],
            },
        },
    },
]


# =====================================================================
#  工具实现（真正查数据库的地方）
# =====================================================================

def _tool_query_warehouse_stats() -> Dict[str, Any]:
    from app.clients.db import get_conn
    sql = """
        SELECT
          (SELECT COUNT(*) FROM product)            AS productCount,
          (SELECT COUNT(*) FROM product_sku)        AS skuCount,
          (SELECT COUNT(*) FROM location)           AS locationCount,
          (SELECT COUNT(*) FROM outbound_order)     AS orderCount,
          (SELECT COUNT(*) FROM picking_wave)       AS waveCount,
          (SELECT COALESCE(SUM(qty),0) FROM inventory) AS totalStock
    """
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            return cur.fetchone() or {}


def _tool_query_inventory(sku_code: str) -> List[Dict[str, Any]]:
    from app.clients.db import get_conn
    sql = """
        SELECT l.location_code AS locationCode, l.location_type AS locationType,
               i.qty AS qty, i.qty_allocated AS allocated, i.qty_available AS available
        FROM inventory i
        JOIN product_sku s ON s.id = i.sku_id
        JOIN location l ON l.id = i.location_id
        WHERE s.sku_code = %s
        ORDER BY i.qty DESC
        LIMIT 20
    """
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (sku_code,))
            return cur.fetchall()


def _tool_query_operator_performance() -> List[Dict[str, Any]]:
    from app.clients.db import get_conn
    sql = """
        SELECT o.op_name AS operator, COUNT(*) AS waveCount
        FROM picking_wave w
        JOIN operator o ON o.id = w.operator_id
        WHERE w.operator_id IS NOT NULL
        GROUP BY o.id, o.op_name
        ORDER BY waveCount ASC
        LIMIT 10
    """
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            return cur.fetchall()


def _tool_query_low_stock(threshold: int = 20) -> List[Dict[str, Any]]:
    from app.clients.db import get_conn
    sql = """
        SELECT s.sku_code AS skuCode, SUM(i.qty_available) AS available
        FROM inventory i
        JOIN product_sku s ON s.id = i.sku_id
        GROUP BY s.id, s.sku_code
        HAVING SUM(i.qty_available) < %s
        ORDER BY available ASC
        LIMIT 20
    """
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (threshold,))
            return cur.fetchall()


TOOL_IMPL = {
    "query_warehouse_stats": lambda args: _tool_query_warehouse_stats(),
    "query_inventory": lambda args: _tool_query_inventory(args.get("skuCode", "")),
    "query_operator_performance": lambda args: _tool_query_operator_performance(),
    "query_low_stock": lambda args: _tool_query_low_stock(args.get("threshold", 20)),
}


# =====================================================================
#  LLM 客户端
# =====================================================================

def _client():
    """惰性创建 LLM 客户端（未配置 key 时返回 None）"""
    if not settings.llm_api_key:
        return None
    try:
        from openai import OpenAI
        return OpenAI(api_key=settings.llm_api_key, base_url=settings.llm_base_url)
    except Exception as e:
        logger.warning("LLM 客户端创建失败: %s", e)
        return None


def is_available() -> bool:
    """LLM 是否可用（未配置 key 时走规则桩）"""
    return bool(settings.llm_api_key)


# =====================================================================
#  ① 异常解释：把异常数据翻译成人话
# =====================================================================

def explain_anomaly(anomaly_type: str, detail: Dict[str, Any]) -> str:
    """
    用 LLM 生成异常诊断建议

    没有配置 API key 时**自动降级**到预置文案——
    这也是「规则兜底」原则的体现：LLM 不可用不能让告警没内容。
    """
    client = _client()
    if client is None:
        logger.info("LLM 未配置，异常解释降级为预置文案")
        return fallback.anomaly_fallback(anomaly_type, detail)

    prompt = f"""你是仓储系统的运维助手。以下是系统检测到的一条异常，请给仓管员一段简明的诊断建议。

异常类型：{anomaly_type}
异常详情：{json.dumps(detail, ensure_ascii=False)}

要求：
1. 用中文，100 字以内
2. 先说明可能的原因，再给出 1-2 条具体建议
3. 不要重复异常数据本身，直接给分析
"""
    try:
        resp = client.chat.completions.create(
            model=settings.llm_model,
            messages=[{"role": "user", "content": prompt}],
            timeout=10,
        )
        return resp.choices[0].message.content.strip()
    except Exception as e:
        logger.warning("LLM 调用失败，降级为预置文案: %s", e)
        return fallback.anomaly_fallback(anomaly_type, detail)


# =====================================================================
#  ② 自然语言查询（Function Calling）
# =====================================================================

SYSTEM_PROMPT = """你是 AI-WMS 智能仓储系统的数据助手。
用户会用自然语言提问，你需要：
1. 判断该调用哪个工具来获取数据
2. 基于工具返回的真实数据，用简洁的中文回答
3. 不要编造数据——如果工具返回空，就如实说"没有查到相关数据"
4. 回答要简洁，直接给结论，不要罗列原始 JSON
"""


def natural_language_query(question: str) -> Dict[str, Any]:
    """
    自然语言查询：LLM 理解意图 → 调工具查库 → 组织回答

    返回：
        {
          "answer": "自然语言回答",
          "toolCalls": [{"tool": "...", "args": {...}, "resultSummary": "..."}],
          "degraded": false
        }

    没有配置 LLM 时返回提示信息（不降级到规则桩，因为这类问题无法用规则回答）
    """
    client = _client()
    if client is None:
        return {
            "answer": "LLM 未配置（缺少 API Key），自然语言查询不可用。"
                      "请在 .env 中配置 LLM_API_KEY 后重试。"
                      "系统其它功能（路径优化、库存管理等）不受影响。",
            "toolCalls": [],
            "degraded": True,
        }

    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": question},
    ]
    tool_calls_record: List[Dict[str, Any]] = []

    try:
        # ---- 第一轮：让 LLM 决定调用哪个工具 ----
        resp = client.chat.completions.create(
            model=settings.llm_model,
            messages=messages,
            tools=TOOLS,
            tool_choice="auto",
            timeout=15,
        )
        msg = resp.choices[0].message

        # ---- 没有工具调用：直接回答 ----
        if not msg.tool_calls:
            return {
                "answer": (msg.content or "").strip(),
                "toolCalls": [],
                "degraded": False,
            }

        # ---- 执行工具 ----
        messages.append(msg)
        for call in msg.tool_calls:
            fn_name = call.function.name
            try:
                args = json.loads(call.function.arguments or "{}")
            except json.JSONDecodeError:
                args = {}

            impl = TOOL_IMPL.get(fn_name)
            if impl is None:
                result = {"error": f"未知工具 {fn_name}"}
            else:
                result = impl(args)

            tool_calls_record.append({
                "tool": fn_name,
                "args": args,
                "resultSummary": _summarize(result),
            })
            messages.append({
                "role": "tool",
                "tool_call_id": call.id,
                "content": json.dumps(result, ensure_ascii=False, default=str),
            })

        # ---- 第二轮：LLM 基于真实数据组织回答 ----
        resp2 = client.chat.completions.create(
            model=settings.llm_model,
            messages=messages,
            timeout=15,
        )
        return {
            "answer": (resp2.choices[0].message.content or "").strip(),
            "toolCalls": tool_calls_record,
            "degraded": False,
        }

    except Exception as e:
        logger.warning("自然语言查询失败: %s", e)
        return {
            "answer": f"查询失败：{type(e).__name__}: {e}",
            "toolCalls": tool_calls_record,
            "degraded": True,
        }


def _summarize(result: Any) -> str:
    """给工具结果做一个简短摘要（便于前端展示调用链）"""
    if isinstance(result, list):
        return f"返回 {len(result)} 条记录"
    if isinstance(result, dict):
        return "、".join(f"{k}={v}" for k, v in list(result.items())[:4])
    return str(result)[:60]
