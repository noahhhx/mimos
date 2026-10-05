"use client";

import { Fragment, useId, useState } from "react";

import { pressWeekPanelAction, type PanelBlock, type PanelButton, type WeekPanel } from "@mimos/api-client";

import { Wheel } from "@/components/wheel";
import { apiClient } from "@/lib/api";
import { hasLanding, problemDetail, shownBlocks } from "@/lib/panels";

/**
 * One plugin's panel for a week (ADR-0017), collapsed to a single line
 * until the user opens it: the plugin's name and, when the plugin gives
 * one, its summary (a chosen country's flag and name). Open, it shows the
 * plugin's blocks as plain text; a button sends its action and the panel
 * is replaced by the plugin's answer, passed up through `onChange`.
 */
export function WeekPanelSection({
  startDate,
  panel,
  onChange,
}: {
  startDate: string;
  panel: WeekPanel;
  onChange: (panel: WeekPanel) => void;
}) {
  const [open, setOpen] = useState(false);
  const [pressing, setPressing] = useState(false);
  const [spinning, setSpinning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bodyId = useId();

  const toggle = () => {
    // Closing mid-spin ends it, so reopening shows where it landed.
    setSpinning(false);
    setOpen(!open);
  };

  const press = async (button: PanelButton) => {
    setPressing(true);
    setError(null);
    const result = await pressWeekPanelAction({
      client: apiClient,
      path: { startDate, pluginId: panel.pluginId },
      body: { id: button.id, value: button.value },
    });
    setPressing(false);
    if (!result.data) {
      setError(problemDetail(result.error, `${panel.pluginName} could not do that. Try again.`));
      return;
    }
    setSpinning(hasLanding(result.data.blocks));
    onChange(result.data);
  };

  const renderBlock = (block: PanelBlock) => {
    switch (block.type) {
      case "text":
        return <p>{block.text}</p>;
      case "highlight":
        return (
          <div className="panel-highlight">
            {block.icon && (
              <span className="panel-highlight-icon" aria-hidden="true">
                {block.icon}
              </span>
            )}
            <div>
              <h3>{block.title}</h3>
              {block.text && <p className="muted">{block.text}</p>}
            </div>
          </div>
        );
      case "wheel":
        return (
          <Wheel
            segments={block.segments}
            landing={block.landing}
            spin={spinning}
            onStop={() => setSpinning(false)}
          />
        );
      case "actions":
        return (
          <div className="panel-actions">
            {block.actions.map((button, index) => (
              <button
                key={index}
                className={button.primary ? "button" : "button secondary"}
                disabled={pressing || spinning}
                onClick={() => void press(button)}
              >
                {button.label}
              </button>
            ))}
          </div>
        );
      default:
        // A block type this web app does not know yet (ADR-0017): skipped.
        return null;
    }
  };

  return (
    <section className="week-panel">
      <h2>
        <button className="week-panel-toggle" aria-expanded={open} aria-controls={bodyId} onClick={toggle}>
          <span className="week-panel-name">{panel.pluginName}</span>
          {panel.summary && (
            <span className="week-panel-summary">
              {panel.summary.icon && <span aria-hidden="true">{panel.summary.icon} </span>}
              {panel.summary.label}
            </span>
          )}
          <span className="week-panel-sign" aria-hidden="true">
            {open ? "−" : "+"}
          </span>
        </button>
      </h2>
      {open && (
        <div id={bodyId} className="week-panel-body">
          {shownBlocks(panel.blocks, spinning).map(({ key, block }) => (
            <Fragment key={key}>{renderBlock(block)}</Fragment>
          ))}
          {error && (
            <p className="card error" role="alert">
              {error}
            </p>
          )}
        </div>
      )}
    </section>
  );
}
