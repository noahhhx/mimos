/**
 * A page's header in the Evening Kitchen design: an optional eyebrow, the
 * serif h1, and the short amber rule, with the page's actions (buttons,
 * week navigation) beside the title on wide screens and under it on narrow
 * ones.
 */
export function PageHeader({
  eyebrow,
  title,
  actions,
}: {
  eyebrow?: React.ReactNode;
  title: React.ReactNode;
  actions?: React.ReactNode;
}) {
  return (
    <header className={actions ? "page-header has-actions" : "page-header"}>
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
      </div>
      {actions && <div className="actions">{actions}</div>}
    </header>
  );
}
