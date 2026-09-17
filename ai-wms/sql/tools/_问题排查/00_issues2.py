# -*- coding: utf-8 -*-
"""深挖 Size (US) > 20 到底是什么"""
import pandas as pd
import os

order = pd.read_csv('../data/footwear/Customer_Order.csv', sep=';')
order['sz'] = order['Size (US)']
order['ref'] = order['Reference'].astype(str).str.strip()

print('=' * 90)
print('假设A: 尺码被去掉了小数点（95 = 9.5）？')
print('=' * 90)
big = order[order.sz > 20]
print('大值记录数: %d' % len(big))
print('大值取值分布:')
print(big.sz.value_counts().sort_index().to_string())
print()

print('=' * 90)
print('假设B: 大尺码属于"另一类商品"？—— 看商品是否混用两种尺码')
print('=' * 90)
g = order.groupby('ref')['sz'].agg(['min', 'max', 'nunique'])
g['有正常码'] = order.groupby('ref')['sz'].apply(lambda s: (s <= 20).any())
g['有大码'] = order.groupby('ref')['sz'].apply(lambda s: (s > 20).any())
both = g[(g.有正常码) & (g.有大码)]
print('商品总数: %d' % len(g))
print('只用正常码(<=20): %d' % len(g[(g.有正常码) & (~g.有大码)]))
print('只用大码(>20):    %d' % len(g[(~g.有正常码) & (g.有大码)]))
print('两种都有:          %d  <- 如果是0，说明是两类商品' % len(both))
if len(both):
    print('  样例:', list(both.index[:8]))
print()

print('=' * 90)
print('大码商品的尺码范围')
print('=' * 90)
onlybig = g[(~g.有正常码) & (g.有大码)]
print('纯大码商品数:', len(onlybig))
print('它们的尺码范围:', onlybig['min'].min(), '~', onlybig['max'].max())
print('样例:')
print(onlybig.head(10).to_string())
print()

print('=' * 90)
print('对照：纯正常码商品的尺码范围')
print('=' * 90)
onlynorm = g[(g.有正常码) & (~g.有大码)]
print('纯正常码商品数:', len(onlynorm))
print('尺码范围:', onlynorm['min'].min(), '~', onlynorm['max'].max())
print()

print('=' * 90)
print('关键验证：某商品的所有尺码取值')
print('=' * 90)
for r in list(onlybig.index[:3]) + list(onlynorm.index[:3]):
    vals = sorted(order[order.ref == r].sz.dropna().unique())
    tag = '大码商品' if r in onlybig.index else '正常商品'
    print('  %-8s [%s] 尺码: %s' % (r, tag, vals[:20]))
