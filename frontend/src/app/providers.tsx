import type { ReactNode } from "react";
import { ConfigProvider, App as AntApp } from "antd";
import viVN from "antd/locale/vi_VN";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../features/auth/hooks/useAuth";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, networkMode: "always" },
    mutations: { retry: false, networkMode: "always" },
  },
});

export function Providers({ children }: { children: ReactNode }) {
  return (
    <ConfigProvider
      locale={viVN}
      theme={{
        cssVar: {},
        hashed: true,
        token: {
          colorPrimary: "#115e59",
          colorText: "#182c30",
          colorTextSecondary: "#637579",
          colorBorder: "#dce4e1",
          borderRadius: 8,
          fontFamily: '"Be Vietnam Pro", sans-serif',
          fontSize: 14,
          controlHeightLG: 50,
        },
        components: {
          Button: { primaryShadow: "none", fontWeight: 500 },
          Input: { paddingInlineLG: 15 },
          Alert: { borderRadiusLG: 8 },
        },
      }}
    >
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>{children}</AuthProvider>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  );
}
