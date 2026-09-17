# -*- coding: utf-8 -*-
"""深入排查体检发现的 7 个问题"""
import pandas as pd
import os

DATA = '../data/footwear'
order = pd.read_csv(os.path.join(DATA, 'Customer_Order.csv'), sep=';')
wave = pd.read_csv(os.path.join(DATA, 'Picking_Wave.csv'), sep=';')
loc = pd.read_csv(os.path.join(DATA, 'Storage_Location.csv'))
loc['code'] = loc.originalLocation.astype(str).str.strip()
wave['lc'] = wave['locations'].astype(str).str.strip()
order['dt'] = pd.to_datetime(order['creationDate'], format='%d/%m/%Y %H:%M', errors='coerce')

print('#' * 90)
print('# 问题1: Size (US) 最大 305 —— 这正常吗？')
print('#' * 90)
sz = order['Size (US)']
print('尺码分布（前 15 个值）:')
print(sz.value_counts().sort_index().head(15).to_string())
print('...')
print('尺码 > 20 的记录数:', (sz > 20).sum())
print()
print('尺码 > 20 的记录样例:')
bad = order[sz > 20][['orderNumber', 'Reference', 'Size (US)', 'quantity (units)', 'creationDate']]
print(bad.head(10).to_string(index=False))
print()
print('这些异常尺码涉及的订单数:', bad.orderNumber.nunique())
print('这些异常记录的时间范围:', bad.creationDate.min(), '~', bad.creationDate.max())

print()
print('#' * 90)
print('# 问题2: quantity (units) = 0')
print('#' * 90)
q0 = order[order['quantity (units)'] == 0]
print('数量为 0 的记录数:', len(q0))
if len(q0):
    print(q0.head(5).to_string(index=False))

print()
print('#' * 90)
print('# 问题3: Size (US) 空值（16 条）')
print('#' * 90)
nn = order[order['Size (US)'].isna()]
print('空值记录数:', len(nn))
if len(nn):
    print(nn.head(5).to_string(index=False))

print()
print('#' * 90)
print('# 问题4: 波次库位不在库位表的 6 个')
print('#' * 90)
loc_codes = set(loc['code'])
std = {x for x in wave['lc'].unique()
       if '-' in x and x.count('-') == 2 and x.split('-')[0].isalpha() and x.split('-')[1].isdigit()}
print('不在库位表的:', sorted(std - loc_codes))
print()
print('所有非标准格式的波次库位:')
nonstd = sorted({x for x in wave['lc'].unique() if x not in std})
print(nonstd)

print()
print('#' * 90)
print('# 问题5: 订单有但波次表没有的 77 个波次号')
print('#' * 90)
ow = set(order['waveNumber'].dropna().astype(int))
ww = set(wave['waveNumber'].dropna().astype(int))
missing = sorted(ow - ww)
print('缺失波次号（前 15 个）:', missing[:15])
print('涉及订单行数:', order[order['waveNumber'].isin(missing)].shape[0])
print('涉及订单数:', order[order['waveNumber'].isin(missing)].orderNumber.nunique())
print('这些记录的时间范围:',
      order[order['waveNumber'].isin(missing)].dt.min(), '~',
      order[order['waveNumber'].isin(missing)].dt.max())
print('全部记录的时间范围:', order.dt.min(), '~', order.dt.max())

print()
print('#' * 90)
print('# 问题6: position 重复的库位')
print('#' * 90)
dup = loc[loc.duplicated('position', keep=False)].sort_values('position')
print(dup[['originalLocation', 'position', 'x', 'y', 'z']].to_string(index=False))
