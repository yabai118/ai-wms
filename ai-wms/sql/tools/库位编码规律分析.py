# -*- coding: utf-8 -*-
"""分析库位编码 -> 坐标的映射规律，用于补全缺失库位"""
import pandas as pd

loc = pd.read_csv('../data/footwear/Storage_Location.csv')
loc['code'] = loc.originalLocation.astype(str).str.strip()
p = loc['code'].str.split('-', expand=True)
loc['zone'] = p[0]
loc['row'] = p[1].astype(int)
loc['seg'] = p[2]

print('=' * 90)
print('1) 末段(seg) 与 x, z 的关系')
print('=' * 90)
m = loc.groupby('seg').agg(x=('x', 'nunique'), z=('z', 'nunique'),
                           xs=('x', lambda s: sorted(s.unique())),
                           zs=('z', lambda s: sorted(s.unique())))
print(m.to_string())
print()

print('=' * 90)
print('2) 验证：seg 是否唯一决定 (x, z)')
print('=' * 90)
g = loc.groupby('seg').agg(nx=('x', 'nunique'), nz=('z', 'nunique'))
bad = g[(g.nx > 1) | (g.nz > 1)]
print('不唯一的 seg: %d 个' % len(bad))
if len(bad):
    print(bad.to_string())
print()

print('=' * 90)
print('3) 验证：(zone, row) 是否唯一决定 y')
print('=' * 90)
g2 = loc.groupby(['zone', 'row'])['y'].nunique()
print('(zone,row) 组合数: %d' % len(g2))
print('y 不唯一的组合: %d' % (g2 > 1).sum())
if (g2 > 1).any():
    print(g2[g2 > 1].head(10).to_string())
print()

print('=' * 90)
print('4) seg -> (x, z) 的映射表')
print('=' * 90)
mp = loc.groupby('seg').agg(x=('x', 'first'), z=('z', 'first')).sort_index()
print(mp.to_string())
print()

print('=' * 90)
print('5) 缺失库位的推断')
print('=' * 90)
missing = ['E-25-20', 'G-25-10', 'I-25-20', 'J-25-20', 'K-25-20', 'K-26-20']
for mc in missing:
    z, r, seg = mc.split('-')
    seg = mc.split('-')[2]
    # 找同区同排的已知库位
    same = loc[(loc.zone == z) & (loc.row == int(r))]
    # seg 映射
    if seg in mp.index:
        x, zz = mp.loc[seg, 'x'], mp.loc[seg, 'z']
        print('  %-10s seg=%s -> x=%s z=%s' % (mc, seg, x, zz))
    else:
        print('  %-10s seg=%s -> 未知 seg!' % (mc, seg))
    print('      同区同排已知库位: %s' % list(same.code[:5]))
    if len(same):
        print('      它们的 y = %s' % sorted(same.y.unique()))
    # 同区其他排的 y 规律
    zy = loc[loc.zone == z].groupby('row')['y'].first()
    print('      %s 区 row->y 样例: %s' % (z, dict(list(zy.items())[:6])))
