# -*- coding: utf-8 -*-
"""增值税发票 OCR 字段解析器。

接口：parse_invoice_result(ocr_result) / parse_invoice(ocr_result)

设计原则：
1. 解析 OCR 文本及其坐标，不依赖固定 item 下标。
2. 发票号码优先依据“发票号码/No”标签；旧版票据顶部的独立代码按版面位置识别。
3. 不含税金额、税额只取“合计”汇总行；价税合计只取“小写”金额。
4. 字段无法可靠识别时返回 null，不用明细行或无关数字猜测。
"""
from __future__ import annotations

import json
import re
import sys
from decimal import Decimal, InvalidOperation
from typing import Any, Dict, List, Optional, Sequence, Tuple

DATE_RE = re.compile(r'(?P<y>19\d{2}|20\d{2})\s*[年./\-]\s*(?P<m>\d{1,2})\s*[月./\-]\s*(?P<d>\d{1,2})\s*日?')
DATE_COMPACT_RE = re.compile(r'(?P<y>19\d{2}|20\d{2})(?P<m>\d{2})(?P<d>\d{2})')
MONEY_RE = re.compile(r'(?<![A-Za-z0-9])(?:[¥￥Yy]\s*)?[-+]?\d[\d,，]*(?:\.\d{1,8})?(?![A-Za-z0-9%])')
TAX_ID_RE = re.compile(r'^[0-9A-Z]{15,20}$', re.I)


def _plain(v: Any) -> str:
    if v is None:
        return ''
    return re.sub(r'\s+', ' ', str(v).replace('\u3000', ' ').replace('\xa0', ' ')).strip()


def _tolist(v: Any) -> Any:
    try:
        return v.tolist() if callable(getattr(v, 'tolist', None)) else v
    except Exception:
        return v


def _box(obj: Dict[str, Any]) -> Optional[Tuple[float, float, float, float]]:
    for key in ('bbox', 'rec_box', 'box', 'polygon', 'rec_poly', 'points', 'rec_polys'):
        v = _tolist(obj.get(key))
        if not isinstance(v, (list, tuple)) or not v:
            continue
        try:
            if len(v) == 4 and all(isinstance(n, (int, float)) for n in v):
                x1, y1, x2, y2 = map(float, v)
                return min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)
            pts = [(float(p[0]), float(p[1])) for p in v if isinstance(p, (list, tuple)) and len(p) >= 2]
            if pts:
                xs, ys = [p[0] for p in pts], [p[1] for p in pts]
                return min(xs), min(ys), max(xs), max(ys)
        except (TypeError, ValueError, IndexError):
            pass
    return None


def _norm(s: str) -> str:
    return s.translate(str.maketrans({'Ｏ':'0', 'O':'0', '〇':'0', 'Ｉ':'1', 'I':'1', 'l':'1', '．':'.', '，':',', '￥':'¥'}))


def _items(data: Any) -> List[Dict[str, Any]]:
    out: List[Dict[str, Any]] = []

    def add(text: Any, obj: Optional[Dict[str, Any]] = None, idx: Optional[int] = None) -> None:
        t = _plain(text)
        if not t:
            return
        obj = obj or {}
        out.append({'text': t, 'bbox': _box(obj), 'index': obj.get('index', idx if idx is not None else len(out)), 'score': obj.get('score', obj.get('rec_score'))})

    def walk(obj: Any) -> None:
        if isinstance(obj, dict):
            its = obj.get('items')
            if isinstance(its, list):
                for i, it in enumerate(its):
                    if isinstance(it, dict):
                        t = next((it[k] for k in ('text', 'rec_text', 'label', 'transcription') if it.get(k) is not None), None)
                        if t is not None:
                            add(t, it, i)
                        else:
                            walk(it)
                    elif isinstance(it, (str, int, float)):
                        add(it, idx=i)
                return
            texts = _tolist(obj.get('rec_texts'))
            if isinstance(texts, (list, tuple)):
                boxes = None
                for k in ('rec_boxes', 'rec_polys', 'dt_polys'):
                    candidate = _tolist(obj.get(k))
                    if isinstance(candidate, (list, tuple)):
                        boxes = candidate
                        break
                for i, t in enumerate(texts):
                    b = boxes[i] if boxes is not None and i < len(boxes) else None
                    add(t, {'bbox': b}, i)
                return
            key = next((k for k in ('text', 'rec_text', 'transcription') if isinstance(obj.get(k), (str, int, float))), None)
            if key:
                add(obj[key], obj)
            for k, v in obj.items():
                if k != key and isinstance(v, (dict, list, tuple)):
                    walk(v)
        elif isinstance(obj, (list, tuple)):
            # 兼容 PaddleOCR: [polygon, (text, score)]
            if len(obj) >= 2 and isinstance(obj[1], (list, tuple)) and obj[1] and isinstance(obj[1][0], str):
                add(obj[1][0], {'polygon': obj[0]})
            else:
                for v in obj:
                    walk(v)
        elif isinstance(obj, str):
            for line in obj.splitlines():
                add(line)

    walk(data)
    # 去掉同一文本、同一坐标的重复包装结果
    result, seen = [], set()
    for it in out:
        b = it['bbox']
        key = (it['text'], tuple(round(x, 1) for x in b) if b else None)
        if key not in seen:
            seen.add(key)
            result.append(it)
    return result


def _center(it: Dict[str, Any]) -> Optional[Tuple[float, float]]:
    b = it.get('bbox')
    return ((b[0] + b[2]) / 2, (b[1] + b[3]) / 2) if b else None


def _money_tokens(text: str) -> List[str]:
    s = _norm(text).replace('，', ',')
    vals = []
    for m in MONEY_RE.finditer(s):
        raw = re.sub(r'^[¥￥Yy]\s*', '', m.group(0)).replace(',', '')
        # 金额至少有小数点和小数位；避免把税率、日期、发票号码当金额
        if '.' not in raw:
            continue
        try:
            Decimal(raw)
            vals.append(raw)
        except InvalidOperation:
            pass
    return vals


def _fmt_money(v: Optional[str]) -> Optional[str]:
    if v is None:
        return None
    try:
        return f'{Decimal(v):.2f}'
    except (InvalidOperation, ValueError):
        return None


def _find_check_code(items: Sequence[Dict[str, Any]]) -> Optional[str]:
    # 校验码通常为一串数字，OCR 可能在数字组之间插入空格。
    for it in items:
        t = _norm(it['text'])
        m = re.search(r'校验码\s*[:：]?\s*([0-9\s]{8,})', t)
        if m:
            value = re.sub(r'\s+', '', m.group(1))
            if len(value) >= 8 and value.isdigit():
                return value
    # 标签和值被 OCR 分成不同文本框时，读取紧邻的数字文本框。
    for i, it in enumerate(items):
        if '校验码' not in it['text']:
            continue
        for cand in items[i + 1:i + 4]:
            value = re.sub(r'\s+', '', _norm(cand['text']))
            if re.fullmatch(r'\d{8,}', value):
                return value
    return None


def _find_date(items: Sequence[Dict[str, Any]]) -> Optional[str]:
    for i, it in enumerate(items):
        if not re.search(r'开票日期|开具日期|开票时间|开具时间', it['text']):
            continue
        near = [it] + list(items[i + 1:i + 5])
        base = _center(it)
        for cand in near:
            c = _center(cand)
            if base and c and cand is not it and abs(c[1] - base[1]) > 35:
                continue
            for pat in (DATE_RE, DATE_COMPACT_RE):
                m = pat.search(_norm(cand['text']))
                if m:
                    y, mo, d = int(m['y']), int(m['m']), int(m['d'])
                    if 1 <= mo <= 12 and 1 <= d <= 31:
                        return f'{y:04d}-{mo:02d}-{d:02d}'
    return None


def _find_ids(items: Sequence[Dict[str, Any]]) -> Tuple[Optional[str], Optional[str]]:
    code: Optional[str] = None
    number: Optional[str] = None
    # 首选同框明确标签，避免从别的号码中误取。
    for it in items:
        t = _norm(it['text'])
        if code is None:
            m = re.search(r'(?:发票代码|(?<!信用)代码)\s*[:：]?\s*([0-9]{10,12}|[0-9]{20})', t, re.I)
            if m:
                code = m.group(1)
        if number is None:
            for pat in (r'(?:发票号码|发票号|号码)\s*[:：]?\s*([0-9]{6,30})', r'\bNo\.?\s*[:：]?\s*([0-9]{6,30})', r'Invoice\s*(?:No|Number)\s*[:：]?\s*([0-9A-Z]{6,30})'):
                m = re.search(pat, t, re.I)
                if m:
                    number = m.group(1)
                    break

    # 标签和值分框时，在同一行附近找值。
    for i, it in enumerate(items):
        t = it['text']
        if code is None and re.search(r'发票代码|(?<!信用)代码', t):
            base = _center(it)
            for cand in items[i + 1:i + 5]:
                m = re.search(r'(?<!\d)(\d{10,12}|\d{20})(?!\d)', _norm(cand['text']))
                c = _center(cand)
                if m and (not base or not c or abs(c[1] - base[1]) <= 35):
                    code = m.group(1); break
        if number is None and re.search(r'发票号码|发票号|号码|\bNo\b', t, re.I):
            base = _center(it)
            for cand in items[i:i + 5]:
                m = re.search(r'(?<!\d)(\d{6,30})(?!\d)', _norm(cand['text']))
                c = _center(cand)
                if m and (not base or not c or abs(c[1] - base[1]) <= 40):
                    number = m.group(1); break

    # 旧版增值税专用发票的代码常位于顶部左侧，OCR 可能只识别出裸数字，
    # 号码则明确带有 No。只有存在 No 号码、票名及顶部坐标证据时才补代码。
    if code is None and number and any(re.search(r'增值税专用发票', x['text']) for x in items):
        no_item = next((x for x in items if re.search(r'\bNo\.?\s*[:：]?\s*\d{6,30}', _norm(x['text']), re.I)), None)
        if no_item:
            nc = _center(no_item)
            candidates = []
            for it in items:
                c = _center(it)
                t = _norm(it['text']).strip()
                if not c or not re.fullmatch(r'\d{10,12}', t):
                    continue
                # 发票代码在号码左侧/顶部，且处于票面头部；排除右侧重复识别的代码。
                if c[1] <= 120 and nc and c[0] < nc[0] and c[0] < 0.55 * max(nc[0], 1):
                    candidates.append((c[1], c[0], t))
            if candidates:
                candidates.sort(key=lambda x: (x[0], x[1]))
                code = candidates[0][2]
    return code, number


def _role_markers(items: Sequence[Dict[str, Any]]) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    buyers = [x for x in items if re.search(r'购买方|购方', x['text'])]
    sellers = [x for x in items if re.search(r'销售方|销方|售方', x['text'])]
    if not sellers:
        by = min((_center(x)[1] for x in buyers if _center(x)), default=-1)
        sellers = [x for x in items if x['text'].strip() in {'销', '售', '方'} and _center(x) and _center(x)[1] > by + 100]
    return buyers, sellers


def _role(it: Dict[str, Any], items: Sequence[Dict[str, Any]], buyers: Sequence[Dict[str, Any]], sellers: Sequence[Dict[str, Any]]) -> Optional[str]:
    p = _center(it)
    bp = [_center(x) for x in buyers if _center(x)]
    sp = [_center(x) for x in sellers if _center(x)]
    if p and bp and sp:
        by, sy = sum(x[1] for x in bp) / len(bp), sum(x[1] for x in sp) / len(sp)
        if abs(by - sy) < 55:
            mid = (min(x[0] for x in bp + sp) + max(x[0] for x in bp + sp)) / 2
            return 'buyer' if p[0] < mid else 'seller'
        return 'buyer' if min(abs(p[1] - x[1]) for x in bp) <= min(abs(p[1] - x[1]) for x in sp) else 'seller'
    if p and bp and not sp:
        # 只识别到购买方标签时，常见版式是购买方在左、销售方在右。
        # 以购买方标记所在列为依据，而不是把整张票据都归给购买方。
        mark_x = sum(x[0] for x in bp) / len(bp)
        return 'buyer' if p[0] <= mark_x else 'seller'
    if p and sp and not bp:
        # 电子发票经常只识别到竖排“销售方信息”，没有识别到购买方标签。
        # 销售方标记在右栏时，左栏属于购买方；右栏属于销售方。
        mark_x = sum(x[0] for x in sp) / len(sp)
        boxes = [x.get('bbox') for x in items if x.get('bbox')]
        if boxes:
            page_left = min(b[0] for b in boxes)
            page_right = max(b[2] for b in boxes)
            page_mid = (page_left + page_right) / 2
        else:
            all_x = [_center(x)[0] for x in items if _center(x)]
            page_mid = (min(all_x) + max(all_x)) / 2 if all_x else mark_x
        marker_is_right = (mark_x >= page_mid - 0.02 * max(page_right - page_left, 1)) if boxes else (mark_x >= page_mid)
        if marker_is_right:
            return 'buyer' if p[0] < mark_x else 'seller'
        # 少见的反向版式：销售方标记在左栏。
        return 'seller' if p[0] <= mark_x else 'buyer'
    if p and (bp or sp):
        marks = [('buyer', x) for x in bp] + [('seller', x) for x in sp]
        # 买卖双方标记处于同一水平带时，按左右栏划分；否则按垂直距离归属。
        if bp and sp:
            by = sum(x[1] for x in bp) / len(bp)
            sy = sum(x[1] for x in sp) / len(sp)
            if abs(by - sy) < 55:
                mid = (sum(x[0] for x in bp) / len(bp) + sum(x[0] for x in sp) / len(sp)) / 2
                return 'buyer' if p[0] < mid else 'seller'
        return min(marks, key=lambda pair: abs(p[1] - pair[1][1]))[0]
    # 无坐标时按前文语义判断
    idx = items.index(it)
    prev = ' '.join(x['text'] for x in items[max(0, idx - 12):idx])
    if re.search(r'销售方|销方', prev) and not re.search(r'购买方|购方', prev):
        return 'seller'
    if re.search(r'购买方|购方', prev) and not re.search(r'销售方|销方', prev):
        return 'buyer'
    return None


def _parties(items: Sequence[Dict[str, Any]]) -> Tuple[Dict[str, Optional[str]], Dict[str, Optional[str]]]:
    result = {'buyer': {'name': None, 'tax_id': None}, 'seller': {'name': None, 'tax_id': None}}
    buyers, sellers = _role_markers(items)
    # 公司名称：支持“名称：公司”同框及“名”+“称：”拆框。
    for i, it in enumerate(items):
        t = it['text']; label = it; name = None
        m = re.search(r'名称\s*[:：]\s*(.+)$', t)
        if m:
            name = m.group(1).strip()
        elif t.strip() in {'名', '名称'} and i + 1 < len(items):
            # OCR 常把“名称：公司名”拆成“名” + “称：公司名”，
            # 也可能拆成“名” + “称：” + “公司名”；两种都兼容。
            next_text = items[i + 1]['text'].strip()
            next_match = re.match(r'^称\s*[:：]\s*(.+)$', next_text)
            if next_match:
                name = next_match.group(1).strip()
            elif re.fullmatch(r'称\s*[:：]?', next_text):
                for cand in items[i + 2:i + 6]:
                    if re.search(r'纳税人|统一社会信用|地址电话|开户行|购买方|销售方|密码', cand['text']):
                        break
                    a, b = _center(it), _center(cand)
                    if a and b and abs(a[1] - b[1]) > 32:
                        continue
                    name = cand['text'].strip(' ：:'); break
        elif re.fullmatch(r'你\s*[:：]?', t.strip()) and i + 1 < len(items):
            # 此类旧票据中，OCR 偶尔把“名称：”误识别为“你：”；
            # 仅在下一项与标签处于同一水平行时，才把下一项作为名称。
            cand = items[i + 1]
            a, b = _center(it), _center(cand)
            if not a or not b or abs(a[1] - b[1]) <= 35:
                name = cand['text'].strip(' ：:')
        if name and not re.search(r'纳税人|购买方|销售方|地址电话|开户行', name):
            r = _role(label, items, buyers, sellers) or _role(it, items, buyers, sellers)
            if r and not result[r]['name']:
                result[r]['name'] = name

    # 对未识别出“名称”标签的一侧补提名称：
    # 仅在已识别名称的同一行附近寻找明显的中文主体名称，避免扫描明细区。
    known_name_items = []
    for i, it in enumerate(items):
        if re.search(r'名称\s*[:：]', it['text']):
            p = _center(it)
            if p:
                known_name_items.append((i, p, _role(it, items, buyers, sellers)))
    for i, it in enumerate(items):
        if not it.get('bbox') or not it['text']:
            continue
        t = it['text'].strip()
        if re.search(r'名称|纳税人|统一社会信用|购买方|销售方|发票|项目名称|规格型号|单位|数量|单价|金额|税率|税额|合计|开票日期|国家税务|税务局|备注|开票人', t):
            continue
        if re.fullmatch(r'[0-9A-Z]{12,}', _norm(t), re.I) or _money_tokens(t):
            continue
        if not re.search(r'[\u4e00-\u9fff]', t) or len(t) < 5:
            continue
        p = _center(it)
        if not p:
            continue
        # 只接受和已识别的主体名称标签同一行的文字框；不把商品明细当作公司名称。
        nearby = [(abs(p[1] - q[1]), role) for _, q, role in known_name_items if role and abs(p[1] - q[1]) <= 48]
        if not nearby:
            continue
        role = _role(it, items, buyers, sellers)
        if role and not result[role]['name']:
            result[role]['name'] = t.strip(' ：:')

    # 纳税人识别号：同框标签或标签和值分框。
    for i, it in enumerate(items):
        t = _norm(it['text']).upper()
        match = re.search(r'(?:统一社会信用代码(?:/纳税人识别号)?|纳税人识别号|税号)\s*[:：]?\s*([0-9A-Z]{15,20})', t, re.I)
        r = _role(it, items, buyers, sellers)
        if match and TAX_ID_RE.fullmatch(match.group(1)) and r and not result[r]['tax_id']:
            result[r]['tax_id'] = match.group(1); continue
        if re.search(r'纳税人识别号|统一社会信用代码|税号', t):
            for cand in items[i + 1:i + 5]:
                val = re.sub(r'\s+', '', _norm(cand['text'])).upper()
                if TAX_ID_RE.fullmatch(val):
                    rr = _role(cand, items, buyers, sellers) or r
                    if rr and not result[rr]['tax_id']:
                        result[rr]['tax_id'] = val
                    break
                if re.search(r'名称|地址电话|开户行|购买方|销售方', cand['text']):
                    break
    for p in result.values():
        if p['name']:
            p['name'] = re.sub(r'^(?:名\s*)?称\s*[:：]?\s*', '', p['name']).strip(' ：:')
    return result['buyer'], result['seller']


def _extract_amounts(items: Sequence[Dict[str, Any]]) -> Dict[str, Any]:
    res: Dict[str, Any] = {'amount_excluding_tax': None, 'tax_amount': None, 'total_amount': None, 'validation': False}

    # 价税合计：先找“小写”标签，选同一水平行、标签右侧的阿拉伯金额。
    total_label_ids = [i for i, x in enumerate(items) if re.search(r'价税合计', x['text'])]
    small_ids = [i for i, x in enumerate(items) if re.search(r'小写', x['text'])]
    for li in (small_ids + total_label_ids):
        label = items[li]; lp = _center(label)
        cand_list = []
        for j, x in enumerate(items):
            vals = _money_tokens(x['text'])
            if not vals:
                continue
            p = _center(x)
            if lp and p:
                if abs(p[1] - lp[1]) <= 28 and p[0] >= lp[0] - 3:
                    cand_list.append((abs(p[1] - lp[1]), p[0] - lp[0], j, vals[-1]))
            elif abs(j - li) <= 4:
                cand_list.append((0, abs(j - li), j, vals[-1]))
        if cand_list:
            cand_list.sort()
            res['total_amount'] = _fmt_money(cand_list[0][3])
            if res['total_amount']:
                break
    if res['total_amount'] is None:
        # OCR 将小写标签和金额分框但没有坐标时，按紧邻顺序补取。
        for li in small_ids:
            for x in items[li:li + 5]:
                vals = _money_tokens(x['text'])
                if vals:
                    res['total_amount'] = _fmt_money(vals[-1]); break
            if res['total_amount']:
                break

    # 不含税金额、税额：找“合计”行（也支持“合”“计”拆框），聚合同一行金额。
    anchors: List[Tuple[float, float, int]] = []
    for i, x in enumerate(items):
        p = _center(x)
        if not p:
            continue
        if re.search(r'合计', x['text']):
            anchors.append((p[0], p[1], i))
        elif x['text'].strip() in {'合', '计'}:
            # 寻找同一水平行上的另一个单字，组合成“合计”锚点。
            for j, y in enumerate(items):
                if j == i or y['text'].strip() not in {'合', '计'}:
                    continue
                q = _center(y)
                if q and abs(q[1] - p[1]) <= 22:
                    anchors.append(((p[0] + q[0]) / 2, (p[1] + q[1]) / 2, min(i, j)))
                    break
    # 去重锚点
    uniq = []
    seen = set()
    for a in anchors:
        k = (round(a[0] / 10), round(a[1] / 10))
        if k not in seen:
            seen.add(k); uniq.append(a)

    rows = []
    for ax, ay, ai in uniq:
        values = []
        for j, x in enumerate(items):
            p = _center(x)
            vals = _money_tokens(x['text'])
            if not p or not vals or abs(p[1] - ay) > 17:
                continue
            # 汇总行应位于“合计”标签右边；排除同一行左侧的其他数字。
            if p[0] <= ax + 25:
                continue
            amount = _fmt_money(vals[-1])
            if amount is not None:
                values.append((p[0], j, amount))
        values.sort(key=lambda z: z[0])
        # 汇总行的金额列从左到右为不含税金额、税额；有更多数字时仍取最靠近标签后的前两列。
        if len(values) >= 2:
            rows.append((ay, values))

    if rows:
        # 若有多个“合计”字样，优先取能找到两笔金额、且位于价税合计小写行上方最近的汇总行。
        total_y = _center(items[small_ids[0]])[1] if small_ids and _center(items[small_ids[0]]) else None
        rows.sort(key=lambda r: (abs(total_y - r[0]) if total_y is not None else 0, -r[0]))
        vals = rows[0][1]
        res['amount_excluding_tax'] = vals[0][2]
        res['tax_amount'] = vals[1][2]

    # 无坐标结构的保守回退：只在“合计”锚点附近的 OCR 顺序中找金额，不扫描明细行。
    if res['amount_excluding_tax'] is None or res['tax_amount'] is None:
        for i, x in enumerate(items):
            if x['text'].strip() == '合计' or re.search(r'合计', x['text']):
                vals = []
                for cand in items[i + 1:i + 8]:
                    nums = _money_tokens(cand['text'])
                    if nums:
                        vals.extend(_fmt_money(v) for v in nums if _fmt_money(v))
                    if len(vals) >= 2:
                        break
                if len(vals) >= 2:
                    res['amount_excluding_tax'] = res['amount_excluding_tax'] or vals[0]
                    res['tax_amount'] = res['tax_amount'] or vals[1]
                    break

    # 最后兜底：部分新版电子发票 OCR 不会识别“合计”标签，或把标签切碎。
    # 利用价税合计与同一水平行的两笔金额做校验，优先选择总额上方且距离最近的行。
    # 这也能区分明细税额（例如 37.30）和汇总税额（37.36）。
    if res['total_amount'] is not None and (res['amount_excluding_tax'] is None or res['tax_amount'] is None):
        total_y = None
        for li in small_ids + total_label_ids:
            p = _center(items[li])
            if p:
                total_y = p[1]
                break
        row_groups: Dict[int, List[Tuple[float, int, str, float]]] = {}
        for j, x in enumerate(items):
            p = _center(x)
            vals = _money_tokens(x['text'])
            if not p or not vals:
                continue
            # 按约 12 像素的水平带分组，容忍 OCR 框高度差。
            row_key = round(p[1] / 12)
            for v in vals:
                fmt = _fmt_money(v)
                if fmt is not None:
                    row_groups.setdefault(row_key, []).append((p[0], j, fmt, p[1]))
        candidates = []
        total_dec = Decimal(res['total_amount'])
        for vals in row_groups.values():
            vals.sort(key=lambda z: z[0])
            # 去掉同一数值/同一坐标附近的重复识别。
            dedup = []
            for v in vals:
                if not any(v[2] == old[2] and abs(v[0] - old[0]) < 8 for old in dedup):
                    dedup.append(v)
            if len(dedup) < 2:
                continue
            # 从左至右尝试相邻金额列；只有加总等于价税合计的组合才可信。
            for a_idx in range(len(dedup) - 1):
                for b_idx in range(a_idx + 1, len(dedup)):
                    a, b = dedup[a_idx], dedup[b_idx]
                    if abs((Decimal(a[2]) + Decimal(b[2])) - total_dec) > Decimal('0.02'):
                        continue
                    row_y = (a[3] + b[3]) / 2
                    if total_y is not None and row_y > total_y + 8:
                        continue
                    distance = abs(total_y - row_y) if total_y is not None else 0
                    candidates.append((distance, row_y, a[2], b[2]))
        if candidates:
            candidates.sort(key=lambda z: (z[0], -z[1]))
            _, _, amount, tax = candidates[0]
            res['amount_excluding_tax'] = amount
            res['tax_amount'] = tax

    if all(res[k] is not None for k in ('amount_excluding_tax', 'tax_amount', 'total_amount')):
        try:
            amount_sum = (Decimal(res['amount_excluding_tax']) + Decimal(res['tax_amount'])).quantize(Decimal('0.01'))
            total = Decimal(res['total_amount']).quantize(Decimal('0.01'))
            res['validation'] = abs(amount_sum - total) <= Decimal('0.02')
        except InvalidOperation:
            res['validation'] = False
    return res


def parse_invoice_result(ocr_result: Any) -> Dict[str, Any]:
    items = _items(ocr_result)
    code, number = _find_ids(items)
    check_code = _find_check_code(items)
    date = _find_date(items)
    buyer, seller = _parties(items)
    amounts = _extract_amounts(items)
    invoice = {'invoice_code': code, 'invoice_number': number, 'check_code': check_code, 'issue_date': date, 'buyer': buyer, 'seller': seller, 'amounts': amounts}
    errors = []
    checks = [
        (number, '未能提取发票号码'), (date, '未能提取开票日期'),
        (buyer['name'], '未能提取购买方名称'), (buyer['tax_id'], '未能提取购买方纳税人识别号'),
        (seller['name'], '未能提取销售方名称'), (seller['tax_id'], '未能提取销售方纳税人识别号'),
        (amounts['amount_excluding_tax'], '未能从合计汇总行提取不含税金额'),
        (amounts['tax_amount'], '未能从合计汇总行提取税额'),
        (amounts['total_amount'], '未能从价税合计小写区域提取价税合计')]
    errors.extend(msg for val, msg in checks if not val)
    return {'success': not errors, 'invoice': invoice, 'errors': errors}


def parse_invoice(ocr_result: Any) -> Dict[str, Any]:
    return parse_invoice_result(ocr_result)

if __name__ == '__main__':
    # 用法：
    #   python invoice_parser.py            # 从 stdin 读 OCR JSON（Java 直接喂 body）
    #   python invoice_parser.py -          # 同上
    #   python invoice_parser.py <file>     # 从文件读，便于本地手工调试

    # 输出同样必须是 utf-8：Windows 下 sys.stdout 默认是 GBK（cp936），
    # Java 端按 UTF-8 解码就会把中文变成乱码。
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

    if len(sys.argv) > 2:
        print('用法: python invoice_parser.py [ocr_result.json | -]', file=sys.stderr)
        raise SystemExit(2)

    arg = sys.argv[1] if len(sys.argv) == 2 else '-'

    if arg == '-':
        # 必须走 buffer + 显式 utf-8，Windows 下 sys.stdin 默认是 GBK，
        # 中文会乱码或直接解码失败；utf-8-sig 顺便吃掉可能存在的 BOM。
        raw = sys.stdin.buffer.read()
        if not raw.strip():
            print('stdin 为空，未收到 OCR JSON', file=sys.stderr)
            raise SystemExit(2)
        text = raw.decode('utf-8-sig')
    else:
        with open(arg, 'r', encoding='utf-8-sig') as f:
            text = f.read()

    try:
        data = json.loads(text)
    except json.JSONDecodeError as e:
        # OCR 服务出错时 body 可能是 HTML 或空串，这里给明确信号而不是抛栈
        print(f'输入不是合法 JSON: {e}', file=sys.stderr)
        raise SystemExit(3)

    print(json.dumps(parse_invoice_result(data), ensure_ascii=False, indent=2))
