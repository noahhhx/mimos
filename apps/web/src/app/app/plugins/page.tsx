"use client";

import { useEffect, useState } from "react";

import { listMyPlugins, updateMyPlugin, type UserPlugin } from "@mimos/api-client";

import { useAuth } from "@/components/auth-provider";
import { PageHeader } from "@/components/page-header";
import { apiClient } from "@/lib/api";
import { pluginHomepage } from "@/lib/plugins";

type Load<T> = { state: "loading" } | { state: "error" } | { state: "ok"; data: T };

/**
 * Plugins (ADR-0013): what they are, then the instance's plugins, each off
 * until the user turns it on here.
 */
export default function PluginsPage() {
  const { user, signIn } = useAuth();
  const [plugins, setPlugins] = useState<Load<UserPlugin[]>>({ state: "loading" });
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const subject = user?.profile.sub ?? null;
  useEffect(() => {
    if (!subject) {
      return;
    }
    let cancelled = false;
    void listMyPlugins({ client: apiClient }).then((result) => {
      if (!cancelled) {
        setPlugins(result.data ? { state: "ok", data: result.data } : { state: "error" });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [subject]);

  if (!user) {
    return (
      <>
        <PageHeader title="Plugins" />
        <p>You need to sign in to use Mimos.</p>
        <button className="button" onClick={() => void signIn()}>
          Sign in
        </button>
      </>
    );
  }

  const showEnabled = (pluginId: string, enabled: boolean) =>
    setPlugins((current) =>
      current.state === "ok"
        ? { state: "ok", data: current.data.map((each) => (each.id === pluginId ? { ...each, enabled } : each)) }
        : current,
    );

  // The box follows the click at once and goes back if the save fails.
  const setEnabled = async (plugin: UserPlugin, enabled: boolean) => {
    showEnabled(plugin.id, enabled);
    setSaving(plugin.id);
    setError(null);
    const result = await updateMyPlugin({ client: apiClient, path: { pluginId: plugin.id }, body: { enabled } });
    setSaving(null);
    if (result.error) {
      showEnabled(plugin.id, !enabled);
      setError(`Could not turn ${plugin.name} ${enabled ? "on" : "off"}.`);
    }
  };

  return (
    <>
      <PageHeader eyebrow="Account" title="Plugins" />

      <section className="card" aria-labelledby="about-heading">
        <h2 id="about-heading">What plugins are</h2>
        <p>
          Plugins are add-ons that whoever runs this Mimos chose to install, bringing features beyond what Mimos does
          on its own. Country of the Week, for example, picks a cuisine for your week and suggests dishes from it.
        </p>
        <p>Every plugin starts off. Turn on the ones you want below, and turn them off again at any time.</p>
      </section>

      <section className="plugins" aria-labelledby="installed-heading">
        <h2 id="installed-heading">On this Mimos</h2>
        {plugins.state === "loading" && <p className="muted">Loading plugins…</p>}
        {plugins.state === "error" && (
          <p className="card error" role="alert">
            Could not load plugins.
          </p>
        )}
        {plugins.state === "ok" && plugins.data.length === 0 && (
          <p className="muted">No plugins are installed on this Mimos right now.</p>
        )}
        {plugins.state === "ok" && plugins.data.length > 0 && (
          <ul className="plugin-list">
            {plugins.data.map((plugin) => {
              const homepage = pluginHomepage(plugin.homepageUrl);
              return (
                <li key={plugin.id}>
                  <label>
                    <input
                      type="checkbox"
                      checked={plugin.enabled}
                      disabled={saving === plugin.id}
                      onChange={(e) => void setEnabled(plugin, e.target.checked)}
                    />
                    {plugin.name}
                  </label>
                  <span className="muted">{plugin.enabled ? "On" : "Off"}</span>
                  {homepage && (
                    <a href={homepage} target="_blank" rel="noopener noreferrer">
                      About {plugin.name}
                    </a>
                  )}
                </li>
              );
            })}
          </ul>
        )}
        {error && (
          <p className="card error" role="alert">
            {error}
          </p>
        )}
      </section>
    </>
  );
}
