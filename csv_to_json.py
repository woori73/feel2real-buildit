import csv
import json
from datetime import date
from pathlib import Path


FIELD_MAPPING = {
    'ledger_type': 'ledgerType',
    'date': 'date',
    'vendor': 'vendor',
    'account': 'account',
    'memo': 'memo',
    'item_type': 'itemType',
    'deposit_amount': 'depositAmount',
    'withdrawal_amount': 'withdrawalAmount',
    'fee': 'fee',
    'account_category': 'accountCategory',
    'item_desc': 'itemDesc',
    'supply_amount': 'supplyAmount',
    'vat': 'vat',
    'tax_exempt_supply': 'taxExemptSupply',
    'total_amount': 'totalAmount',
    'card_fee': 'cardFee',
    'receipt_method': 'receiptMethod',
    'pay_method': 'payMethod',
    'business': 'business',
    'biz_reg_no': 'bizRegNo',
    'fiscal_year': 'fiscalYear',
    'business_code': 'businessCode',
    'ref_no': 'refNo',
    'menu_path': 'menuPath',
    'raw': 'raw',
}
RAW_ONLY_FIELDS = {'source', 'source_data_type', 'balance'}
CSV_PATH = Path(__file__).resolve().parent / 'work-input' / 'easyshop-real' / 'EASYSHOP_REAL_DATA_18CSV_2024_2026' / 'EASYSHOP_ACADEMY_LEDGER_2026_SALES.csv'


def convert_row(row, business_code):
    if None in row or any(value is None for value in row.values()):
        raise ValueError('CSV column count mismatch')
    unknown = set(row) - set(FIELD_MAPPING) - RAW_ONLY_FIELDS
    if unknown:
        raise ValueError(f'Unmapped CSV headers: {sorted(unknown)}')
    mapped = {target: row.get(source) for source, target in FIELD_MAPPING.items()}
    if mapped['businessCode'] not in (None, '', business_code):
        raise ValueError('Business code mismatch')
    mapped['businessCode'] = business_code
    original_date = row['date']
    if date.fromisoformat(original_date).isoformat() != original_date:
        raise ValueError('Expected canonical YYYY-MM-DD date')
    mapped['raw'] = json.dumps(row, ensure_ascii=False, separators=(',', ':'))
    return mapped


def build_first_row_request():
    with CSV_PATH.open('r', encoding='utf-8-sig', newline='') as source:
        reader = csv.DictReader(source, strict=True)
        row = next(reader)
    mapped = convert_row(row, 'ACADEMY')
    if mapped['ledgerType'] != 'sales' or mapped['date'] != '2026-01-03':
        raise ValueError('Unexpected first sales row')
    if int(mapped['totalAmount'].replace(',', '')) != 540000:
        raise ValueError('Unexpected first sales amount')
    for source, target in FIELD_MAPPING.items():
        if source in row and source != 'raw' and mapped[target] != row[source]:
            raise ValueError(f'Value changed: {source}')
    return {
        'businessId': None,
        'sourceName': CSV_PATH.name,
        'rows': [mapped],
    }


if __name__ == '__main__':
    print(json.dumps(build_first_row_request(), ensure_ascii=False, indent=2))
