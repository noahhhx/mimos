import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { createSession, type SessionManager, type SessionUser } from "../src/lib/session.ts";

/** A UserManager stand-in: one stored user, a scripted renewal, and a log of what was asked. */
function fakeManager(stored: SessionUser | null, renewal: () => Promise<SessionUser | null>) {
  const calls = { renewals: 0, removals: 0 };
  let user = stored;
  const manager: SessionManager<SessionUser> = {
    getUser: async () => user,
    signinSilent: async () => {
      calls.renewals++;
      user = await renewal();
      return user;
    },
    removeUser: async () => {
      calls.removals++;
      user = null;
    },
  };
  return { manager, calls };
}

describe("createSession", () => {
  it("returns a user whose token is still good, without renewing", async () => {
    const { manager, calls } = fakeManager({ access_token: "a", expires_in: 300 }, async () => null);

    assert.equal((await createSession(manager)())?.access_token, "a");
    assert.equal(calls.renewals, 0);
  });

  it("returns null when nobody is signed in", async () => {
    const { manager, calls } = fakeManager(null, async () => null);

    assert.equal(await createSession(manager)(), null);
    assert.equal(calls.renewals, 0);
  });

  it("renews an expired token", async () => {
    const { manager, calls } = fakeManager({ access_token: "old", expires_in: -600 }, async () => ({
      access_token: "new",
      expires_in: 300,
    }));

    assert.equal((await createSession(manager)())?.access_token, "new");
    assert.equal(calls.removals, 0);
  });

  it("renews a token about to expire", async () => {
    const { manager, calls } = fakeManager({ access_token: "old", expires_in: 5 }, async () => ({
      access_token: "new",
      expires_in: 300,
    }));

    assert.equal((await createSession(manager)())?.access_token, "new");
    assert.equal(calls.renewals, 1);
  });

  // The reported bug: a stale session in the browser sent a dead token on every call.
  it("signs out when an expired session cannot be renewed", async () => {
    const { manager, calls } = fakeManager({ access_token: "old", expires_in: -600 }, async () => {
      throw new Error("invalid_grant: Session not active");
    });

    assert.equal(await createSession(manager)(), null);
    assert.equal(calls.removals, 1);
  });

  it("shares one renewal between concurrent callers", async () => {
    let finish: (user: SessionUser) => void = () => {};
    const { manager, calls } = fakeManager(
      { access_token: "old", expires_in: -1 },
      () => new Promise((resolve) => (finish = resolve)),
    );
    const session = createSession(manager);

    const pending = Promise.all([session(), session(), session()]);
    await new Promise((resolve) => setImmediate(resolve));
    finish({ access_token: "new", expires_in: 300 });

    assert.deepEqual(
      (await pending).map((user) => user?.access_token),
      ["new", "new", "new"],
    );
    assert.equal(calls.renewals, 1);
  });
});
