import { useState } from "react";
import {
  AppstoreOutlined,
  BellOutlined,
  CheckOutlined,
  ClockCircleOutlined,
  CommentOutlined,
  InboxOutlined,
  MailOutlined,
  MessageOutlined,
  MoreOutlined,
  RobotOutlined,
  SendOutlined,
  TeamOutlined,
} from "@ant-design/icons";

const examples = [
  {
    channel: "Web form",
    subject: "Mình cần hỗ trợ đơn hàng",
    customer: "Minh Anh",
    initials: "MA",
    message:
      "Chào bạn, mình muốn kiểm tra trạng thái đơn hàng vừa đặt. Bạn hỗ trợ mình nhé!",
    reply:
      "Chào Minh Anh! Mình đã nhận được yêu cầu và sẽ kiểm tra đơn hàng giúp bạn ngay nhé.",
  },
  {
    channel: "Email",
    subject: "Cần hỗ trợ cập nhật hóa đơn",
    customer: "Hoàng Nam",
    initials: "HN",
    message:
      "Chào đội ngũ hỗ trợ, mình muốn cập nhật thông tin trên hóa đơn của đơn hàng gần nhất.",
    reply:
      "Chào Nam! Bạn gửi giúp mình thông tin cần cập nhật để mình kiểm tra nhé.",
  },
  {
    channel: "Live chat",
    subject: "Mình cần đổi địa chỉ giao hàng",
    customer: "Thu Hà",
    initials: "TH",
    message:
      "Mình vừa đặt hàng nhưng nhập nhầm địa chỉ. Mình có thể cập nhật lại được không?",
    reply:
      "Chào Hà! Mình đang kiểm tra trạng thái giao hàng để hỗ trợ bạn đổi địa chỉ nhé.",
  },
];

export function InboxPreview() {
  const [active, setActive] = useState(0);
  const current = examples[active];
  return (
    <div
      className="preview-scene"
      aria-label="Bản xem trước giao diện Helpdesk"
    >
      <div
        className="preview-channel-tabs"
        role="tablist"
        aria-label="Kênh tiếp nhận minh họa"
      >
        {examples.map((item, index) => (
          <button
            key={item.channel}
            id={`channel-${index}`}
            role="tab"
            aria-label={item.channel}
            tabIndex={active === index ? 0 : -1}
            aria-selected={active === index}
            aria-controls="inbox-example"
            className={active === index ? "active" : ""}
            onClick={() => setActive(index)}
            onKeyDown={(event) => {
              const next =
                event.key === "ArrowRight"
                  ? (active + 1) % examples.length
                  : event.key === "ArrowLeft"
                    ? (active + examples.length - 1) % examples.length
                    : event.key === "Home"
                      ? 0
                      : event.key === "End"
                        ? examples.length - 1
                        : null;
              if (next === null) return;
              event.preventDefault();
              setActive(next);
              document.getElementById(`channel-${next}`)?.focus();
            }}
          >
            {index === 0 ? (
              <AppstoreOutlined aria-hidden="true" />
            ) : index === 1 ? (
              <MailOutlined aria-hidden="true" />
            ) : (
              <MessageOutlined aria-hidden="true" />
            )}
            {item.channel}
          </button>
        ))}
      </div>
      <div
        className="inbox-preview"
        id="inbox-example"
        role="tabpanel"
        aria-labelledby={`channel-${active}`}
      >
        <aside className="preview-sidebar" aria-hidden="true">
          <span className="preview-logo">h.</span>
          <InboxOutlined className="selected" />
          <AppstoreOutlined aria-hidden="true" />
          <TeamOutlined aria-hidden="true" />
          <RobotOutlined aria-hidden="true" />
          <span className="preview-sidebar-avatar">A</span>
        </aside>
        <div className="preview-app">
          <div className="preview-topbar">
            <span>Không gian làm việc</span>
            <div>
              <span className="online-dot" /> Sẵn sàng{" "}
              <BellOutlined aria-hidden="true" />
            </div>
          </div>
          <div className="preview-columns">
            <div className="preview-inbox-list">
              <div className="preview-section-title">
                Hộp thư <span>3</span>
              </div>
              <span className="preview-filter">
                Tất cả yêu cầu <span>⌄</span>
              </span>
              {examples.map((item, index) => (
                <div
                  key={item.customer}
                  className={`preview-ticket ${active === index ? "selected" : ""}`}
                >
                  <div className="flex justify-between">
                    <strong>{item.customer}</strong>
                    <span>{index + 2} phút</span>
                  </div>
                  <p>{item.subject}</p>
                  <span
                    className={`ticket-label ${index === 0 ? "label-blue" : index === 1 ? "label-orange" : ""}`}
                  >
                    {index === 0 ? "Đang xử lý" : "Mới"}
                  </span>
                </div>
              ))}
            </div>
            <div className="preview-thread">
              <div className="thread-header">
                <span>
                  #{1024 + active}
                  <strong role="heading" aria-level={3}>
                    {current.subject}
                  </strong>
                </span>
                <MoreOutlined aria-hidden="true" />
              </div>
              <div className="thread-conversation">
                <span className="thread-date">Hôm nay</span>
                <div className="message-author">
                  <span className="mini-avatar">{current.initials}</span>
                  <strong>{current.customer}</strong>
                  <span>10:24</span>
                </div>
                <p className="chat-bubble customer-bubble">{current.message}</p>
                <div className="message-author agent-author">
                  <strong>An · Hỗ trợ viên</strong>
                  <span className="mini-avatar agent-avatar">A</span>
                </div>
                <p className="chat-bubble agent-bubble">{current.reply}</p>
                <div className="ai-note">
                  <RobotOutlined aria-hidden="true" />
                  <span>AI gợi ý câu trả lời từ kho kiến thức</span>
                  <CheckOutlined aria-hidden="true" />
                </div>
              </div>
              <div className="preview-composer">
                <span>
                  <CommentOutlined aria-hidden="true" /> Phản hồi công khai
                </span>
                <p>Nhập câu trả lời của bạn...</p>
                <div>
                  <span>＋ &nbsp; ☺</span>
                  <span className="preview-send">
                    <SendOutlined aria-hidden="true" />
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div className="preview-sla">
        <span className="sla-icon">
          <ClockCircleOutlined aria-hidden="true" />
        </span>
        <div>
          <strong>Đúng người. Đúng thời điểm.</strong>
          <span>Theo dõi SLA trong từng yêu cầu</span>
        </div>
        <span className="sla-check">
          <CheckOutlined aria-hidden="true" />
        </span>
      </div>
      <p className="preview-caption">
        Giao diện minh họa · Chọn một kênh để khám phá
      </p>
    </div>
  );
}
