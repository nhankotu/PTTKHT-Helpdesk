import { Link } from "react-router-dom";
import { Alert, Button } from "antd";
import {
  ArrowRightOutlined,
  CheckCircleFilled,
  CheckOutlined,
  ClockCircleOutlined,
  InboxOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { SiteHeader } from "../../../shared/components/SiteHeader";
import { Brand } from "../../../shared/components/Brand";
import { InboxPreview } from "../components/InboxPreview";
import { useAuth } from "../../auth/hooks/useAuth";
import { apiErrorMessage } from "../../../shared/api/client";

const features = [
  {
    icon: <InboxOutlined aria-hidden="true" />,
    title: "Một hộp thư, mọi kênh",
    description:
      "Tập trung yêu cầu từ web, email và chat. Hội thoại luôn liền mạch, thông tin luôn đầy đủ.",
    color: "mint",
  },
  {
    icon: <RobotOutlined aria-hidden="true" />,
    title: "Thêm một trợ lý AI",
    description:
      "Phân loại yêu cầu và gợi ý câu trả lời có dẫn nguồn. Đội ngũ của bạn luôn là người quyết định.",
    color: "blue",
  },
  {
    icon: <ClockCircleOutlined aria-hidden="true" />,
    title: "Không bỏ lỡ cam kết",
    description:
      "Theo dõi thời hạn phản hồi, nhận cảnh báo và ưu tiên những yêu cầu cần được hỗ trợ sớm.",
    color: "sand",
  },
];

export function HomePage() {
  const { user, restoreError, retryRestore, logout } = useAuth();
  return (
    <>
      <SiteHeader />
      <main id="main-content">
        <section className="hero-section">
          <div className="page-container">
            {restoreError ? (
              <Alert
                className="session-alert"
                type="warning"
                showIcon
                title="Chưa thể khôi phục phiên đăng nhập"
                description={apiErrorMessage(restoreError)}
                action={
                  <div className="flex gap-2">
                    <Button onClick={retryRestore}>Thử lại</Button>
                    <Button onClick={() => void logout()}>Xóa phiên</Button>
                  </div>
                }
              />
            ) : null}
            {user && (
              <div className="welcome-panel" role="status">
                <span className="welcome-avatar">
                  <UserOutlined aria-hidden="true" />
                </span>
                <div>
                  <h2>Xin chào, {user.fullName}</h2>
                  <p>Bạn đã đăng nhập thành công với tài khoản {user.email}.</p>
                </div>
                <span className="welcome-badge">
                  <CheckCircleFilled aria-hidden="true" /> Đã kết nối
                </span>
              </div>
            )}
            <div className="hero-grid">
              <div className="hero-copy">
                <div className="hero-eyebrow">
                  <span className="online-dot" /> Hỗ trợ liền mạch, kết nối gần
                  hơn
                </div>
                <h1>
                  Mọi yêu cầu.
                  <br />
                  Một nơi kết nối.
                </h1>
                <p className="hero-description">
                  Biến mỗi cuộc hội thoại thành một trải nghiệm hỗ trợ tốt hơn.
                  Kết nối khách hàng và đội ngũ của bạn trên một nền tảng
                  Helpdesk tích hợp AI.
                </p>
                <div className="hero-actions">
                  <a
                    className="button-link"
                    href={user ? "#platform" : "/register"}
                  >
                    {user ? "Khám phá nền tảng" : "Bắt đầu sử dụng"}
                    <ArrowRightOutlined aria-hidden="true" />
                  </a>
                  <a className="text-link" href="#how-it-works">
                    Khám phá cách hoạt động <span aria-hidden="true">↗</span>
                  </a>
                </div>
                <div className="hero-assurances">
                  <span>
                    <CheckOutlined aria-hidden="true" /> Tiếp nhận đa kênh
                  </span>
                  <span>
                    <CheckOutlined aria-hidden="true" /> AI có dẫn nguồn
                  </span>
                  <span>
                    <CheckOutlined aria-hidden="true" /> Theo dõi SLA
                  </span>
                </div>
                <div className="hero-people">
                  <div className="avatar-stack" aria-hidden="true">
                    <span>MA</span>
                    <span>HN</span>
                    <span>TH</span>
                    <span>＋</span>
                  </div>
                  <p>
                    Khách hàng và đội ngũ hỗ trợ.
                    <br />
                    <strong>Luôn trên cùng một nhịp.</strong>
                  </p>
                </div>
              </div>
              <InboxPreview />
            </div>
          </div>
        </section>
        <section id="platform" className="platform-section page-container">
          <div className="section-intro">
            <div>
              <span className="section-kicker">
                Được kết nối để hỗ trợ tốt hơn
              </span>
              <h2>Bớt thao tác. Thêm thấu hiểu.</h2>
            </div>
            <p>
              Từ yêu cầu đầu tiên đến khi vấn đề được giải quyết,
              <br className="desktop-break" /> mọi thông tin đều ở đúng nơi bạn
              cần.
            </p>
          </div>
          <div className="feature-grid">
            {features.map((feature) => (
              <article className="feature-item" key={feature.title}>
                <span className={`feature-icon ${feature.color}`}>
                  {feature.icon}
                </span>
                <h3>{feature.title}</h3>
                <p>{feature.description}</p>
              </article>
            ))}
          </div>
        </section>
        <section id="how-it-works" className="workflow-section">
          <div className="page-container workflow-grid">
            <div>
              <span className="section-kicker">Một quy trình xuyên suốt</span>
              <h2>
                Để mỗi yêu cầu
                <br />
                đều có lời giải.
              </h2>
              <p>
                Khách hàng dễ dàng gửi yêu cầu.
                <br />
                Đội ngũ có đủ thông tin để hỗ trợ.
              </p>
              <div className="workflow-trust">
                <SafetyCertificateOutlined aria-hidden="true" />
                <span>
                  Phân quyền rõ ràng.
                  <br />
                  <strong>Thông tin nội bộ được tách riêng.</strong>
                </span>
              </div>
            </div>
            <ol className="workflow-steps">
              <li>
                <span>1</span>
                <div>
                  <h3>Tiếp nhận & kết nối</h3>
                  <p>
                    Yêu cầu được tập hợp thành ticket, giữ lại đầy đủ nội dung
                    và kênh liên hệ.
                  </p>
                </div>
              </li>
              <li>
                <span>2</span>
                <div>
                  <h3>Phân công & hỗ trợ</h3>
                  <p>
                    Chuyển đến đội ngũ phù hợp, kết hợp gợi ý AI và kho kiến
                    thức để trả lời.
                  </p>
                </div>
              </li>
              <li>
                <span>3</span>
                <div>
                  <h3>Giải quyết & lắng nghe</h3>
                  <p>
                    Theo dõi tiến độ, xác nhận kết quả và ghi nhận đánh giá từ
                    khách hàng.
                  </p>
                </div>
              </li>
            </ol>
          </div>
        </section>
        <section id="start" className="page-container">
          <div className="start-panel">
            <div>
              <span className="section-kicker">Bắt đầu từ một kết nối</span>
              <h2>
                Một trải nghiệm hỗ trợ
                <br />
                tốt hơn bắt đầu từ đây.
              </h2>
              <p>Tạo tài khoản khách hàng để bắt đầu với Helpdesk.</p>
            </div>
            <div>
              {user ? (
                <span className="connected-pill">
                  <CheckCircleFilled aria-hidden="true" /> Bạn đã sẵn sàng kết
                  nối
                </span>
              ) : (
                <Link to="/register" className="button-link light">
                  Tạo tài khoản <ArrowRightOutlined aria-hidden="true" />
                </Link>
              )}
              <span className="start-note">Đơn giản từ bước đầu tiên.</span>
            </div>
          </div>
        </section>
      </main>
      <footer className="site-footer page-container">
        <Brand />
        <p>Kết nối yêu cầu. Đồng hành giải quyết.</p>
        <span>© {new Date().getFullYear()} Helpdesk</span>
      </footer>
    </>
  );
}
