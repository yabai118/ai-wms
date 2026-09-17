# -*- coding: utf-8 -*-
"""数据体检：逐字段核对类型/范围/空值/唯一性，用于验证表设计"""
import pandas as pd
import numpy as np
import os

DATA = '../data/footwear'
pd.set_option('display.width', 250)
pd.set_option('display.max_colwidth', 40)


def profile(name, df, key_cols=None):
    print('=' * 100)
    print('【%s】  行数=%d  列数=%d' % (name, len(df), len(df.columns)))
    print('=' * 100)
    print('%-26s %-10s %8s %8s %-22s %-22s' %
          ('列名', 'dtype', '空值', '唯一值', '最小值', '最大值'))
    print('-' * 100)
    for c in df.columns:
        s = df[c]
        nulls = s.isna().sum()
        nuniq = s.nunique()
        if pd.api.types.is_numeric_dtype(s):
            mn = s.min()
            mx = s.max()
            mn = '' if pd.isna(mn) else ('%.4g' % mn)
            mx = '' if pd.isna(mx) else ('%.4g' % mx)
        else:
            vals = s.dropna().astype(str)
            mn = vals.min()[:20] if len(vals) else ''
            mx = vals.max()[:20] if len(vals) else ''
        print('%-26s %-10s %8d %8d %-22s %-22s' % (c, str(s.dtype), nulls, nuniq, mn, mx))
    if key_cols:
        print()
        print('  候选键检查:')
        for k in key_cols:
            if k in df.columns:
                u = df[k].nunique()
                n = len(df)
                print('    %-24s 唯一值=%-8d 总行数=%-8d %s'
                      % (k, u, n, '[OK]可作唯一键' if u == n else '[NG] 有重复'))
    print()


# ---------- 1. Product.csv ----------
raw = open(os.path.join(DATA, 'Product.csv'), encoding='utf-8').read().split('\n')
prod = pd.read_csv(os.path.join(DATA, 'Product.csv'), sep=';')
prod.columns = ['reference', 'abc_class', 'sector']
prod = prod.apply(lambda s: s.str.strip() if s.dtype == 'object' else s)
profile('Product.csv（商品款）', prod, key_cols=['reference'])

# ---------- 2. Storage_Location.csv ----------
loc = pd.read_csv(os.path.join(DATA, 'Storage_Location.csv'))
loc['originalLocation'] = loc['originalLocation'].astype(str).str.strip()
profile('Storage_Location.csv（库位）', loc, key_cols=['originalLocation'])

# ---------- 3. Customer_Order.csv ----------
order = pd.read_csv(os.path.join(DATA, 'Customer_Order.csv'), sep=';')
for c in order.columns:
    if order[c].dtype == 'object':
        order[c] = order[c].astype(str).str.strip()
profile('Customer_Order.csv（客户订单）', order,
        key_cols=['orderNumber', 'codCustomer', 'waveNumber'])

# ---------- 4. Picking_Wave.csv ----------
wave = pd.read_csv(os.path.join(DATA, 'Picking_Wave.csv'), sep=';')
for c in wave.columns:
    if wave[c].dtype == 'object':
        wave[c] = wave[c].astype(str).str.strip()
profile('Picking_Wave.csv（拣货波次）', wave,
        key_cols=['waveNumber', 'locations'])

# ---------- 5. 四种存储策略 ----------
for f in ['Random_Storage.csv', 'Class_Based_Storage.csv',
          'Dedicated_Storage.csv', 'Hybrid_Storage.csv']:
    try:
        d = pd.read_csv(os.path.join(DATA, f), sep=';')
    except Exception:
        d = pd.read_csv(os.path.join(DATA, f))
    print('=' * 100)
    print('【%s】  形状=%s' % (f, d.shape))
    print('  列:', list(d.columns)[:8], '...' if len(d.columns) > 8 else '')
    print()

# ---------- 6. 关键关系验证 ----------
print('=' * 100)
print('【关系验证】')
print('=' * 100)

prod_refs = set(prod['reference'])
order_refs = set(order['Reference'])
wave_refs = set(wave['reference'])
print('1) 订单里的商品 ∈ 商品表?      %s  (订单有 %d 种, 商品表 %d 种, 差集 %d)'
      % ('[OK]' if order_refs <= prod_refs else '[NG]',
         len(order_refs), len(prod_refs), len(order_refs - prod_refs)))
print('2) 波次里的商品 ∈ 商品表?      %s  (差集 %d)'
      % ('[OK]' if wave_refs <= prod_refs else '[NG]', len(wave_refs - prod_refs)))

skus = set(order['Reference'] + '-' + order['Size (US)'].astype(str).str.replace(r'\.0$', '', regex=True))
print('3) 订单的 款×码 组合数:        %d' % len(skus))

loc_codes = set(loc['originalLocation'])
wave_locs = set(wave['locations'])
std_wave_locs = {x for x in wave_locs if '-' in x and x.count('-') == 2
                 and x.split('-')[0].isalpha() and x.split('-')[1].isdigit()}
print('4) 波次里标准库位 ∈ 库位表?    %s  (波次库位 %d 种, 标准格式 %d 种, 不在库位表的 %d)'
      % ('[OK]' if std_wave_locs <= loc_codes else '[NG]',
         len(wave_locs), len(std_wave_locs), len(std_wave_locs - loc_codes)))

order_waves = set(order['waveNumber'])
wave_waves = set(wave['waveNumber'])
print('5) 订单的波次号 ∈ 波次表?      %s  (订单 %d 个, 波次 %d 个, 订单有但波次无 %d)'
      % ('[OK]' if order_waves <= wave_waves else '[WARN]',
         len(order_waves), len(wave_waves), len(order_waves - wave_waves)))
print('   波次有但订单无: %d' % len(wave_waves - order_waves))
