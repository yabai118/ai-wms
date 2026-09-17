# -*- coding: utf-8 -*-
"""最终确认：大尺码是"去小数点"还是"另一套尺码体系" """
import pandas as pd

order = pd.read_csv('../data/footwear/Customer_Order.csv', sep=';')
order['sz'] = order['Size (US)']
order['ref'] = order['Reference'].astype(str).str.strip()

print('=' * 90)
print('关键测试：同一个商品，"正常码"和"大码"是否成对出现')
print('=' * 90)
g = order.groupby('ref')['sz'].agg(['min', 'max'])
both = g[(g['min'] <= 20) & (g['max'] > 20)]
print('两种都有的商品数: %d' % len(both))
print()
for r in list(both.index[:5]):
    vals = sorted(order[order.ref == r].sz.dropna().unique())
    norm = [v for v in vals if v <= 20]
    bigv = [v for v in vals if v > 20]
    print('商品 %s:' % r)
    print('   正常码: %s' % norm)
    print('   大  码: %s' % bigv)
    print()

print('=' * 90)
print('测试：85 是否等于 8.5 ？看同一订单里是否有 8 和 85 并存')
print('=' * 90)
o1 = order[order.orderNumber == 124438][['Reference', 'sz', 'quantity (units)']]
print('订单 124438 的所有行:')
print(o1.to_string(index=False))
print()

print('=' * 90)
print('统计：大码值的末尾数字规律')
print('=' * 90)
big = order[order.sz > 20]['sz']
vc = big.value_counts().sort_index()
print(vc.to_string())
print()
print('观察: 75/85/95/105/115 间隔10 -> 若去小数点则是 7.5/8.5/9.5/10.5/11.5')
print('观察: 215~305 间隔5      -> 若去小数点则是 21.5/22/22.5... 不合理')
print()
print('大码值 > 200 的商品:')
b2 = order[order.sz > 200].groupby('ref')['sz'].agg(['min','max','nunique','count'])
print(b2.to_string())
