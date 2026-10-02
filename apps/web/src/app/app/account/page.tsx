"use client";

import { useState, type FormEvent } from "react";

import { exportAccount, importAccount, type ImportReport } from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { exportFileName, importSummary, parseExportFile } from "@/lib/account-data";
import { apiClient } from "@/lib/api";

/**
 * Your data, portable (ADR-0011): download everything as one file, and
 * restore such a file into an empty account, here or on another Mimos.
 */
export default function AccountPage() {
  const { user, signIn } = useAuth();
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);

  if (!user) {
    return (
      <>
        <PageHeader title="Your data" />
        <p>You need to sign in to use Mimos.</p>
        <button className="button" onClick={() => void signIn()}>
          Sign in
        </button>
      </>
    );
  }

  const download = async () => {
    setExporting(true);
    const result = await exportAccount({ client: apiClient });
    setExporting(false);
    if (result.error || !result.data) {
      setExportError("Could not export your data.");
      return;
    }
    setExportError(null);
    const blob = new Blob([JSON.stringify(result.data, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = exportFileName(result.data.exportedAt);
    link.click();
    URL.revokeObjectURL(url);
  };

  const restore = async (event: FormEvent) => {
    event.preventDefault();
    if (!file) {
      return;
    }
    setReport(null);
    const parsed = parseExportFile(await file.text());
    if (!parsed.ok) {
      setImportError(parsed.error);
      return;
    }
    setImporting(true);
    const result = await importAccount({ client: apiClient, body: parsed.document });
    setImporting(false);
    if (result.error || !result.data) {
      const problem = result.error as { detail?: string } | undefined;
      setImportError(problem?.detail ?? "Could not import the file.");
      return;
    }
    setImportError(null);
    setReport(result.data);
  };

  return (
    <>
      <PageHeader eyebrow="Account" title="Your data" />

      <section className="card" aria-labelledby="export-heading">
        <h2 id="export-heading">Export</h2>
        <p>
          Download everything you have added to Mimos as one file: your recipes, meal plans, shopping lists and
          food log. Keep it somewhere safe. You can import it into a fresh account, here or on another Mimos.
        </p>
        {exportError && (
          <p className="card error" role="alert">
            {exportError}
          </p>
        )}
        <button className="button" onClick={() => void download()} disabled={exporting}>
          {exporting ? "Exporting…" : "Download export"}
        </button>
      </section>

      <section className="card" aria-labelledby="import-heading">
        <h2 id="import-heading">Import</h2>
        <p>
          Restore an export into this account. Import only works into an empty account, one with no recipes, plans,
          shopping lists or logged meals yet, so nothing here is ever overwritten or doubled up.
        </p>
        <form onSubmit={(event) => void restore(event)}>
          <label>
            Export file
            <input
              type="file"
              accept=".json,application/json"
              onChange={(event) => {
                setFile(event.target.files?.[0] ?? null);
                setImportError(null);
                setReport(null);
              }}
            />
          </label>
          {importError && (
            <p className="card error" role="alert">
              {importError}
            </p>
          )}
          {report && (
            <div className="card ok" role="status">
              <p>{importSummary(report)}</p>
              {report.warnings.length > 0 && (
                <ul>
                  {report.warnings.map((warning) => (
                    <li key={warning}>{warning}</li>
                  ))}
                </ul>
              )}
            </div>
          )}
          <p>
            <button className="button" type="submit" disabled={!file || importing}>
              {importing ? "Importing…" : "Import"}
            </button>
          </p>
        </form>
      </section>
    </>
  );
}
