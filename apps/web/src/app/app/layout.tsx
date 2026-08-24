import { AuthProvider } from "@/components/auth-provider";

/** The authenticated app shell: everything under /app requires a login. */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>;
}
