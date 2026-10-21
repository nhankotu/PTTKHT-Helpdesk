"""Browser acceptance for approved FAQ and failed/retried simulated email.

Use an isolated server DB; this script creates a rule and tickets.
"""
from pathlib import Path
import os

from playwright.sync_api import sync_playwright, expect

base=os.getenv('HELPDESK_BASE_URL','http://127.0.0.1:8010')
out=Path(__file__).resolve().parent.parent/'data'/'qa'
out.mkdir(parents=True,exist_ok=True)
question='Làm sao để đặt lại mật khẩu tài khoản?'


def login(page,role):
    page.goto(base)
    page.locator(f'[data-action=demo][data-role={role}]').click()
    page.get_by_role('button',name='Đăng nhập',exact=True).click()
    expect(page.locator('nav')).to_be_visible()


with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.getenv('HELPDESK_BROWSER',r'C:\Program Files\Google\Chrome\Application\chrome.exe'),headless=True)
    staff=browser.new_page(viewport={'width':1440,'height':1000})
    errors=[]
    staff.on('pageerror',lambda error:errors.append(str(error)))
    login(staff,'supervisor')
    staff.locator('nav [data-view=kb]').click()
    card=staff.locator('.kb-card').filter(has=staff.get_by_role('heading',name='Đặt lại mật khẩu tài khoản',exact=True))
    card.get_by_role('button',name='FAQ tự trả lời',exact=True).click()
    staff.locator('#modal [name=question]').fill(question)
    staff.get_by_role('button',name='Duyệt câu hỏi mới',exact=True).click()
    expect(staff.get_by_role('button',name='Tắt tự trả lời',exact=True)).to_be_visible()
    staff.screenshot(path=str(out/'faq-approval.png'),full_page=True)
    staff.get_by_role('button',name='Hủy',exact=True).click()

    customer=browser.new_page(viewport={'width':390,'height':844})
    customer.on('pageerror',lambda error:errors.append(str(error)))
    login(customer,'customer')
    customer.get_by_role('button',name='Tạo yêu cầu',exact=True).click()
    customer.locator('#modal [name=subject]').fill('Hướng dẫn mật khẩu qua FAQ')
    customer.locator('#modal [name=body]').fill(question)
    customer.get_by_role('button',name='Gửi yêu cầu',exact=True).click()
    expect(customer.get_by_text('Tự động · FAQ đã duyệt',exact=True)).to_be_visible(timeout=15000)
    expect(customer.locator('.page-head .badge')).to_have_text('Mới tiếp nhận')
    customer.screenshot(path=str(out/'faq-customer-mobile.png'),full_page=True)

    staff.locator('nav [data-view=tickets]').click()
    staff.get_by_role('button',name='Kênh email mô phỏng',exact=True).click()
    staff.locator('#modal [name=failures_remaining]').fill('1')
    staff.get_by_role('button',name='Lưu',exact=True).click()
    expect(staff.locator('#modal')).not_to_be_visible()
    staff.get_by_role('button',name='Mô phỏng email',exact=True).click()
    staff.locator('#modal [name=external_event_id]').fill('automation-browser-email')
    staff.locator('#modal [name=subject]').fill('Kiểm thử gửi email thất bại')
    staff.locator('#modal [name=body]').fill('Tôi cần hỗ trợ kiểm tra hóa đơn thanh toán.')
    staff.get_by_role('button',name='Gửi yêu cầu',exact=True).click()
    expect(staff.get_by_role('heading',name='Kiểm thử gửi email thất bại',exact=True)).to_be_visible()
    # Wait for initial analysis to settle before composing, avoiding a version race.
    expect(staff.get_by_role('button',name='Phân tích lại',exact=True)).to_be_enabled(timeout=15000)
    staff.locator('#reply').fill('Đây là phản hồi thử nghiệm qua email mô phỏng.')
    staff.get_by_role('button',name='Gửi tin nhắn',exact=False).click()
    expect(staff.get_by_text('Gửi thất bại · Chưa tính SLA phản hồi',exact=False)).to_be_visible(timeout=15000)
    staff.get_by_role('button',name='Nhận xử lý',exact=True).click()
    expect(staff.get_by_role('button',name='Đã giải quyết',exact=True)).to_be_disabled()
    staff.screenshot(path=str(out/'email-delivery-failed.png'),full_page=True)
    staff.get_by_role('button',name='Thử gửi lại',exact=True).click()
    expect(staff.get_by_role('button',name='Đã giải quyết',exact=True)).to_be_enabled(timeout=15000)
    staff.get_by_role('button',name='Đã giải quyết',exact=True).click()
    expect(staff.locator('.page-head .badge')).to_have_text('Đã giải quyết')
    assert not errors,errors
    print('Automation browser acceptance passed: approved FAQ, customer live update, failed delivery, retry and resolution.')
    print(out)
    browser.close()
