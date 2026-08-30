import { AppNav } from "@/components/app-nav";
import { AuthProvider } from "@/components/auth-provider";

/** The authenticated app shell: navigation plus the OIDC auth context. */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <AppNav />
      {children}
    </AuthProvider>
  );
}
