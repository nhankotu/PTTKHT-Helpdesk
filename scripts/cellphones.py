"""Collect two approved public policies and import reviewed snapshots into LOCAL KB."""
from __future__ import annotations

import argparse
from collections import OrderedDict
from datetime import datetime, timezone
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen, HTTPRedirectHandler, build_opener

from bs4 import BeautifulSoup, NavigableString, Tag

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'data' / 'cellphones'
USER_AGENT = 'HelpdeskAcademicResearch/0.1 (two public policy pages; local study)'
SOURCES = [
    {'document_id': 'CPS-DOI-TRA', 'slug': 'huong-dan-huy-giao-dich-doi-tra',
     'title': 'CellphoneS — Hủy giao dịch, đổi trả và khiếu nại', 'selector': '.cps-block-content',
     'excluded_sections': ['2.4. Điều kiện hủy giao dịch:']},
    {'document_id': 'CPS-GIAO-HANG', 'slug': 'chinh-sach-giao-hang',
     'title': 'CellphoneS — Thanh toán và giao hàng', 'selector': '#chinh-sach-giao-hang',
     'excluded_sections': ['4. THÔNG TIN VỀ HUỶ ĐƠN HÀNG VÀ THỜI GIAN HOÀN TIỀN:']},
]


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def payload_digest(article: dict) -> str:
    return digest(json.dumps(article, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode())


def validate_review(articles: list[dict], manifest: dict, review: dict, data_root: Path) -> None:
    sources = {d['document_id']: d for d in manifest['documents']}
    if len({a['articleCode'] for a in articles}) != len(articles):
        raise ValueError('Duplicate article codes in import bundle')
    for article in articles:
        source = sources[article['articleCode']]
        approved = review['documents'][article['articleCode']]
        if approved['status'] != 'reviewed' or approved['payload_sha256'] != payload_digest(article):
            raise ValueError('Article payload differs from reviewed snapshot; review again before import')
        raw = data_root / source['raw_file']
        if approved['raw_sha256'] != source['raw_sha256'] or digest(raw.read_bytes()) != approved['raw_sha256']:
            raise ValueError('Raw file differs from reviewed snapshot')


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def inline(node: Tag | NavigableString, source_url: str) -> str:
    if isinstance(node, NavigableString):
        return re.sub(r'\s+', ' ', str(node))
    if node.name == 'br':
        return '\n'
    if node.name in ('script', 'style', 'svg', 'img'):
        return ''
    value = ''.join(inline(child, source_url) for child in node.children)
    if not value.strip():
        return ''
    if node.name == 'a' and node.get('href'):
        target = urljoin(source_url, node['href'])
        if urlparse(target).scheme in ('https', 'http', 'mailto', 'tel'):
            return f'[{value.strip()}]({target})'
    return value


def clean_text(value: str) -> str:
    return '\n'.join(re.sub(r'[^\S\n]+', ' ', line).strip() for line in value.splitlines()).strip()


def table_to_markdown(table: Tag, source_url: str = '') -> str:
    """Expand colspan/rowspan, then retain all levels of the original headers."""
    rows = table.find_all('tr')
    grid: dict[tuple[int, int], str] = {}
    header_count = 0
    for r, row in enumerate(rows):
        cells = row.find_all(['td', 'th'], recursive=False)
        is_header = bool(cells) and all(c.name == 'th' or c.find(['strong', 'b']) for c in cells)
        if r == header_count and is_header:
            header_count += 1
        c = 0
        for cell in cells:
            while (r, c) in grid:
                c += 1
            value = clean_text(inline(cell, source_url)).replace('|', '\\|').replace('\n', '<br>')
            rowspan, colspan = int(cell.get('rowspan', 1)), int(cell.get('colspan', 1))
            for dr in range(rowspan):
                for dc in range(colspan):
                    grid[r + dr, c + dc] = value
            c += colspan
    if not grid:
        raise ValueError('Empty source table')
    width = max(c for _, c in grid) + 1
    height = max(r for r, _ in grid) + 1
    if header_count:
        headers = [' / '.join(dict.fromkeys(grid.get((r, c), '') for r in range(header_count) if grid.get((r, c), ''))) for c in range(width)]
    else:
        headers = [f'Cột {c + 1}' for c in range(width)]
    lines = ['| ' + ' | '.join(headers) + ' |', '| ' + ' | '.join(['---'] * width) + ' |']
    for r in range(header_count, height):
        lines.append('| ' + ' | '.join(grid.get((r, c), '') for c in range(width)) + ' |')
    return '\n'.join(lines)


def extract_document(html: str | bytes, selector: str, source_url: str) -> dict:
    soup = BeautifulSoup(html, 'html.parser')
    selected = soup.select(selector)
    if len(selected) != 1:
        raise ValueError(f'Expected one policy body for {selector}; found {len(selected)}')
    main = selected[0]
    for element in main.select('script, style, noscript, svg, #breadcrumbs, #block-comment-cps, .comment-container'):
        element.decompose()
    blocks: list[dict] = []
    images: list[dict] = []
    headings: list[tuple[int, str]] = []

    def visit(node):
        if isinstance(node, NavigableString):
            value = clean_text(str(node))
            if value:
                blocks.append({'kind': 'paragraph', 'path': [h[1] for h in headings], 'text': value})
            return
        if not isinstance(node, Tag):
            return
        value = clean_text(inline(node, source_url))
        heading_level = int(node.name[1]) if re.fullmatch(r'h[1-6]', node.name) else None
        if node.name == 'p' and ('sub-title' in node.get('class', []) or (len(value) < 180 and re.match(r'^[0-9]+\.\s', value) and value.upper() == value)):
            heading_level = 2
        elif node.name == 'p' and len(value) < 180 and re.match(r'^[a-z]\.\s', value):
            heading_level = 3
        if heading_level and value:
            while headings and headings[-1][0] >= heading_level:
                headings.pop()
            headings.append((heading_level, value))
            blocks.append({'kind': 'heading', 'level': heading_level, 'path': [h[1] for h in headings], 'text': value})
        elif node.name == 'table':
            blocks.append({'kind': 'table', 'path': [h[1] for h in headings], 'text': table_to_markdown(node, source_url)})
        elif node.name in ('p', 'li'):
            if value:
                blocks.append({'kind': 'paragraph', 'path': [h[1] for h in headings], 'text': ('- ' if node.name == 'li' else '') + value})
            for img in node.find_all('img'):
                visit(img)
        elif node.name == 'img':
            src = node.get('src') or node.get('data-src')
            if src:
                url = urljoin(source_url, src)
                images.append({'image_id': f'image-{len(images) + 1}', 'url': url, 'alt': node.get('alt') or '', 'review_status': 'pending'})
                blocks.append({'kind': 'image', 'path': [h[1] for h in headings], 'text': f'![Ảnh nguồn {len(images)}]({url})'})
        else:
            for child in list(node.children):
                visit(child)

    visit(main)
    markdown = '\n\n'.join(('#' * b['level'] + ' ' + b['text']) if b['kind'] == 'heading' else b['text'] for b in blocks)
    if not markdown.strip():
        raise ValueError('Empty policy body')
    return {'markdown': markdown + '\n', 'blocks': blocks, 'images': images,
            'table_count': len(main.find_all('table')), 'plain_text': main.get_text(' ', strip=True)}


def section_effective_dates(document: dict) -> list[dict]:
    found = []
    for block in document['blocks']:
        match = re.search(r'(?:hiệu lực áp dụng|có hiệu lực|áp dụng từ).{0,80}?(\d{1,2}/\d{1,2}/\d{4})', block['text'], re.I)
        if match:
            found.append({'date': datetime.strptime(match[1], '%d/%m/%Y').date().isoformat(),
                          'section_path': block['path'], 'source_text': block['text']})
    return found


def chunk_ready_content(document: dict, source_url: str, excluded_sections: list[str] | None = None) -> str:
    """Keep each section's table, notes and exceptions together for current backend splitter."""
    groups: OrderedDict[tuple[str, ...], list[str]] = OrderedDict()
    for block in document['blocks']:
        if block['kind'] not in ('paragraph', 'table'):
            continue
        if any(section in block['path'] for section in (excluded_sections or [])):
            continue
        path = tuple(block['path']) or ('Nội dung chính sách',)
        groups.setdefault(path, []).append(block['text'])
    return '\n\n'.join('## ' + ' > '.join(path) + '\n' + '\n'.join(texts) + '\nNguồn: ' + source_url for path, texts in groups.items())


def fetch(url: str) -> tuple[bytes, dict]:
    request = Request(url, headers={'User-Agent': USER_AGENT})
    with urlopen(request, timeout=40) as response:
        if urlparse(response.url).hostname not in ('cellphones.com.vn', 'lh7-rt.googleusercontent.com'):
            raise ValueError('Unexpected redirect host; inspect before collecting')
        body = response.read(5_000_001)
        if len(body) > 5_000_000:
            raise ValueError('Source exceeds 5 MB limit')
        return body, {'final_url': response.url, 'content_type': response.headers.get('Content-Type'),
                      'last_modified': response.headers.get('Last-Modified'), 'etag': response.headers.get('ETag')}


def collect(from_snapshots: bool = False) -> None:
    saved = json.loads((DATA / 'sources.json').read_text(encoding='utf-8')) if from_snapshots else None
    previous = {d['document_id']: d for d in saved['documents']} if saved else {}
    timestamp = datetime.fromisoformat(saved['documents'][0]['retrieved_at']) if saved else datetime.now(timezone.utc)
    stamp = timestamp.strftime('%Y%m%dT%H%M%SZ')
    records, articles = [], []
    for source in SOURCES:
        url = 'https://cellphones.com.vn/' + source['slug']
        prior = previous.get(source['document_id'])
        raw, http = ((DATA / prior['raw_file']).read_bytes(), prior['http']) if prior else fetch(url)
        doc = extract_document(raw, source['selector'], url)
        if len(doc['plain_text']) < 1000:
            raise ValueError(f'{source["document_id"]}: suspiciously short policy')
        raw_path = Path('raw') / source['document_id'] / (stamp + '.html')
        (DATA / raw_path).parent.mkdir(parents=True, exist_ok=True)
        (DATA / raw_path).write_bytes(raw)
        md_path = Path('kb') / (source['document_id'] + '.md')
        (DATA / md_path).parent.mkdir(parents=True, exist_ok=True)
        markdown = f'# {source["title"]}\n\nNguồn: {url}\n\nThu thập (UTC): {timestamp.isoformat()}\n\nNgày hiệu lực toàn trang: chưa xác định; mốc hiệu lực theo từng mục được giữ trong nội dung.\n\n---\n\n' + doc['markdown']
        (DATA / md_path).write_text(markdown, encoding='utf-8')
        for i, image in enumerate(doc['images']):
            cached = prior['images'][i] if prior else None
            image_body, image_http = ((DATA / cached['file']).read_bytes(), {'content_type': 'image/png'}) if cached else fetch(image['url'])
            if 'image/png' not in (image_http['content_type'] or ''):
                raise ValueError('Unexpected image format; inspect before importing')
            image_path = Path('raw') / source['document_id'] / f'{stamp}-{image["image_id"]}.png'
            (DATA / image_path).write_bytes(image_body)
            image.update({'file': image_path.as_posix(), 'sha256': digest(image_body)})
        content = chunk_ready_content(doc, url, source['excluded_sections'])
        article = {'articleCode': source['document_id'], 'categoryId': None, 'accessScope': 'PUBLIC', 'allowAutoReply': False,
                   'title': source['title'], 'summary': f'Bản chụp tài liệu công khai CellphoneS. Nguồn: {url}. Thu thập UTC: {timestamp.isoformat()}. Ngày hiệu lực toàn trang: chưa xác định. Tạm loại mục hoàn tiền chưa thống nhất. Không phải dữ liệu đơn hàng trực tiếp.',
                   'content': content}
        articles.append(article)
        records.append({**source, 'business': 'CellphoneS', 'source_url': url, 'retrieved_at': timestamp.isoformat(),
                        'effective_date': None, 'effective_date_note': 'No global effective date established. Scoped dates are listed separately; collection date is not effective date.',
                        'section_effective_dates': section_effective_dates(doc),
                        'raw_file': raw_path.as_posix(), 'raw_sha256': digest(raw), 'markdown_file': md_path.as_posix(),
                        'markdown_sha256': digest(markdown.encode()), 'article_content_sha256': digest(content.encode()),
                        'http': http, 'table_count': doc['table_count'], 'section_chunk_count': len(content.split('\n\n')),
                        'images': doc['images'], 'review_status': 'pending'})
        print(f'{source["document_id"]}: {len(raw)} bytes, {doc["table_count"]} tables, {len(doc["images"])} images')
        if not from_snapshots:
            time.sleep(1)
    write_json(DATA / 'sources.json', {'business': 'CellphoneS', 'documents': records})
    (DATA / 'kb' / 'articles.jsonl').write_text(''.join(json.dumps(a, ensure_ascii=False) + '\n' for a in articles), encoding='utf-8')


def api_request(base: str, path: str, token: str | None = None, payload: dict | None = None):
    headers = {'Accept': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode('utf-8')
    if data is not None:
        headers['Content-Type'] = 'application/json; charset=utf-8'
    request = Request(base.rstrip('/') + path, headers=headers, data=data)
    class RejectRedirects(HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            raise RuntimeError('Local API redirect rejected before forwarding credentials')
    try:
        with build_opener(RejectRedirects()).open(request, timeout=40) as response:
            body = response.read()
            return json.loads(body) if body else None
    except HTTPError as error:
        # Do not dump raw bodies: authentication data may be sensitive.
        raise RuntimeError(f'Local API HTTP {error.code} at {path}') from None


def import_local(base: str) -> None:
    parsed = urlparse(base)
    if parsed.hostname not in ('localhost', '127.0.0.1', '::1') or parsed.scheme not in ('http', 'https'):
        raise ValueError('This importer only writes to a loopback local backend')
    manifest = json.loads((DATA / 'sources.json').read_text(encoding='utf-8'))
    review = json.loads((DATA / 'review.json').read_text(encoding='utf-8'))
    articles = [json.loads(line) for line in (DATA / 'kb' / 'articles.jsonl').read_text(encoding='utf-8').splitlines() if line.strip()]
    validate_review(articles, manifest, review, DATA)
    email = os.getenv('HELPDESK_EMAIL') or input('Local KB editor email: ')
    password = os.getenv('HELPDESK_PASSWORD') or getpass.getpass('Local KB editor password: ')
    auth = api_request(base, '/auth/login', payload={'email': email, 'password': password})
    token = auth['accessToken']
    existing = []
    page = 0
    while True:
        response = api_request(base, f'/kb/articles?page={page}&size=100', token)
        existing.extend(response['items'])
        if page + 1 >= response['totalPages']:
            break
        page += 1
    by_code = {a['articleCode']: a for a in existing}
    results = []
    for article in articles:
        current = by_code.get(article['articleCode'])
        if current:
            matching = next((v for v in current['versions'] if v['content'] == article['content'] and v['title'] == article['title'] and v.get('summary') == article.get('summary') and v['status'] in ('DRAFT', 'IN_REVIEW', 'PUBLISHED')), None)
            if matching:
                version = matching
            else:
                version = api_request(base, f'/kb/articles/{current["id"]}/versions', token,
                                      {k: article[k] for k in ('title', 'summary', 'content')})
            article_id = current['id']
        else:
            created = api_request(base, '/kb/articles', token, article)
            article_id, version = created['id'], created['versions'][0]
        if version['status'] == 'DRAFT':
            version = api_request(base, f'/kb/versions/{version["id"]}/submit', token, {})
        if version['status'] == 'IN_REVIEW':
            version = api_request(base, f'/kb/versions/{version["id"]}/publish', token, {})
        detail = api_request(base, f'/kb/articles/{article_id}', token)
        if detail['currentPublishedVersionId'] != version['id']:
            raise ValueError('Imported version is not the current published version')
        result = {'article_code': article['articleCode'], 'article_id': article_id, 'version_id': version['id'],
                  'status': version['status'], 'content_sha256': digest(article['content'].encode()), 'expected_chunks': len(article['content'].split('\n\n'))}
        results.append(result)
        print(f'{article["articleCode"]}: article={article_id}, version={version["id"]}, {version["status"]}')
    write_json(DATA / 'local-import.json', {'api_base': base, 'imported_at': datetime.now(timezone.utc).isoformat(), 'articles': results})


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['collect', 'import-local'])
    parser.add_argument('--api-base', default='http://localhost:8080/api/v1')
    parser.add_argument('--from-snapshots', action='store_true', help='Re-extract saved raw HTML without network access')
    args = parser.parse_args()
    try:
        collect(args.from_snapshots) if args.action == 'collect' else import_local(args.api_base)
    except (ValueError, RuntimeError, URLError, FileNotFoundError, KeyError) as error:
        print(f'Error: {error}', file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == '__main__':
    main()
