import { AppNav } from "@/components/app-nav";

/** The authenticated app shell's navigation; the auth context comes from the root layout. */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <AppNav />
      {children}
    </>
  );
}
