# portal_core/utils.py
import pandas as pd
import io
import csv
import re
import math
from copy import copy
from datetime import datetime
from pathlib import Path
from openpyxl import Workbook, load_workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side, NamedStyle, Color
from openpyxl.utils import get_column_letter


def _seek0(file_obj):
    """Rewind an uploaded-file stream to the start before (re)reading it."""
    if hasattr(file_obj, 'seek'):
        try:
            file_obj.seek(0)
        except (OSError, ValueError):
            pass
    return file_obj


def _to_excel_buffer(df, sheet_name):
    """Serialize a DataFrame as an in-memory .xlsx download."""
    output = io.BytesIO()
    with pd.ExcelWriter(output, engine='openpyxl') as writer:
        df.to_excel(writer, sheet_name=sheet_name, index=False)
    output.seek(0)
    return output


def _require_columns(df, columns, file_label):
    missing = [column for column in columns if column not in df.columns]
    if missing:
        found = ', '.join(str(c) for c in df.columns) or '(no columns found)'
        raise ValueError(
            f"{file_label} is missing required column(s): {', '.join(missing)}. "
            f"Columns actually found in the file: {found}"
        )


def _read_uploaded_table(file_obj, dtype=None):
    """Read either an Excel workbook or a delimited text upload."""
    suffix = Path(getattr(file_obj, 'name', '')).suffix.lower()
    _seek0(file_obj)
    
    content = file_obj.read()
    
    if suffix in {'.xlsx', '.xlsm', '.xls'}:
        return pd.read_excel(io.BytesIO(content), dtype=dtype)
        
    if isinstance(content, bytes):
        try:
            content = content.decode('utf-8-sig')
        except UnicodeDecodeError:
            content = content.decode('latin-1')

    return pd.read_csv(io.StringIO(content), sep=None, engine='python', on_bad_lines='skip', dtype=dtype)


# --- FORMATTING HELPERS ---
_MATCH_STYLES = {
    'ID MATCH':         (PatternFill('solid', fgColor='C6EFCE'), Font(color='006100')),   # Green
    'SUBSTITUTE MATCH': (PatternFill('solid', fgColor='FFFF99'), Font(color='9C6500')),   # Yellow
    'NO MATCH':         (PatternFill('solid', fgColor='FFC7CE'), Font(color='9C0006')),   # Red
}

_HEADER_FILL = PatternFill('solid', fgColor='305496')
_HEADER_FONT = Font(bold=True, color='FFFFFF')
_THIN_BORDER = Border(*(Side(style='thin', color='D9D9D9') for _ in range(4)))
_FORMAL_BORDER = Border(*(Side(style='thin', color='000000') for _ in range(4)))

_STANDARD_OUTPUT_COLUMNS = [
    'MODULE', 'SLOT', 'SIDENO', 'PARTNO', 'SUB_ALTERNATE', 'REFDES',
    'QTY', 'FEEDERNAME', 'PACKAGE', 'DESCRIPTION', 'MIN', 'MAX'
]

_STANDARD_EXPORT_HEADERS = {
    'MODULE': 'Module', 'SLOT': 'Slot', 'SIDENO': 'SideNo', 'PARTNO': 'PartNo',
    'SUB_ALTERNATE': 'Sub/Alternate', 'REFDES': 'RefDes', 'QTY': 'Qty',
    'FEEDERNAME': 'FeederName', 'PACKAGE': 'Package', 'DESCRIPTION': 'Description',
    'MIN': 'Min+Unit', 'MAX': 'Max+Unit'
}


def _safe_get(row, *keys):
    for key in keys:
        if key in row:
            val = row[key]
            if not pd.isna(val) and val is not None and str(val).strip() != '':
                return val
    return ''


def _filter_zeroes(val):
    if pd.isna(val): return ''
    v_str = str(val).strip()
    return '' if v_str in ('0', '00', '0.0', '0.00', '00.00') else val


# --- SAFE COORDINATE SEARCHERS ---
def find_header_row_safe(df, target_header):
    for idx, row in df.iterrows():
        row_values = [str(cell).strip().upper() for cell in row.values if pd.notnull(cell)]
        if target_header.upper() in row_values:
            return idx
    return None

def find_header_col_safe(df, header_row_idx, target_header):
    if header_row_idx is None:
        return None
    row = df.iloc[header_row_idx]
    for col_idx, cell_value in enumerate(row.values):
        if str(cell_value).strip().upper() == target_header.upper():
            return col_idx
    return None


# =====================================================================
# --- TOOL 0: BOM MODIFIER FORMATTING (MODULE 5) ---
# =====================================================================
_REFDES_RANGE_RE = re.compile(r'^([A-Za-z_]+)(\d+)-([A-Za-z_]+)?(\d+)$', re.IGNORECASE)

def expand_ref_des(ref_des):
    if pd.isna(ref_des) or str(ref_des).strip() == "":
        return ""

    parts = str(ref_des).split(",")
    result_tokens = []

    for item in parts:
        item = item.strip()
        if not item: continue
        
        m = _REFDES_RANGE_RE.match(item)
        if m:
            prefix1 = m.group(1)
            start_num = int(m.group(2))
            
            prefix2 = m.group(3)
            if not prefix2:
                prefix2 = prefix1
                
            end_num = int(m.group(4))

            if prefix1.upper() == prefix2.upper() and start_num <= end_num:
                for i in range(start_num, end_num + 1):
                    result_tokens.append(f"{prefix1}{i}")
            else:
                result_tokens.append(item)
        else:
            result_tokens.append(item)

    return ", ".join(result_tokens)

def modify_bom_format(bom_file):
    """Extracts the 8 required columns, reformats the BOM, and auto-adjusts ONLY RefDes."""
    _seek0(bom_file)
    bom_bytes = bom_file.read()
    raw_bom = pd.read_excel(io.BytesIO(bom_bytes), header=None)

    header_row_idx = None
    for target in ["IPN", "ID", "PARTNUMBER", "PART NO"]:
        header_row_idx = find_header_row_safe(raw_bom, target)
        if header_row_idx is not None:
            break

    if header_row_idx is None:
        raise ValueError("Core Header 'IPN' or 'ID' not found in the BOM file.")

    df_bom = pd.read_excel(io.BytesIO(bom_bytes), skiprows=header_row_idx)
    df_bom.columns = [str(c).strip().upper() for c in df_bom.columns]

    column_aliases = {
        "IPN": ["IPN", "INTERNAL PART NUMBER", "PART NUMBER", "PARTNO", "ID"],
        "Description": ["DESCRIPTION", "DESC", "PART DESCRIPTION"],
        "MPN": ["MPN", "MANUFACTURER PART NUMBER"],
        "Original Manufacturer": ["ORIGINAL MANUFACTURER", "MANUFACTURER", "MFG"],
        "External ID": ["EXTERNAL ID", "EXT ID"],
        "Reference Designator": ["REFERENCE DESIGNATOR", "REFERENCE", "REFDES", "DESIGNATOR"],
        "Quantity": ["QUANTITY", "QTY"],
        "Substitute": ["SUBSTITUTE LIST", "SUBSTITUTE", "SUBSTITUTE PART", "ALT", "ALTERNATE", "SUB"]
    }

    actual_cols = {}
    for out_col, search_aliases in column_aliases.items():
        exact = next((c for c in df_bom.columns if c in search_aliases), None)
        if exact:
            actual_cols[out_col] = exact
            continue
            
        partial = None
        for c in df_bom.columns:
            words = c.replace('_', ' ').replace('-', ' ').split()
            if any(alias in words for alias in search_aliases):
                partial = c
                break
        
        if partial:
            actual_cols[out_col] = partial
        else:
            actual_cols[out_col] = next((c for c in df_bom.columns if any(alias in c for alias in search_aliases)), None)

    out_rows = []
    for _, row in df_bom.iterrows():
        new_row = {}
        for out_col in column_aliases.keys():
            source_col = actual_cols[out_col]
            
            val = row[source_col] if source_col and pd.notnull(row[source_col]) else ""
            val = str(val).strip() if val != "" else ""
            
            if out_col == "Quantity" and val.endswith('.0'):
                val = val[:-2]
                
            if out_col == "Reference Designator":
                val = expand_ref_des(val)
                
            new_row[out_col] = val
            
        out_rows.append(new_row)

    df_out = pd.DataFrame(out_rows)

    output = io.BytesIO()
    with pd.ExcelWriter(output, engine='openpyxl') as writer:
        df_out.to_excel(writer, sheet_name='Modified_BOM', index=False)
        worksheet = writer.sheets['Modified_BOM']
        
        for cell in worksheet[1]:
            cell.font = Font(bold=True)
            
        for idx, col in enumerate(df_out.columns):
            col_letter = get_column_letter(idx + 1)
            
            if col == "Reference Designator":
                worksheet.column_dimensions[col_letter].width = 60
                wrap_align = Alignment(wrap_text=True, vertical='top')
                
                for r in range(2, len(df_out) + 2):
                    cell = worksheet.cell(row=r, column=idx + 1)
                    cell.alignment = wrap_align
                    
                    val = str(cell.value) if cell.value is not None else ""
                    if val:
                        lines = math.ceil(len(val) / 55.0)
                        if lines > 1:
                            worksheet.row_dimensions[r].height = lines * 15.0
            else:
                if len(df_out) > 0:
                    max_len = max([len(str(x)) for x in df_out[col].values] + [len(str(col))]) + 2
                else:
                    max_len = len(str(col)) + 2
                worksheet.column_dimensions[col_letter].width = min(max_len, 100)

    output.seek(0)
    return {
        'file_buffer': output,
        'filename': 'modified_BOM.xlsx'
    }


# =====================================================================
# --- STEP 1 REPORT FORMATTER ---
# =====================================================================
def _build_step1_report(report_data):
    wb = Workbook()
    wb.remove(wb.active)

    sheet_configs = [
        ('Match', 'ID MATCH'),
        ('Substitute Match', 'SUBSTITUTE MATCH'),
        ('No Match', 'NO MATCH')
    ]

    headers = ['PartNumber', 'Reference Designator', 'Status', 'Match Type', 'Source File', 'Location', 'Remarks']
    num_headers = len(headers)

    header_align = Alignment(horizontal='center', vertical='top', wrap_text=True)
    data_align = Alignment(horizontal='left', vertical='top', wrap_text=True)
    center_align = Alignment(horizontal='center', vertical='top', wrap_text=True)
    center_cols = {3, 4, 5, 6}
    _style_name_cache = {}

    def _get_named_style(cache_key, fill, font, alignment):
        name = _style_name_cache.get(cache_key)
        if name is not None: return name
        name = f"s1_{cache_key}"
        if name not in wb.named_styles:
            ns = NamedStyle(name=name)
            ns.fill = fill
            ns.font = font
            ns.border = _THIN_BORDER
            ns.alignment = alignment
            wb.add_named_style(ns)
        _style_name_cache[cache_key] = name
        return name

    header_style_name = _get_named_style('header', _HEADER_FILL, _HEADER_FONT, header_align)

    for sheet_title, target_match_type in sheet_configs:
        ws = wb.create_sheet(title=sheet_title)
        ws.append(headers)
        for cell in ws[1]: cell.style = header_style_name

        sheet_data = [row for row in report_data if row['Match Type'] == target_match_type]
        fill, font = _MATCH_STYLES.get(target_match_type, _MATCH_STYLES['NO MATCH'])
        center_style_name = _get_named_style(f'{target_match_type}_center', fill, font, center_align)
        left_style_name = _get_named_style(f'{target_match_type}_left', fill, font, data_align)

        for idx, row_data in enumerate(sheet_data):
            ws.append([row_data[h] for h in headers])
            r = idx + 2
            match_type = row_data['Match Type']
            if match_type == target_match_type:
                row_center_style, row_left_style = center_style_name, left_style_name
            else:
                fb_fill, fb_font = _MATCH_STYLES.get(match_type, _MATCH_STYLES['NO MATCH'])
                row_center_style = _get_named_style(f'{match_type}_center', fb_fill, fb_font, center_align)
                row_left_style = _get_named_style(f'{match_type}_left', fb_fill, fb_font, data_align)

            for col_idx in range(1, num_headers + 1):
                cell = ws.cell(row=r, column=col_idx)
                cell.style = row_center_style if col_idx in center_cols else row_left_style

        dynamic_widths = {}
        for col_idx, header in enumerate(headers, start=1):
            max_len = len(header)
            for r in range(2, len(sheet_data) + 2):
                value = ws.cell(row=r, column=col_idx).value
                if value is not None:
                    lines = str(value).split('\n')
                    max_len = max(max_len, max(len(line) for line in lines))

            if col_idx == 2: final_width = max(20, min(max_len + 3, 45))
            elif col_idx == 7: final_width = max(20, min(max_len + 3, 50))
            else: final_width = max(12, min(max_len + 3, 30))

            ws.column_dimensions[get_column_letter(col_idx)].width = final_width
            dynamic_widths[col_idx] = final_width

        for r in range(2, len(sheet_data) + 2):
            max_lines = 1
            for c in range(1, len(headers) + 1):
                val = str(ws.cell(row=r, column=c).value or "")
                chars_per_line = max(1.0, float(dynamic_widths.get(c, 12)) - 2.0)
                lines = 0
                for line in val.split('\n'):
                    if len(line) == 0: lines += 1
                    else: lines += int((len(line) - 1) / chars_per_line) + 1
                if lines > max_lines: max_lines = lines
            ws.row_dimensions[r].height = min(max_lines * 15.5, 409.5)
            
        ws.row_dimensions[1].height = 20.0
        ws.freeze_panes = 'A2'

    output = io.BytesIO()
    wb.save(output)
    output.seek(0)
    return output


# =====================================================================
# --- STEP 1: VALIDATION (TWO-WAY CHECK PLACEMENT <-> BOM) ---
# =====================================================================
def validate_and_match_files(bom_file, placement_t_file, placement_b_file=None):
    _seek0(bom_file)
    bom_bytes = bom_file.read()
    raw_bom = pd.read_excel(io.BytesIO(bom_bytes), header=None)

    is_double_side = False
    side_str = "T" 
    
    if placement_b_file is not None or (isinstance(placement_t_file, list) and len(placement_t_file) > 1):
        is_double_side = True
        
    if not is_double_side:
        single_file = placement_t_file[0] if isinstance(placement_t_file, list) else placement_t_file
        stem = Path(str(getattr(single_file, 'name', ''))).stem.upper()
        if stem.endswith('_B') or stem.endswith('BOTTOM'): side_str = 'B'
        else: side_str = 'T'
        out_filename = f"SINGLE SIDE_BOM VALIDATED_REPORT_{side_str}.xlsx"
    else:
        out_filename = "DOUBLE SIDE_BOM VALIDATED_REPORT.xlsx"

    # Find BOM Headers dynamically
    bom_row = None
    for target in ["IPN", "ID", "PARTNUMBER", "PART NO"]:
        bom_row = find_header_row_safe(raw_bom, target)
        if bom_row is not None: break

    if bom_row is not None:
        df_bom = pd.read_excel(io.BytesIO(bom_bytes), skiprows=bom_row)
    else:
        df_bom = pd.read_excel(io.BytesIO(bom_bytes))
        
    df_bom.columns = [str(c).strip().upper() for c in df_bom.columns]

    bom_id_idx = next((i for i, c in enumerate(df_bom.columns) if c in ['IPN', 'ID', 'PARTNUMBER', 'PART NO', 'PARTNO']), 0)
    
    bom_ref_idx = next((i for i, c in enumerate(df_bom.columns) if c in ['REFERENCE DESIGNATOR', 'REFERENCE', 'REFDES', 'DESIGNATOR']), None)
    if bom_ref_idx is None:
        bom_ref_idx = next((i for i, c in enumerate(df_bom.columns) if 'REFERENCE' in c or 'REFDES' in c), 1)
    
    sub_aliases = ["SUBSTITUTE LIST", "SUBSTITUTE", "SUBSTITUTE PART", "ALT", "ALTERNATE", "SUB"]
    bom_sub_idx = None
    for i, c in enumerate(df_bom.columns):
        if c in sub_aliases:
            bom_sub_idx = i
            break
    if bom_sub_idx is None:
        for i, c in enumerate(df_bom.columns):
            if any(alias in c for alias in sub_aliases):
                bom_sub_idx = i
                break

    def parse_placement(path, source_name):
        _seek0(path)
        path_bytes = path.read()
        try: content_str = path_bytes.decode('utf-8-sig')
        except UnicodeDecodeError: content_str = path_bytes.decode('latin-1')
            
        df_raw = pd.read_csv(io.StringIO(content_str), header=None, on_bad_lines='skip')
        pl_row = find_header_row_safe(df_raw, "PartNumber")

        if pl_row is not None:
            p_idx = find_header_col_safe(df_raw, pl_row, "PartNumber")
            r_idx = find_header_col_safe(df_raw, pl_row, "GSQSC")
            g_idx = find_header_col_safe(df_raw, pl_row, "GSQBN")
            df_clean = pd.read_csv(io.StringIO(content_str), skiprows=pl_row, on_bad_lines='skip')
        else:
            p_idx, r_idx, g_idx = 1, 15, 0
            df_clean = pd.read_csv(io.StringIO(content_str), header=None, on_bad_lines='skip')

        selected = df_clean.iloc[:, [g_idx, p_idx, r_idx]].copy()
        selected.columns = ['GSQBN', 'PART', 'REF']
        selected['SOURCE_FILE'] = source_name
        return selected

    pl_dfs = []
    if isinstance(placement_t_file, list):
        for f in placement_t_file: pl_dfs.append(parse_placement(f, getattr(f, 'name', 'Placement.csv')))
    else:
        pl_dfs.append(parse_placement(placement_t_file, getattr(placement_t_file, 'name', 'Placement_T.csv')))
        
    if placement_b_file is not None:
        pl_dfs.append(parse_placement(placement_b_file, getattr(placement_b_file, 'name', 'Placement_B.csv')))

    df_pl = pd.concat(pl_dfs, ignore_index=True) if pl_dfs else pd.DataFrame(columns=['GSQBN', 'PART', 'REF', 'SOURCE_FILE'])

    def clean_val(val):
        """Safely cleans part numbers without stripping alphanumeric codes like CAP.100UF"""
        if pd.isna(val) or val is None:
            return ''
        if isinstance(val, float):
            if val.is_integer():
                return str(int(val))
            return str(val).strip()
        s = str(val).strip()
        if s.endswith('.0'):
            s = s[:-2]
        return s

    bom_dict_id = set()
    bom_dict_sub = set()
    bom_expected = {} 

    for _, row in df_bom.iterrows():
        part = clean_val(row.iloc[bom_id_idx]).upper()
        
        subs = []
        if bom_sub_idx is not None:
            sub_val = str(row.iloc[bom_sub_idx]).strip().upper()
            if sub_val not in ['NAN', 'NONE', '']:
                sub_val = re.sub(r'[/;|\n\r]', ',', sub_val)
                subs = [clean_val(s).upper() for s in sub_val.split(',') if clean_val(s)]

        ref_str = str(row.iloc[bom_ref_idx])
        expanded_refs = expand_ref_des(ref_str)

        if expanded_refs:
            for ref in expanded_refs.split(','):
                ref_clean = ref.strip().upper()
                if ref_clean:
                    if part:
                        bom_dict_id.add(f"{part}|{ref_clean}")
                        if ref_clean not in bom_expected:
                            bom_expected[ref_clean] = set()
                        bom_expected[ref_clean].add(part)
                    
                    for sub in subs:
                        if sub:
                            bom_dict_sub.add(f"{sub}|{ref_clean}")

    report_data = []
    placed_refs = set()

    for _, row in df_pl.iterrows():
        if str(row['GSQBN']).strip() not in ['1', '1.0', 'TRUE', 'YES']:
            continue
            
        pl_part = clean_val(row['PART']).upper()
        r_val = str(row['REF']).strip().upper()
        source = row['SOURCE_FILE']

        if not pl_part or not r_val:
            continue
            
        placed_refs.add(r_val)
        
        key = f"{pl_part}|{r_val}"

        if key in bom_dict_id:
            status, match_type, remarks = "MATCH", "ID MATCH", "Matched BOM ID"
        elif key in bom_dict_sub:
            status, match_type, remarks = "MATCH", "SUBSTITUTE MATCH", "Matched Substitute List"
        else:
            status, match_type, remarks = "MISMATCH", "NO MATCH", "Not found in BOM ID or Substitute List"

        report_data.append({
            'PartNumber': pl_part,
            'Reference Designator': r_val,
            'Status': status,
            'Match Type': match_type,
            'Source File': source,
            'Location': 'PLACEMENT',
            'Remarks': remarks
        })

    for ref, expected_parts in bom_expected.items():
        if ref not in placed_refs:
            for p in expected_parts:
                report_data.append({
                    'PartNumber': p,
                    'Reference Designator': ref,
                    'Status': 'MISMATCH',
                    'Match Type': 'NO MATCH',
                    'Source File': '-',
                    'Location': 'BOM',
                    'Remarks': 'Not found in Placement'
                })

    if not report_data:
        raise ValueError("No usable BOM part/reference rows were found.")

    return {
        'is_valid': all(d['Status'] == 'MATCH' for d in report_data),
        'file_buffer': _build_step1_report(report_data),
        'filename': out_filename
    }


# =====================================================================
# --- FORMATTER FOR STEP 2 (FLAT, RAW OUTPUT) ---
# =====================================================================
def _format_step2_flat(ws, df, start_row):
    """Write the unmerged value-only output created by VBA Module3."""
    wrap_align = Alignment(wrap_text=True)
    nowrap_align = Alignment(wrap_text=False)

    for idx, (_, row) in enumerate(df.iterrows()):
        r = start_row + idx
        ws.cell(row=r, column=1).value = _safe_get(row, 'Module', 'MODULE')
        ws.cell(row=r, column=2).value = _safe_get(row, 'Slot', 'SLOT')
        ws.cell(row=r, column=3).value = _safe_get(row, 'SideNo', 'SIDENO')
        ws.cell(row=r, column=4).value = _safe_get(row, 'PartNo', 'PARTNO')
        ws.cell(row=r, column=5).value = _safe_get(row, 'Sub/Alternate', 'SUB_ALTERNATE')
        ws.cell(row=r, column=6).value = _safe_get(row, 'RefDes', 'REFDES')

        qty_val = _safe_get(row, 'Qty', 'QTY')
        try:
            qty_int = int(float(qty_val)) if str(qty_val).strip() not in ('', 'nan', 'None') else ''
        except (TypeError, ValueError):
            qty_int = qty_val

        ws.cell(row=r, column=7).value = qty_int
        ws.cell(row=r, column=8).value = _safe_get(row, 'FeederName', 'FEEDERNAME')
        ws.cell(row=r, column=9).value = _safe_get(row, 'Package', 'PACKAGE')
        ws.cell(row=r, column=10).value = _safe_get(row, 'Description', 'DESCRIPTION')
        # Module3 copies the database text exactly; zero tolerances remain zero.
        ws.cell(row=r, column=11).value = _safe_get(row, 'Min+Unit', 'MIN+UNIT', 'MIN')
        ws.cell(row=r, column=12).value = _safe_get(row, 'Max+Unit', 'MAX+UNIT', 'MAX')

        ws.cell(row=r, column=5).alignment = wrap_align
        ws.cell(row=r, column=6).alignment = nowrap_align

    # The macro assigns E=40 and F=80, then calls AutoFit on A:L. AutoFit is
    # therefore the final operation. openpyxl has no AutoFit engine, so use an
    # equivalent content-width calculation on the same populated cells.
    _autofit_columns(ws, 1, 12, 1, max(ws.max_row, start_row - 1))


def _vba_trim(value):
    """Equivalent of Trim(CStr(value)) for the scalar values used by the macros."""
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return ''
    if isinstance(value, float) and value.is_integer():
        return str(int(value)).strip()
    return str(value).strip()


def _excel_numeric_scalar(value):
    """Mimic Excel's CSV conversion for Module and Slot cells."""
    text = _vba_trim(value)
    if not text:
        return ''
    try:
        number = float(text)
    except ValueError:
        return text
    if not math.isfinite(number):
        return text
    return int(number) if number.is_integer() else number


def _autofit_columns(ws, first_col, last_col, first_row, last_row):
    """Set Excel-style content widths for the value range used by VBA AutoFit."""
    for col_idx in range(first_col, last_col + 1):
        max_len = 0
        for row_idx in range(first_row, last_row + 1):
            value = ws.cell(row=row_idx, column=col_idx).value
            if value is None:
                continue
            max_len = max(max_len, *(len(line) for line in str(value).splitlines() or ['']))
        # Excel caps ColumnWidth at 255 characters.
        ws.column_dimensions[get_column_letter(col_idx)].width = min(max(max_len + 2, 1), 255)


def _decode_delimited_rows(file_obj):
    _seek0(file_obj)
    content = file_obj.read()
    if isinstance(content, bytes):
        try:
            content = content.decode('utf-8-sig')
        except UnicodeDecodeError:
            content = content.decode('latin-1')

    sample = content[:8192]
    try:
        delimiter = csv.Sniffer().sniff(sample, delimiters=',;\t|').delimiter
    except csv.Error:
        delimiter = ','
    return list(csv.reader(io.StringIO(content), delimiter=delimiter))


def _row_value(row, one_based_column):
    index = one_based_column - 1
    return row[index] if index < len(row) else ''


# =====================================================================
# --- FORMATTER FOR STEP 3 (DYNAMIC SPLITTING & MERGING) ---
# =====================================================================
def _split_ref_des_dynamic(full_text, split_count):
    """Port of Module2.SplitRefDesDynamic, including VBA's 1-based slicing."""
    if split_count <= 1:
        return [full_text]

    txt_len = len(full_text)
    parts = []
    start_pos = 1  # VBA string positions are one-based.

    for i in range(1, split_count):
        # Python round and VBA CLng both use banker's rounding for .5 values.
        target_pos = round(txt_len * i / split_count)
        comma_index = full_text.rfind(',', 0, target_pos)
        split_pos = comma_index + 1 if comma_index >= 0 else 0

        if split_pos <= start_pos:
            split_pos = target_pos

        zero_start = max(start_pos - 1, 0)
        zero_end = max(split_pos - 1, zero_start)
        parts.append(full_text[zero_start:zero_end].strip())
        start_pos = split_pos + 1

    parts.append(full_text[max(start_pos - 1, 0):].strip())
    return parts


def _estimated_excel_wrapped_height(text, column_width, font_size=11):
    """Mirror the Euclid 11 RefDes AutoFit pattern in the approved workbook."""
    if not text:
        return 14.0
    # In the approved workbook F=60.6328125. Its long rows resolve to roughly
    # 55.6 text characters per 14-point line (for example rows 8-13).
    usable_chars = max(float(column_width or 60.6328125) - 5.0, 1.0)
    line_count = 0
    for logical_line in str(text).split('\n'):
        line_count += max(1, math.ceil(len(logical_line) / usable_chars))
    line_height = 14.0
    return min(line_count * line_height, 409.5)


_STEP3_REFERENCE_WIDTHS = {
    'A': 10.6328125,
    'B': 8.08984375,
    'C': 10.26953125,
    'D': 12.7265625,
    'E': 14.26953125,
    'F': 60.6328125,
    'G': 10.6328125,
    'H': 14.0,
    'I': 12.1796875,
    'J': 50.6328125,
    'K': 14.1796875,
    'L': 13.0,
}


def _format_step3_merged(ws, df, start_row, part_counts):
    """Apply Module2's copy, split, duplicate, merge, and sizing operations."""
    # The old template has F near 10 characters wide, but the approved output
    # measures and displays RefDes at 60.6328125. Using the narrow template
    # width here was the reason normal records were split into extra rows.
    initial_refdes_width = _STEP3_REFERENCE_WIDTHS['F']
    split_rows = []
    for _, row in df.iterrows():
        ref_des = str(_safe_get(row, 'RefDes', 'REFDES')).strip()

        estimated_height = _estimated_excel_wrapped_height(
            ref_des,
            initial_refdes_width,
            ws.cell(start_row, 6).font.sz or 11,
        )
        if estimated_height > 800:
            split_count = 4
        elif estimated_height > 400:
            split_count = 3
        elif estimated_height > 200:
            split_count = 2
        else:
            split_count = 1

        if split_count > 1:
            for part in _split_ref_des_dynamic(ref_des, split_count):
                new_row = row.copy()
                new_row['RefDes'] = part
                split_rows.append(new_row)
        else:
            new_row = row.copy()
            new_row['RefDes'] = ref_des
            split_rows.append(new_row)

    df_processed = pd.DataFrame(split_rows)
    if df_processed.empty:
        return
    df_processed = df_processed.fillna('')

    for idx, (_, row) in enumerate(df_processed.iterrows()):
        r = start_row + idx
        ws.cell(row=r, column=1).value = _safe_get(row, 'Module', 'MODULE')
        ws.cell(row=r, column=2).value = _safe_get(row, 'Slot', 'SLOT')
        ws.cell(row=r, column=3).value = _safe_get(row, 'SideNo', 'SIDENO')
        ws.cell(row=r, column=4).value = _safe_get(row, 'PartNo', 'PARTNO')
        ws.cell(row=r, column=5).value = _safe_get(row, 'Sub/Alternate', 'SUB_ALTERNATE')
        ws.cell(row=r, column=6).value = _safe_get(row, 'RefDes', 'REFDES')

        qty_val = _safe_get(row, 'Qty', 'QTY')
        try:
            qty_int = int(float(qty_val)) if str(qty_val).strip() not in ('', 'nan', 'None') else ''
        except (TypeError, ValueError):
            qty_int = qty_val

        ws.cell(row=r, column=7).value = qty_int
        ws.cell(row=r, column=8).value = _safe_get(row, 'FeederName', 'FEEDERNAME')
        ws.cell(row=r, column=9).value = _safe_get(row, 'Package', 'PACKAGE')
        ws.cell(row=r, column=10).value = _safe_get(row, 'Description', 'DESCRIPTION')
        ws.cell(row=r, column=11).value = _safe_get(row, 'Min+Unit', 'MIN+UNIT', 'MIN')
        ws.cell(row=r, column=12).value = _safe_get(row, 'Max+Unit', 'MAX+UNIT', 'MAX')

    max_r = start_row + len(df_processed) - 1

    # Duplicate highlighting happens after splitting, so one source row split
    # into multiple output rows is also considered a duplicate by Module2.
    duplicate_counts = {}
    for r in range(start_row, max_r + 1):
        key = _vba_trim(ws.cell(r, 4).value)
        if key:
            duplicate_counts[key] = duplicate_counts.get(key, 0) + 1
    for r in range(start_row, max_r + 1):
        key = _vba_trim(ws.cell(r, 4).value)
        if key and duplicate_counts.get(key, 0) > 1:
            cell = ws.cell(r, 4)
            cell.fill = PatternFill('solid', fgColor='000000')
            font = copy(cell.font)
            font.color = Color(rgb='FFFFFFFF')
            cell.font = font

    _autofit_columns(ws, 1, 12, 1, max_r)

    # Module2 skips SideNo, RefDes, and Qty while merging consecutive PartNo rows.
    cols_to_merge = [1, 2, 4, 5, 8, 9, 10, 11, 12]
    start_merge_row = start_row
    while start_merge_row <= max_r:
        part_val = _vba_trim(ws.cell(row=start_merge_row, column=4).value)
        end_merge_row = start_merge_row
        while end_merge_row < max_r:
            next_r = end_merge_row + 1
            if _vba_trim(ws.cell(row=next_r, column=4).value) == part_val:
                end_merge_row += 1
            else:
                break

        if end_merge_row > start_merge_row:
            for col in cols_to_merge:
                ws.merge_cells(start_row=start_merge_row, start_column=col, end_row=end_merge_row, end_column=col)
                merged_cell = ws.cell(row=start_merge_row, column=col)
                alignment = copy(merged_cell.alignment)
                alignment.horizontal = 'center'
                alignment.vertical = 'center'
                merged_cell.alignment = alignment
        start_merge_row = end_merge_row + 1

    _autofit_columns(ws, 1, 12, 1, max_r)
    for column_letter, width in _STEP3_REFERENCE_WIDTHS.items():
        ws.column_dimensions[column_letter].width = width

    for r in range(start_row, max_r + 1):
        cell_f = ws.cell(r, 6)
        alignment_f = copy(cell_f.alignment)
        alignment_f.wrap_text = True
        alignment_f.vertical = 'top'
        cell_f.alignment = alignment_f

        cell_j = ws.cell(r, 10)
        alignment_j = copy(cell_j.alignment)
        alignment_j.horizontal = 'left'
        alignment_j.vertical = 'center'
        cell_j.alignment = alignment_j

        ws.row_dimensions[r].height = _estimated_excel_wrapped_height(
            _vba_trim(cell_f.value),
            ws.column_dimensions['F'].width,
            cell_f.font.sz or 11,
        )


def _read_and_flatten_feeder_file(file_obj):
    _seek0(file_obj)
    content = file_obj.read()
    wb = load_workbook(io.BytesIO(content), data_only=True)
    ws = wb.worksheets[0]

    last_row = 0
    last_col = 0
    for row in ws.iter_rows():
        for cell in row:
            if cell.value not in (None, ''):
                last_row = max(last_row, cell.row)
                last_col = max(last_col, cell.column)

    if last_row < 1:
        return pd.DataFrame(columns=_STANDARD_OUTPUT_COLUMNS)

    headers = [_vba_trim(ws.cell(1, c).value).upper() for c in range(1, last_col + 1)]
    import_map = {'SUB/ALTERNATE': 'SUB_ALTERNATE', 'MIN+UNIT': 'MIN', 'MAX+UNIT': 'MAX'}
    headers = [import_map.get(header, header) for header in headers]

    records = []
    for r in range(2, last_row + 1):
        values = [ws.cell(r, c).value for c in range(1, last_col + 1)]
        if any(value not in (None, '') for value in values):
            records.append(dict(zip(headers, values)))

    return pd.DataFrame(records, columns=headers).fillna('')


# =====================================================================
# --- STEP 2.1: FEEDER INPUT & DATA ASSEMBLY ---
# =====================================================================
_FEEDER_SETUP_ALIASES = {
    'MODULE': {'MODULE', 'MOD', 'MODULENUMBER', 'MODULE NUMBER'},
    'SLOT': {'SLOT', 'LOCATION', 'LOC', 'FEEDER LOCATION', 'FEEDER SLOT'},
    'SIDENO': {'SIDENO', 'SIDE', 'SIDE NO', 'SIDE_NO', 'MACHINE SIDE'},
    'PARTNO': {'PARTNO', 'PART NO', 'PART NUMBER', 'PARTNUMBER', 'PN', 'P/N'},
    'FEEDERNAME': {'FEEDERNAME', 'FEEDER NAME', 'FEEDER', 'FEEDER ID', 'FEEDER_ID'},
    'PACKAGE': {'PACKAGE', 'PKG', 'PACKAGE TYPE', 'PACKAGENAME', 'PACKAGE NAME'},
}

_FEEDER_SETUP_POSITIONS = {
    'MODULE': 3, 'SIDENO': 4, 'PARTNO': 8,
    'FEEDERNAME': 9, 'SLOT': 11, 'PACKAGE': 15,
}

def _resolve_feeder_setup_columns(df_setup):
    columns = list(df_setup.columns)
    resolved = {}
    for canonical, aliases in _FEEDER_SETUP_ALIASES.items():
        resolved[canonical] = next((c for c in columns if c in aliases), None)
    still_missing = [k for k, v in resolved.items() if v is None]
    if still_missing and len(columns) > max(_FEEDER_SETUP_POSITIONS.values()):
        for canonical in still_missing:
            resolved[canonical] = columns[_FEEDER_SETUP_POSITIONS[canonical]]
    rename_map = {v: k for k, v in resolved.items() if v is not None}
    return df_setup.rename(columns=rename_map)

def _infer_feeder_list_filename(placement_file):
    stem = Path(str(getattr(placement_file, 'name', '') or '')).stem.upper()
    if stem.endswith('_T') or stem.endswith('TOP'): side = 'T'
    elif stem.endswith('_B') or stem.endswith('BOTTOM'): side = 'B'
    else: side = None
    return f"Feeder List_{side}.xlsx" if side else "Feeder List.xlsx"


def _normalize_pcb_side(value):
    """Return the exact PCB-side labels used by the approved VBA output."""
    text = _vba_trim(value).upper()
    if text in {'T', 'TOP'}:
        return 'Top'
    if text in {'B', 'BOT', 'BOTTOM'}:
        return 'Bottom'
    return ''


def _infer_pcb_side_from_filename(file_obj):
    stem = Path(str(getattr(file_obj, 'name', '') or '')).stem.upper()
    if stem.endswith('_T') or stem.endswith('TOP'):
        return 'Top'
    if stem.endswith('_B') or stem.endswith('BOTTOM'):
        return 'Bottom'
    return ''


def generate_feeder_list(placement_file, feeder_setup_file, minmax_db_file, side=None):
    resolved_side = _normalize_pcb_side(side) or _infer_pcb_side_from_filename(placement_file)
    placement_rows = _decode_delimited_rows(placement_file)
    if len(placement_rows) < 3:
        raise ValueError('Placement file must contain its headers on row 3.')

    placement_headers = {
        _vba_trim(value).upper(): index
        for index, value in enumerate(placement_rows[2])
    }
    required_headers = ('GSQSC', 'GSQBN', 'PARTNUMBER', 'MEMO')
    missing = [header for header in required_headers if header not in placement_headers]
    if missing:
        raise ValueError(
            'Cannot locate GSQSC, GSQBN, PARTNUMBER or MEMO headers in placement row 3.'
        )

    refdes_map = {}
    qty_map = {}
    memo_map = {}
    part_col = placement_headers['PARTNUMBER']
    ref_col = placement_headers['GSQSC']
    enabled_col = placement_headers['GSQBN']
    memo_col = placement_headers['MEMO']

    # Module3 begins at row 4 and stops at the last non-empty PartNumber row.
    last_placement_index = 2
    for index in range(3, len(placement_rows)):
        if _vba_trim(_row_value(placement_rows[index], part_col + 1)):
            last_placement_index = index

    for row in placement_rows[3:last_placement_index + 1]:
        if _vba_trim(_row_value(row, enabled_col + 1)) != '1':
            continue
        part = _vba_trim(_row_value(row, part_col + 1))
        refdes = _vba_trim(_row_value(row, ref_col + 1))
        if not part:
            continue
        if part in refdes_map:
            refdes_map[part] = f'{refdes_map[part]}, {refdes}'
            qty_map[part] += 1
        else:
            refdes_map[part] = refdes
            qty_map[part] = 1
        if part not in memo_map:
            memo_map[part] = _vba_trim(_row_value(row, memo_col + 1))

    _seek0(minmax_db_file)
    db_content = minmax_db_file.read()
    db_wb = load_workbook(io.BytesIO(db_content), data_only=True)
    db_ws = db_wb.worksheets[0]
    db_map = {}
    last_db_row = 1
    for row_idx in range(2, db_ws.max_row + 1):
        if _vba_trim(db_ws.cell(row_idx, 1).value):
            last_db_row = row_idx
    for row_idx in range(2, last_db_row + 1):
        part = _vba_trim(db_ws.cell(row_idx, 1).value)
        if part and part not in db_map:
            db_map[part] = (
                _vba_trim(db_ws.cell(row_idx, 3).value),
                _vba_trim(db_ws.cell(row_idx, 10).value),
                _vba_trim(db_ws.cell(row_idx, 11).value),
            )

    feeder_rows = _decode_delimited_rows(feeder_setup_file)
    last_feeder_index = 0
    for index, row in enumerate(feeder_rows):
        if _vba_trim(_row_value(row, 1)):
            last_feeder_index = index

    records = []
    for row in feeder_rows[1:last_feeder_index + 1]:
        part = _vba_trim(_row_value(row, 9))
        if not part:
            continue
        pcb_side = resolved_side or _normalize_pcb_side(_row_value(row, 5))
        if not pcb_side:
            raise ValueError(
                'PCB side could not be determined. Name the placement file with '
                'a _T/_B or TOP/BOTTOM suffix, or provide Top/Bottom in feeder setup column 5.'
            )
        location = _vba_trim(_row_value(row, 12))
        slot = _vba_trim(location.split(',', 1)[0])
        description, min_value, max_value = db_map.get(part, ('', '', ''))
        records.append({
            'MODULE': _excel_numeric_scalar(_row_value(row, 4)),
            'SLOT': _excel_numeric_scalar(slot),
            'SIDENO': pcb_side,
            'PARTNO': part,
            'SUB_ALTERNATE': memo_map.get(part, ''),
            'REFDES': refdes_map.get(part, ''),
            'QTY': qty_map.get(part, 0),
            'FEEDERNAME': _vba_trim(_row_value(row, 10)),
            'PACKAGE': _vba_trim(_row_value(row, 16)),
            'DESCRIPTION': description,
            'MIN': min_value,
            'MAX': max_value,
        })

    # The VBA macro deletes worksheet rows 2 and 3 after populating the list.
    # Because OutRow advances only for non-empty PartNo records, this removes
    # the first two emitted feeder records exactly.
    records = records[2:]
    final_df = pd.DataFrame(records, columns=_STANDARD_OUTPUT_COLUMNS)
    export_df = final_df.rename(columns=_STANDARD_EXPORT_HEADERS)

    wb = Workbook()
    ws = wb.active
    headers = list(export_df.columns)
    ws.append(headers)

    step2_font = Font(bold=True)
    
    # Module3: bold headers, no fill, no borders, and no frozen pane.
    for cell in ws[1]:
        cell.font = step2_font

    _format_step2_flat(ws, export_df, 2)

    output = io.BytesIO()
    wb.save(output)
    output.seek(0)
    return {
        'file_buffer': output,
        'filename': _infer_feeder_list_filename(placement_file),
    }


# =====================================================================
# --- STEP 2.2: COMBINE ---
# =====================================================================
_STEP2_COMBINED_REFERENCE_WIDTHS = {
    'A': 7.0,
    'B': 7.1796875,
    'C': 6.81640625,
    'D': 7.81640625,
    'E': 12.453125,
    'F': 255.6328125,
    'G': 3.81640625,
    'H': 12.0,
    'I': 26.26953125,
    'J': 41.1796875,
    'K': 12.1796875,
    'L': 13.0,
}


def _combined_excel_general_value(value):
    """Mimic Excel General-cell conversion used by Module4's value copy."""
    text = _vba_trim(value)
    if not text:
        return None

    # Older web feeder lists stored a literal leading apostrophe in Slot.
    # Excel treats that apostrophe as a text-entry marker rather than data.
    if text.startswith("'"):
        text = text[1:]

    try:
        number = float(text)
    except ValueError:
        return text

    if not math.isfinite(number):
        return text
    return int(number) if number.is_integer() else number


def combine_feeder_lists(file_t, file_b):
    df_t = _read_and_flatten_feeder_file(file_t)
    df_b = _read_and_flatten_feeder_file(file_b)

    # The web form explicitly identifies these uploads as Top and Bottom.
    # Enforce those roles so an unrelated numeric feeder-setup field (such as
    # 0) can never leak into the final PCB Side column.
    df_t['SIDENO'] = 'Top'
    df_b['SIDENO'] = 'Bottom'

    # Father's Module4 selection order was Feeder List_B followed by
    # Feeder List_T. Excel's sort is stable for identical Module/Slot pairs,
    # so Bottom must be concatenated first to reproduce the approved file.
    combined = pd.concat([df_b, df_t], ignore_index=True)
    _require_columns(combined, ['MODULE', 'SLOT', 'SIDENO', 'PARTNO'], 'Feeder files')

    # The approved workbook identifies every auxiliary A-/B- location as an
    # MTU tray, regardless of package text inherited from the feeder export.
    for index in combined.index:
        slot_text = _vba_trim(combined.at[index, 'SLOT'])
        if slot_text.startswith("'"):
            slot_text = slot_text[1:]
        if re.match(r'^[AB]-', slot_text, flags=re.IGNORECASE):
            combined.at[index, 'FEEDERNAME'] = 'MTU'
            combined.at[index, 'PACKAGE'] = 'Tray'

    def excel_sort_key(value):
        normalized = _combined_excel_general_value(value)
        if normalized is None:
            return (2, '')
        if isinstance(normalized, (int, float)) and not isinstance(normalized, bool):
            return (0, float(normalized))
        return (1, _vba_trim(normalized).casefold())

    # Module4 sorts by Module, then Slot only. File-selection/input order is
    # retained as the stable tie-breaker; PCB side is not a sort criterion.
    ordered_indices = sorted(
        range(len(combined)),
        key=lambda index: (
            excel_sort_key(combined.iloc[index]['MODULE']),
            excel_sort_key(combined.iloc[index]['SLOT']),
        ),
    )
    combined = combined.iloc[ordered_indices].reset_index(drop=True)

    for col in _STANDARD_OUTPUT_COLUMNS:
        if col not in combined.columns:
            combined[col] = ''

    # Range-to-range assignment in Excel converts numeric-looking General
    # values to numbers and removes the entry apostrophe from text cells.
    for column in ('MODULE', 'SLOT', 'PARTNO', 'SUB_ALTERNATE', 'QTY'):
        combined[column] = combined[column].map(_combined_excel_general_value)

    export_df = combined[_STANDARD_OUTPUT_COLUMNS].rename(columns=_STANDARD_EXPORT_HEADERS)

    wb = Workbook()
    ws = wb.active
    ws.title = 'Combined_Data'
    headers = list(export_df.columns)
    ws.append(headers)

    # Module4 applies a light-blue bold header. The approved workbook uses
    # Excel's Aptos Narrow 11 default throughout the populated range.
    step2_combine_fill = PatternFill('solid', fgColor='D9E1F2')
    header_font = Font(name='Aptos Narrow', size=11, bold=True)
    data_font = Font(name='Aptos Narrow', size=11)

    for cell in ws[1]:
        cell.fill = step2_combine_fill
        cell.font = header_font

    for row_offset, values in enumerate(export_df.itertuples(index=False, name=None), start=2):
        for column_offset, value in enumerate(values, start=1):
            cell = ws.cell(row=row_offset, column=column_offset)
            cell.value = None if value is None or _vba_trim(value) == '' else value
            cell.font = data_font

    # These are the exact AutoFit results measured from the approved
    # Feeder_List_Combined.xlsx generated by Module4.
    for column_letter, width in _STEP2_COMBINED_REFERENCE_WIDTHS.items():
        ws.column_dimensions[column_letter].width = width

    ws.freeze_panes = 'A2'

    output = io.BytesIO()
    wb.save(output)
    output.seek(0)
    return output


# =====================================================================
# --- STEP 3: FINAL GENERATOR (MASTER FORMATTING) ---
# =====================================================================
def generate_final_wi_kerno(combined_file, template_file):
    combined_df = _read_and_flatten_feeder_file(combined_file)

    normalized_sides = combined_df['SIDENO'].map(_normalize_pcb_side) if 'SIDENO' in combined_df.columns else None
    if normalized_sides is None or (normalized_sides == '').any():
        raise ValueError(
            'Combined feeder list contains an invalid PCB Side. Regenerate it in Step 2.2 '
            'using the Top feeder file in the Top field and the Bottom feeder file in the Bottom field.'
        )
    combined_df['SIDENO'] = normalized_sides
    
    for col in _STANDARD_OUTPUT_COLUMNS:
        if col not in combined_df.columns:
            combined_df[col] = ''
            
    df_processed = combined_df[_STANDARD_OUTPUT_COLUMNS].rename(columns=_STANDARD_EXPORT_HEADERS)

    _seek0(template_file)
    template_content = template_file.read()
    wb = load_workbook(io.BytesIO(template_content))
    ws = wb.active

    start_row = 7
    # A previously generated loading list can be reused as the template. Its
    # data rows contain vertical merges created by this function, so remove
    # those old data-area merges before clearing and rebuilding the output.
    # Header/title merges above row 7 are deliberately preserved.
    for merged_range in list(ws.merged_cells.ranges):
        if merged_range.max_row >= start_row and merged_range.min_col <= 12:
            ws.unmerge_cells(str(merged_range))

    # Module2 clears prior data, fill colors, and explicit font colors while
    # preserving the template's borders, fonts, alignments, and number formats.
    clear_through_row = max(ws.max_row, start_row + len(df_processed) - 1)
    for row in ws.iter_rows(min_row=start_row, max_row=clear_through_row, min_col=1, max_col=12):
        for cell in row:
            cell.value = None
            cell.fill = PatternFill(fill_type=None)
            font = copy(cell.font)
            font.color = Color(auto=True)
            cell.font = font

    part_counts = df_processed['PartNo'].value_counts().to_dict()

    _format_step3_merged(ws, df_processed, start_row, part_counts)

    output = io.BytesIO()
    wb.save(output)
    output.seek(0)

    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    filename = f'NXT_Device_Loading_List_{timestamp}.xlsx'

    return {
        'file_buffer': output,
        'filename': filename,
    }
