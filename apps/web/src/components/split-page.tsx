/**
 * The two-column page of the Evening Kitchen design (docs/design/index.md):
 * a rail with the page's identity and context, and a main column with the
 * work. Below 880px the rail stacks above the main column.
 */
export function SplitPage({
  rail,
  children,
  className,
}: {
  rail: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={className ? `split ${className}` : "split"}>
      <div className="rail">{rail}</div>
      <div className="split-main">{children}</div>
    </div>
  );
}
