# -*- coding: utf-8 -*-
import pandas as pd

p = pd.read_csv('../data/footwear/Product.csv', sep=';')
p.columns = ['reference', 'abc', 'sector']
p['reference'] = p['reference'].str.strip()
p['abc'] = p['abc'].str.strip()

o = pd.read_csv('../data/footwear/Customer_Order.csv', sep=';')
o['ref'] = o['Reference'].astype(str).str.strip()

w = pd.read_csv('../data/footwear/Picking_Wave.csv', sep=';')
w['ref'] = w['reference'].astype(str).str.strip()

loc = pd.read_csv('../data/footwear/Storage_Location.csv')
loc['code'] = loc.originalLocation.str.strip()
loc['zone'] = loc.code.str.split('-').str[0]
w['loc_code'] = w['locations'].astype(str).str.strip()

print('=' * 60)
print(' 商品维度：有多少从没动过')
print('=' * 60)
all_p = set(p.reference)
in_order = set(o['ref'])
in_wave = set(w['ref'])
print('商品总数:            %d' % len(all_p))
print('出现在订单里的:      %d' % len(in_order))
print('出现在拣货里的:      %d' % len(in_wave))
print('从未进过任何订单:    %d' % len(all_p - in_order))
print('   -> %s' % sorted(all_p - in_order)[:12])
print('有订单但没拣过:      %d' % len(in_order - in_wave))
print('   -> %s' % sorted(in_order - in_wave)[:12])

print()
print('这些未动商品的 ABC 等级分布:')
dead = all_p - in_wave
print(p[p.reference.isin(dead)]['abc'].value_counts().to_string())

print()
print('=' * 60)
print(' 库位维度：有多少从没用过')
print('=' * 60)
used_loc = set(w['loc_code'][w['loc_code'].str.match(r'^[A-Z]-\d+-\d+$', na=False)].unique())
loc['used'] = loc.code.isin(used_loc)
print('库位总数:            %d' % len(loc))
print('被拣货用过的:        %d' % loc.used.sum())
print('从未用过的:          %d' % (~loc.used).sum())

print()
print('各区使用率:')
t = loc.groupby('zone').agg(总数=('code', 'size'), 用过=('used', 'sum'))
t['使用率'] = (t.用过 / t.总数 * 100).round(0).astype(int).astype(str) + '%'
print(t.to_string())
