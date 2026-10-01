import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import { UsageError } from "../src/args.ts";
import { REPO_ROOT } from "../src/config.ts";
import {
  endsWithPrompt,
  evaluationDone,
  hitThreadId,
  jdbLocation,
  parseEvaluation,
  parseHits,
  parseLocals,
  parseLocation,
  parseStopResult,
  parseThreads,
  parseWhere,
  resolveClassName,
  sourceRoots,
  stopCommand,
} from "../src/jdb.ts";

// Transcript excerpts recorded from `harness debug break` against the running API (JDK 21 jdb).

const ME = "io.github.noahhhx.mimos.api.identity.MeController";

const START = `Set uncaught java.lang.Throwable
Set deferred uncaught java.lang.Throwable
Initializing jdb ...
> `;

const THREE_HITS = `> Set breakpoint ${ME}:27
>
Breakpoint hit: "thread=http-nio-8080-exec-7", ${ME}.getMe(), line=27 bci=8
27            return ResponseEntity.ok(new UserProfile()
>
Breakpoint hit: "thread=http-nio-8080-exec-3", ${ME}.getMe(), line=27 bci=8
27            return ResponseEntity.ok(new UserProfile()
>
Breakpoint hit: "thread=http-nio-8080-exec-4", ${ME}.getMe(), line=27 bci=8
27            return ResponseEntity.ok(new UserProfile()
> `;

const THREADS = `Group system:
  (java.lang.ref.Reference$ReferenceHandler)12378             Reference Handler               running
  (java.lang.Thread)12380                                     Signal Dispatcher               running
Group main:
  (org.apache.tomcat.util.threads.TaskThread)12384            Catalina-utility-1              cond. waiting
  (org.springframework.boot.tomcat.TomcatWebServer$1)12386    container-0                     sleeping
  (org.apache.tomcat.util.threads.TaskThread)12377            http-nio-8080-exec-7            running (at breakpoint)
  (org.apache.tomcat.util.threads.TaskThread)12390            http-nio-8080-exec-8            cond. waiting
Group InnocuousThreadGroup:
  (jdk.internal.misc.InnocuousThread)12382                    Common-Cleaner                  cond. waiting
> `;

const WHERE = `http-nio-8080-exec-7[1]   [1] ${ME}.getMe (MeController.java:27)
  [2] java.lang.invoke.DirectMethodHandle$Holder.invokeVirtual (null)
  [3] java.lang.invoke.LambdaForm$MH/0x00007af33c0ac400.invoke (null)
  [16] org.springframework.web.servlet.FrameworkServlet.processRequest (FrameworkServlet.java:1,000)
  [138] org.apache.coyote.AbstractProtocol$ConnectionHandler.process (AbstractProtocol.java:1,307)
http-nio-8080-exec-7[1] `;

test("locations: Class:line, Class.method, signatures, nested classes", () => {
  assert.deepEqual(parseLocation("MeController:27"), { kind: "line", className: "MeController", line: 27 });
  assert.deepEqual(parseLocation("MeController.getMe"), {
    kind: "method",
    className: "MeController",
    method: "getMe",
    signature: undefined,
  });
  const overloaded = parseLocation("a.b.Outer$Inner.run(int, java.lang.String)");
  assert.deepEqual(overloaded, { kind: "method", className: "a.b.Outer$Inner", method: "run", signature: "(int, java.lang.String)" });
  assert.equal(jdbLocation(overloaded), "a.b.Outer$Inner.run(int, java.lang.String)");
  assert.equal(parseLocation("Foo.<init>").kind, "method");
  for (const bad of ["MeController", "MeController:0", "MeController:x", "Me Controller.get", ":27", "a..b.c"]) {
    assert.throws(() => parseLocation(bad), UsageError, bad);
  }
});

test("breakpoints suspend only the hitting thread", () => {
  assert.equal(stopCommand({ kind: "line", className: ME, line: 27 }), `stop thread at ${ME}:27`);
  assert.equal(
    stopCommand({ kind: "method", className: ME, method: "getMe", signature: undefined }),
    `stop thread in ${ME}.getMe`,
  );
});

test("simple class names resolve from the source roots; nested, qualified, ambiguous, unknown", () => {
  const root = mkdtempSync(join(tmpdir(), "harness-jdb-"));
  try {
    const a = join(root, "a/src/main/java");
    const b = join(root, "b/src/main/java");
    mkdirSync(join(a, "x/y"), { recursive: true });
    mkdirSync(join(b, "z"), { recursive: true });
    writeFileSync(join(a, "x/y/Only.java"), "");
    writeFileSync(join(a, "x/y/Twice.java"), "");
    writeFileSync(join(b, "z/Twice.java"), "");
    assert.equal(resolveClassName("Only", [a, b]), "x.y.Only");
    assert.equal(resolveClassName("Only$Inner", [a, b]), "x.y.Only$Inner");
    assert.equal(resolveClassName("org.lib.Thing", [a, b]), "org.lib.Thing", "qualified names are taken as given");
    assert.throws(() => resolveClassName("Twice", [a, b]), /ambiguous.*x\.y\.Twice, z\.Twice/);
    assert.throws(() => resolveClassName("Missing", [a, b]), /no class Missing/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("the repo's source roots find the API's classes", () => {
  const roots = sourceRoots(REPO_ROOT);
  assert.ok(roots.some((root) => root.endsWith("apps/api/src/main/java")));
  assert.ok(roots.some((root) => root.endsWith("core/core-recipes/src/main/java")));
  assert.equal(resolveClassName("MeController", roots), ME);
});

test("prompts: none, a thread's, and output that is not one", () => {
  assert.ok(endsWithPrompt(START));
  assert.ok(endsWithPrompt(WHERE));
  assert.ok(endsWithPrompt("Signal Dispatcher[1] "));
  assert.ok(!endsWithPrompt("Initializing jdb ..."));
  assert.ok(!endsWithPrompt(`Set breakpoint ${ME}:27\n`));
  assert.ok(!endsWithPrompt("a -> "));
});

test("stop: set, deferred, refused", () => {
  assert.deepEqual(parseStopResult(`> Set breakpoint ${ME}:27\n> `), { kind: "set" });
  assert.deepEqual(
    parseStopResult("> Deferring breakpoint org.example.Nope:3.\nIt will be set after the class is loaded.\n> "),
    { kind: "deferred" },
  );
  assert.deepEqual(parseStopResult(`> Unable to set breakpoint ${ME}.me : No method me in ${ME}\n> `), {
    kind: "failed",
    reason: `No method me in ${ME}`,
  });
  assert.deepEqual(parseStopResult(`Unable to set breakpoint ${ME}:2 : No code at line 2 in ${ME}\n> `), {
    kind: "failed",
    reason: `No code at line 2 in ${ME}`,
  });
  assert.equal(parseStopResult(""), undefined, "no answer yet");
});

test("hit events, with the source line when jdb found it", () => {
  const hits = parseHits(THREE_HITS);
  assert.equal(hits.length, 3);
  assert.deepEqual(hits[0], {
    thread: "http-nio-8080-exec-7",
    method: `${ME}.getMe()`,
    line: 27,
    bci: 8,
    source: "27            return ResponseEntity.ok(new UserProfile()",
  });
  assert.deepEqual(hits.map((hit) => hit.thread), ["http-nio-8080-exec-7", "http-nio-8080-exec-3", "http-nio-8080-exec-4"]);
  const noSource = parseHits('Breakpoint hit: "thread=main", org.lib.Thing.run(), line=1,204 bci=0\n> ');
  assert.deepEqual(noSource, [{ thread: "main", method: "org.lib.Thing.run()", line: 1204, bci: 0, source: undefined }]);
});

test("threads: IDs by name, names with spaces, the one at the breakpoint", () => {
  const threads = parseThreads(THREADS);
  assert.equal(threads.length, 7);
  assert.deepEqual(threads[0], { id: "12378", name: "Reference Handler", status: "running" });
  assert.deepEqual(threads[3], { id: "12386", name: "container-0", status: "sleeping" });
  assert.equal(hitThreadId(threads, "http-nio-8080-exec-7"), "12377");
  assert.equal(hitThreadId(threads, "Signal Dispatcher"), "12380");
  assert.equal(hitThreadId(threads, "http-nio-8080-exec-1"), undefined);
});

test("where: frames, missing line info, grouped digits", () => {
  const frames = parseWhere(WHERE);
  assert.equal(frames.length, 5);
  assert.deepEqual(frames[0], { index: 1, method: `${ME}.getMe`, location: "MeController.java:27" });
  assert.deepEqual(frames[1], { index: 2, method: "java.lang.invoke.DirectMethodHandle$Holder.invokeVirtual", location: null });
  assert.equal(frames[3]?.location, "FrameworkServlet.java:1000");
  assert.equal(frames[4]?.location, "AbstractProtocol.java:1307");
});

test("locals: arguments and locals, empty sections, unavailable", () => {
  assert.deepEqual(
    parseLocals(`http-nio-8080-exec-7[1] Method arguments:
Local variables:
profile = instance of io.github.noahhhx.mimos.api.identity.UserProfileRecord(id=12404)
http-nio-8080-exec-7[1] `),
    {
      arguments: [],
      locals: [{ name: "profile", value: "instance of io.github.noahhhx.mimos.api.identity.UserProfileRecord(id=12404)" }],
      unavailable: undefined,
    },
  );
  assert.deepEqual(
    parseLocals(`exec-5[1] Method arguments:
proxy = instance of org.springframework.core.$Proxy6(id=12402)
args = null
Local variables:
exec-5[1] `),
    {
      arguments: [
        { name: "proxy", value: "instance of org.springframework.core.$Proxy6(id=12402)" },
        { name: "args", value: "null" },
      ],
      locals: [],
      unavailable: undefined,
    },
  );
  assert.equal(
    parseLocals("main[1] Local variable information not available.  Compile with -g to generate variable information\nmain[1] ")
      .unavailable,
    "Local variable information not available.  Compile with -g to generate variable information",
  );
});

test("print and dump: values, multi-line objects, failures", () => {
  const printed = `http-nio-8080-exec-2[1]  profile.subjectId() = "b5da2810-2e6e-4a8d-8c04-be8f19a0f5d9"\nhttp-nio-8080-exec-2[1] `;
  assert.ok(evaluationDone(printed, "profile.subjectId()"));
  assert.deepEqual(parseEvaluation(printed, "profile.subjectId()"), { ok: true, value: '"b5da2810-2e6e-4a8d-8c04-be8f19a0f5d9"' });

  const dumped = `exec-7[1]  this = {
    currentUser: instance of io.github.noahhhx.mimos.api.identity.CurrentUserService(id=12406)
}
exec-7[1] `;
  assert.ok(evaluationDone(dumped, "this"));
  assert.ok(!evaluationDone("exec-7[1]  this = {\n    currentUser: x", "this"), "a dump is done at its closing brace");
  assert.deepEqual(parseEvaluation(dumped, "this"), {
    ok: true,
    value: "{\n    currentUser: instance of io.github.noahhhx.mimos.api.identity.CurrentUserService(id=12406)\n}",
  });

  const failed = `http-nio-8080-exec-3[1] com.sun.tools.example.debug.expr.ParseException: Name unknown: profile
 profile = null
http-nio-8080-exec-3[1] `;
  assert.ok(evaluationDone(failed, "profile"));
  assert.deepEqual(parseEvaluation(failed, "profile"), { ok: false, error: "ParseException: Name unknown: profile" });
  assert.deepEqual(parseEvaluation("> No current thread\n> ", "x"), { ok: false, error: "No current thread" });
  assert.ok(!evaluationDone("", "x"));
});
