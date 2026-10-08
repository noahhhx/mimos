# 00. Clean up the first run

**Step:** none · **Depends on:** nothing

The first run left fourteen worktrees under `.claude/worktrees/` and three
agent branches. Only the three part branches carry work.

## Do

- Keep the branches `ft1-planned-totals`, `ft2-fuel-profile`, and
  `ft3-fuel-targets`, and the worktree `.claude/worktrees/ft1`, which holds
  item 01's uncommitted edits. Keep `ft2` and `ft3` too if they are clean
  (`git -C <path> status --short` prints nothing).
- Look at each other worktree before removing it. `lane-*` and `v-*` are
  detached checkouts for verification and hold nothing to keep; the
  `agent-*` worktrees and their `worktree-agent-*` branches came from
  subagents. Remove a worktree with `git worktree remove <path>` only when
  `git status --short` in it is empty, and say what you removed.
- Stop any compose stack the run left up: `devenv shell -- harness down`.

## Done when

`git worktree list` shows `main` plus the part worktrees you kept, and
`docker ps` shows no stray Mimos containers.
