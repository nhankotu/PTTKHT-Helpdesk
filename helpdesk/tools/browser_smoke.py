"""Optional real-browser check. Run against a local server on port 8000."""
from pathlib import Path
import os
import time

from playwright.sync_api import sync_playwright, expect

root=Path(__file__).resolve().parent.parent
out=root/'data'/'qa'
out.mkdir(parents=True,exist_ok=True)
browser_path=os.getenv('HELPDESK_BROWSER',r'C:\Program Files\Google\Chrome\Application\chrome.exe')

with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=browser_path,headless=True)
    page=browser.new_page(viewport={'width':1440,'height':1000})
    errors=[]
    page.on('pageerror',lambda error:errors.append(str(error)))
    page.goto(os.getenv('HELPDESK_BASE_URL','http://127.0.0.1:8000'))
    page.get_by_role('button',name='Khách hàng',exact=True).click()
    page.get_by_role('button',name='Đăng nhập',exact=True).click()
    expect(page.get_by_role('heading',name='Yêu cầu của tôi')).to_be_visible()
    page.get_by_role('button',name='Tạo yêu cầu',exact=True).click()
    subject=f'Kiểm thử giao diện · Đặt lại mật khẩu {int(time.time())}'
    page.locator('#modal [name=subject]').fill(subject)
    page.locator('#modal [name=body]').fill('Tôi quên mật khẩu tài khoản, xin hướng dẫn đặt lại mật khẩu.')
    page.get_by_role('button',name='Gửi yêu cầu',exact=True).click()
    expect(page.get_by_role('heading',name=subject,exact=True)).to_be_visible()
    page.get_by_role('button',name='Đăng xuất',exact=True).click()
    page.get_by_role('button',name='Trưởng nhóm',exact=True).click()
    page.get_by_role('button',name='Đăng nhập',exact=True).click()
    expect(page.get_by_role('heading',name='Tổng quan hỗ trợ')).to_be_visible()
    page.screenshot(path=str(out/'dashboard-desktop.png'),full_page=True)
    page.locator('nav [data-view=tickets]').click()
    page.get_by_text(subject,exact=True).click()
    expect(page.get_by_role('heading',name=subject,exact=True)).to_be_visible()
    expect(page.get_by_role('button',name='Dùng làm bản nháp',exact=True)).to_be_visible(timeout=15000)
    page.get_by_role('button',name='Dùng làm bản nháp',exact=True).click()
    expect(page.locator('#reply')).not_to_have_value('')
    page.get_by_role('button',name='Gửi tin nhắn',exact=False).click()
    expect(page.locator('.message.staff')).to_have_count(1)
    expect(page.get_by_text('Đang chờ gửi · Chưa tính SLA phản hồi',exact=False)).not_to_be_visible(timeout=15000)
    page.get_by_role('button',name='Nhận xử lý',exact=True).click()
    page.get_by_role('button',name='Đã giải quyết',exact=True).click()
    expect(page.locator('.page-head .badge')).to_have_text('Đã giải quyết')
    page.screenshot(path=str(out/'ticket-desktop.png'),full_page=True)
    page.set_viewport_size({'width':390,'height':844})
    page.locator('nav [data-view=tickets]').click()
    expect(page.get_by_role('heading',name='Hộp thư hỗ trợ')).to_be_visible()
    page.screenshot(path=str(out/'inbox-mobile.png'),full_page=True)
    # Horizontal scrolling is confined to the table, not the whole page.
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Page overflows mobile viewport'
    expect(page.get_by_role('button',name='Đăng xuất',exact=True)).to_be_visible()
    page.set_viewport_size({'width':1440,'height':1000})
    page.get_by_role('button',name='Đăng xuất',exact=True).click()
    page.get_by_role('button',name='Agent',exact=True).click()
    page.get_by_role('button',name='Đăng nhập',exact=True).click()
    expect(page.get_by_role('heading',name='Hộp thư hỗ trợ')).to_be_visible()
    page.locator('nav [data-view=kb]').click()
    page.get_by_role('button',name='Viết bài mới',exact=True).click()
    kb_title=f'Hướng dẫn QA {int(time.time())}'
    page.locator('#modal [name=title]').fill(kb_title)
    page.locator('#modal [name=body]').fill('Nội dung kiểm thử bài tri thức được Agent soạn và Supervisor duyệt xuất bản.')
    page.get_by_role('button',name='Lưu bản nháp',exact=True).click()
    expect(page.get_by_role('heading',name=kb_title,exact=True)).to_be_visible()
    page.get_by_role('button',name='Đăng xuất',exact=True).click()
    page.get_by_role('button',name='Trưởng nhóm',exact=True).click()
    page.get_by_role('button',name='Đăng nhập',exact=True).click()
    expect(page.get_by_role('heading',name='Tổng quan hỗ trợ')).to_be_visible()
    page.locator('nav [data-view=kb]').click()
    article=page.locator('.kb-card').filter(has=page.get_by_role('heading',name=kb_title,exact=True))
    article.get_by_role('button',name='Xuất bản',exact=True).click()
    expect(article.get_by_role('button',name='Thu hồi',exact=True)).to_be_visible()
    page.get_by_role('button',name='Đăng xuất',exact=True).click()
    page.get_by_role('button',name='Admin',exact=True).click()
    page.get_by_role('button',name='Đăng nhập',exact=True).click()
    expect(page.get_by_role('heading',name='Tổng quan hỗ trợ')).to_be_visible()
    page.locator('nav [data-view=users]').click()
    page.get_by_role('button',name='Thêm thành viên',exact=True).click()
    qa_email=f'qa-{int(time.time())}@helpdesk.local'
    page.locator('#modal [name=name]').fill('Agent kiểm thử')
    page.locator('#modal [name=email]').fill(qa_email)
    page.locator('#modal [name=password]').fill('BrowserTest123!')
    page.locator('#modal [name=team_id]').select_option('1')
    page.get_by_role('button',name='Tạo tài khoản',exact=True).click()
    expect(page.get_by_text(qa_email,exact=True)).to_be_visible()
    page.locator('nav [data-view=sla]').click()
    expect(page.get_by_role('heading',name='Chính sách hiện hành')).to_be_visible()
    page.get_by_role('button',name='Cấu hình',exact=True).first.click()
    page.get_by_role('button',name='Lưu phiên bản mới',exact=True).click()
    expect(page.get_by_role('heading',name='Chính sách hiện hành')).to_be_visible()
    assert not errors,errors
    print('Browser smoke passed: all 4 roles, ticket flow, AI draft, KB publication, user creation, SLA policy, desktop/mobile layout.')
    print(out)
    browser.close()
