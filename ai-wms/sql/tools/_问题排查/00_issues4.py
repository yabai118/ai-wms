# -*- coding: utf-8 -*-
"""决定性验证：大码值 = 真实尺码 × 10 ？"""
import pandas as pd
import numpy as np

order = pd.read_csv('../data/footwear/Customer_Order.csv', sep=';')
wave = pd.read_csv('../data/footwear/Picking_Wave.csv', sep=';')


def normalize(s):
    """>20 的值除以 10"""
    return np.where(s > 20, s / 10, s)


print('=' * 90)
print('验证：把 >20 的值 ÷10，尺码分布是否变成合理的序列')
print('=' * 90)

o_sz = order['Size (US)']
o_norm = pd.Series(normalize(o_sz.dropna()), index=o_sz.dropna().index)

print('【还原前】尺码取值数: %d, 范围 %.1f ~ %.1f' % (o_sz.nunique(), o_sz.min(), o_sz.max()))
print('【还原后】尺码取值数: %d, 范围 %.1f ~ %.1f' % (o_norm.nunique(), o_norm.min(), o_norm.max()))
print()
print('还原后的尺码完整分布:')
print(o_norm.value_counts().sort_index().to_string())
print()

print('=' * 90)
print('验证：还原后，同一商品的尺码是否变成连续序列')
print('=' * 90)
order['sz_norm'] = normalize(order['Size (US)'])
for r in ['0LNUOV', '02MRUH', '05W6TK', 'PY5UPB']:
    vals = sorted(order[order.Reference.astype(str).str.strip() == r].sz_norm.dropna().unique())
    print('  %-8s 还原后尺码: %s' % (r, vals))
print()

print('=' * 90)
print('验证：波次表的尺码是否同样规律')
print('=' * 90)
w_sz = wave['Size (US)']
w_norm = pd.Series(normalize(w_sz.dropna()), index=w_sz.dropna().index)
print('还原前范围: %.1f ~ %.1f  (取值 %d 种)' % (w_sz.min(), w_sz.max(), w_sz.nunique()))
print('还原后范围: %.1f ~ %.1f  (取值 %d 种)' % (w_norm.min(), w_norm.max(), w_norm.nunique()))
print()
print('还原后尺码分布:')
print(w_norm.value_counts().sort_index().to_string())
print()

print('=' * 90)
print('验证：还原后，SKU (款×码) 有多少个')
print('=' * 90)
o2 = order.dropna(subset=['Size (US)']).copy()
o2['sz_norm'] = normalize(o2['Size (US)'])
sku_before = order.dropna(subset=['Size (US)']).groupby(
    ['Reference', 'Size (US)']).ngroups
sku_after = o2.groupby(['Reference', 'sz_norm']).ngroups
print('还原前 SKU 数: %d' % sku_before)
print('还原后 SKU 数: %d' % sku_after)
print()

# 检查是否有冲突：同一款同一个还原后尺码，来自不同原始值
g = o2.groupby(['Reference', 'sz_norm'])['Size (US)'].nunique()
conflict = g[g > 1]
print('冲突检查（同款同尺码有多个原始值）: %d 个' % len(conflict))
if len(conflict):
    print(conflict.head(10).to_string())
