import { test, expect } from "@playwright/test";

test("an old login response cannot replace a newer registration session after navigation", async ({
  page,
}) => {
  const authResponse = (name: string, id: number) => ({
    accessToken: `mock-${name}`,
    refreshToken: `mock-refresh-${name}`,
    tokenType: "Bearer",
    expiresInSeconds: 3600,
    user: {
      id,
      fullName: name,
      email: `${name}@example.com`,
      roles: ["CUSTOMER"],
      teamIds: [],
      isActive: true,
      isAvailable: false,
    },
  });
  let releaseFirst!: () => void;
  const firstResponse = new Promise<void>((resolve) => {
    releaseFirst = resolve;
  });
  let loginStarted = false;
  let loginCompleted = false;
  await page.route("**/api/v1/auth/login", async (route) => {
    loginStarted = true;
    await firstResponse;
    try {
      await route.fulfill({ json: authResponse("First", 1) });
    } catch {
      /* Navigation may cancel the old request. */
    } finally {
      loginCompleted = true;
    }
  });
  await page.route("**/api/v1/auth/register", (route) =>
    route.fulfill({ json: authResponse("Second", 2) }),
  );
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill("First@example.com");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect.poll(() => loginStarted).toBe(true);
  await page.getByRole("link", { name: "Đăng ký", exact: true }).click();
  await page.getByLabel("Họ và tên", { exact: true }).fill("Second");
  await page.getByLabel("Email", { exact: true }).fill("Second@example.com");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  await page
    .getByLabel("Nhập lại mật khẩu", { exact: true })
    .fill("Password123!");
  await page
    .getByRole("button", { name: "Tạo tài khoản", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Xin chào, Second" }),
  ).toBeVisible();
  releaseFirst();
  await expect.poll(() => loginCompleted).toBe(true);
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          JSON.parse(sessionStorage.getItem("helpdesk.session")!).accessToken,
      ),
    )
    .toBe("mock-Second");
  await expect(
    page.getByRole("heading", { name: "Xin chào, Second" }),
  ).toBeVisible();
});

test("browser offline fails promptly instead of queuing authentication indefinitely", async ({
  page,
  context,
}) => {
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill("offline@example.com");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  await context.setOffline(true);
  try {
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await expect(page.getByRole("alert")).toContainText("Không thể kết nối");
    await expect(page.getByLabel("Email", { exact: true })).toBeEnabled();
    await expect(
      page.getByRole("button", { name: "Đăng nhập", exact: true }),
    ).toBeEnabled();
  } finally {
    await context.setOffline(false);
  }
});

test("home navigation, preview channels, and responsive entry pages", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Mọi yêu cầu. Một nơi kết nối." }),
  ).toBeVisible();
  await page.getByRole("tab", { name: "Email", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Cần hỗ trợ cập nhật hóa đơn" }),
  ).toBeVisible();
  await page.getByRole("tab", { name: "Live chat", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Mình cần đổi địa chỉ giao hàng" }),
  ).toBeVisible();
  for (const path of ["/", "/login", "/register"]) {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(path);
    await expect(page.locator("h1")).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  }
  await page.goto("/login");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByText("Vui lòng nhập email hợp lệ.")).toBeVisible();
  await expect(page.getByText("Vui lòng nhập mật khẩu.")).toBeVisible();
});

test("registration validation, backend unavailable, and retry preserve inputs", async ({
  page,
}) => {
  await page.goto("/register");
  await page.getByLabel("Họ và tên", { exact: true }).fill("Nguyễn Minh An");
  await page.getByLabel("Email", { exact: true }).fill("an@example.com");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  await page
    .getByLabel("Nhập lại mật khẩu", { exact: true })
    .fill("Mismatch123!");
  await page
    .getByRole("button", { name: "Tạo tài khoản", exact: true })
    .click();
  await expect(page.getByText("Mật khẩu nhập lại chưa khớp.")).toBeVisible();
  await page
    .getByLabel("Nhập lại mật khẩu", { exact: true })
    .fill("Password123!");
  await page.route("**/api/v1/auth/register", (route) => route.abort("failed"));
  await page
    .getByRole("button", { name: "Tạo tài khoản", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText("Không thể kết nối");
  await expect(page.getByLabel("Email", { exact: true })).toHaveValue(
    "an@example.com",
  );
  await expect(
    page.getByRole("button", { name: "Tạo tài khoản", exact: true }),
  ).toBeEnabled();
});

test("real registration, restored session, logout, duplicate registration and login", async ({
  page,
}) => {
  const email = `fe-check-${Date.now()}@example.com`;
  await page.goto("/register");
  await page
    .getByLabel("Họ và tên", { exact: true })
    .fill("Kiểm thử giao diện");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  await page
    .getByLabel("Nhập lại mật khẩu", { exact: true })
    .fill("Password123!");
  await page
    .getByRole("button", { name: "Tạo tài khoản", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Xin chào, Kiểm thử giao diện" }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Xin chào, Kiểm thử giao diện" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Đăng xuất", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "Đăng nhập", exact: true }),
  ).toBeVisible();
  await page.goto("/register");
  await page
    .getByLabel("Họ và tên", { exact: true })
    .fill("Kiểm thử giao diện");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  await page
    .getByLabel("Nhập lại mật khẩu", { exact: true })
    .fill("Password123!");
  await page
    .getByRole("button", { name: "Tạo tài khoản", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    /đã.*(tồn tại|sử dụng|đăng ký)/i,
  );
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Mật khẩu", { exact: true }).fill("wrongpassword");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Email hoặc mật khẩu không đúng",
  );
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Xin chào, Kiểm thử giao diện" }),
  ).toBeVisible();
});
