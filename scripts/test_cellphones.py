import unittest
import hashlib
import json
from pathlib import Path
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

from cellphones import extract_document, table_to_markdown, chunk_ready_content, section_effective_dates, validate_review, api_request, payload_digest
from bs4 import BeautifulSoup


class ExtractionTests(unittest.TestCase):
    def test_table_keeps_both_levels_of_merged_headers_and_rowspan(self):
        table = BeautifulSoup('''<table><tr><th rowspan="2">Sản phẩm</th><th colspan="2">Trong hạn</th></tr>
            <tr><th>Mới</th><th>Cũ</th></tr><tr><td rowspan="2">Laptop</td><td>20%</td><td>Không áp dụng</td></tr>
            <tr><td>30%</td><td>Thỏa thuận</td></tr></table>''', 'html.parser').table
        result = table_to_markdown(table)
        self.assertIn('| Sản phẩm | Trong hạn / Mới | Trong hạn / Cũ |', result)
        self.assertIn('| Laptop | 20% | Không áp dụng |', result)
        self.assertIn('| Laptop | 30% | Thỏa thuận |', result)

    def test_removes_comments_and_navigation_but_keeps_notes_and_relative_links(self):
        doc = extract_document('''<div id="chinh-sach-giao-hang"><div id="breadcrumbs">NAVIGATION</div>
          <h1>Giao hàng</h1><p class="sub-title">1. Phí giao</p><p>Miễn phí (*)</p><p>(*) Chỉ áp dụng nội thành.</p>
          <p><a href="/huong-dan">Hướng dẫn</a></p><div id="block-comment-cps">PRIVATE COMMENT</div></div>''',
          '#chinh-sach-giao-hang', 'https://cellphones.com.vn/chinh-sach-giao-hang')
        self.assertNotIn('PRIVATE COMMENT', doc['markdown'])
        self.assertNotIn('NAVIGATION', doc['markdown'])
        self.assertIn('(*) Chỉ áp dụng nội thành.', doc['markdown'])
        self.assertIn('https://cellphones.com.vn/huong-dan', doc['markdown'])

    def test_missing_main_content_fails_closed(self):
        with self.assertRaises(ValueError):
            extract_document('<html><p>Access denied</p></html>', '#missing', 'https://cellphones.com.vn/')

    def test_chunk_payload_preserves_section_heading_with_each_paragraph(self):
        doc = extract_document('<article><h1>Chính sách</h1><h2>Đổi trả</h2><p>Điều kiện A.</p><p>Ngoại lệ B.</p></article>', 'article', 'https://cellphones.com.vn/policy')
        chunks = chunk_ready_content(doc, 'https://cellphones.com.vn/policy').split('\n\n')
        self.assertEqual(len(chunks), 1)
        self.assertIn('Điều kiện A.\nNgoại lệ B.', chunks[0])
        self.assertTrue(all('Đổi trả' in chunk for chunk in chunks))
        self.assertTrue(all('Nguồn: https://cellphones.com.vn/policy' in chunk for chunk in chunks))

    def test_images_are_reported_instead_of_silently_dropped(self):
        doc = extract_document('<article><h1>Policy</h1><p>See image:</p><img src="/policy.png"></article>', 'article', 'https://cellphones.com.vn/policy')
        self.assertEqual(doc['images'][0]['url'], 'https://cellphones.com.vn/policy.png')
        self.assertIn('Ảnh nguồn', doc['markdown'])

    def test_empty_links_are_removed_and_lettered_subsections_keep_their_context(self):
        doc = extract_document('<article><h1>Giao hàng</h1><p><a href="/qr"></a>c. Trả góp:</p><p>Điều kiện áp dụng.</p></article>', 'article', 'https://cellphones.com.vn/policy')
        self.assertNotIn('[](', doc['markdown'])
        self.assertIn('c. Trả góp:', doc['blocks'][-1]['path'])

    def test_section_dates_are_not_misrepresented_as_global_effective_dates(self):
        doc = extract_document('<article><h1>Chính sách</h1><h2>4. Hoàn tiền</h2><p>Hiệu lực áp dụng: Kể từ ngày 12/12/2022 đến khi có thông báo thay thế mới.</p></article>', 'article', 'https://cellphones.com.vn/policy')
        self.assertEqual(section_effective_dates(doc)[0]['date'], '2022-12-12')
        self.assertEqual(section_effective_dates(doc)[0]['section_path'][-1], '4. Hoàn tiền')

    def test_ambiguous_sections_stay_in_markdown_but_are_excluded_from_import(self):
        doc = extract_document('<article><h1>Policy</h1><h2>1. Giao hàng</h2><p>Quy tắc giao hàng.</p><h2>2. Hoàn tiền</h2><p>Quy định chưa rõ.</p></article>', 'article', 'https://cellphones.com.vn/policy')
        self.assertIn('Quy định chưa rõ.', doc['markdown'])
        self.assertNotIn('Quy định chưa rõ.', chunk_ready_content(doc, 'https://cellphones.com.vn/policy', ['2. Hoàn tiền']))


class ImportSafetyTests(unittest.TestCase):
    def test_review_covers_metadata_and_actual_raw_snapshot(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'raw.html').write_bytes(b'original')
            sha = hashlib.sha256(b'original').hexdigest()
            article = {'articleCode': 'CPS-TEST', 'content': 'policy', 'title': 'Original', 'accessScope': 'PUBLIC', 'allowAutoReply': False}
            manifest = {'documents': [{'document_id': 'CPS-TEST', 'raw_file': 'raw.html', 'raw_sha256': sha}]}
            review = {'documents': {'CPS-TEST': {'status': 'reviewed', 'payload_sha256': payload_digest(article), 'raw_sha256': sha}}}
            validate_review([article], manifest, review, root)
            for patch in [{'title': 'Changed'}, {'allowAutoReply': True}, {'accessScope': 'INTERNAL_AGENT'}, {'summary': 'Changed'}]:
                with self.assertRaises(ValueError):
                    validate_review([{**article, **patch}], manifest, review, root)
            (root / 'raw.html').write_bytes(b'tampered')
            with self.assertRaises(ValueError):
                validate_review([article], manifest, review, root)

    def test_api_does_not_forward_bearer_through_redirect(self):
        reached = []
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path == '/redirect':
                    self.send_response(302)
                    self.send_header('Location', '/target')
                    self.end_headers()
                else:
                    reached.append(self.headers.get('Authorization'))
                    self.send_response(200)
                    self.end_headers()
                    self.wfile.write(b'{}')
            def log_message(self, *args):
                pass
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with self.assertRaises(RuntimeError):
                api_request(f'http://127.0.0.1:{server.server_port}', '/redirect', 'fake-test-token')
            self.assertEqual(reached, [])
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == '__main__':
    unittest.main()
