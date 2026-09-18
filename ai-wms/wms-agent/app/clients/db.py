# -*- coding: utf-8 -*-
"""数据库客户端（只读，用于读取波次任务与库位坐标）"""
from __future__ import annotations

from contextlib import contextmanager
from typing import List

import pymysql

from app.config import settings
from app.services.routing import PickTask


@contextmanager
def get_conn():
    conn = pymysql.connect(
        host=settings.db_host, port=settings.db_port,
        user=settings.db_user, password=settings.db_password,
        database=settings.db_name, charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        yield conn
    finally:
        conn.close()


def load_wave_tasks(wave_id: int) -> List[PickTask]:
    """
    读取某个波次的全部拣货任务（含库位坐标）

    :param wave_id: 波次 ID
    :return: PickTask 列表
    """
    sql = """
        SELECT t.id            AS task_id,
               s.sku_code      AS sku_code,
               l.location_code AS location_code,
               l.x_coord       AS x,
               l.y_coord       AS y,
               t.qty_plan      AS qty
        FROM picking_task t
        JOIN product_sku s ON s.id = t.sku_id
        JOIN location l ON l.id = t.location_id
        WHERE t.wave_id = %s
        ORDER BY t.id
    """
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (wave_id,))
            rows = cur.fetchall()

    return [
        PickTask(
            task_id=r["task_id"],
            sku_code=r["sku_code"],
            location_code=r["location_code"],
            x=r["x"],
            y=r["y"],
            qty=r["qty"],
        )
        for r in rows
    ]


def get_wave_info(wave_id: int) -> dict:
    """读取波次基本信息"""
    sql = "SELECT id, wave_no, total_qty, total_tasks FROM picking_wave WHERE id = %s"
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, (wave_id,))
            return cur.fetchone() or {}
