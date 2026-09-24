#!/usr/bin/env python3
"""Turn docs/BACKLOG.md into GitHub Issues.

Dry-run by default: prints what it would create. Pass --apply to create issues
with `gh issue create`, and --project N to also add each issue to a GitHub
Project (v2). Idempotent: an issue whose title already exists (open or closed)
is skipped, so the script can be re-run after editing the backlog.

Format contract (see the top of docs/BACKLOG.md):
  ### TD-23: Title
  **Epic** E2 · **Points** 5 · **Priority** P1 · **Sprint** 2 · **Area** ticket [· **Type** task]
  ...body until the next ### or ## heading...
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

REPO_DEFAULT = "gavinfecko/triagedesk-api"
EPIC_LABELS = {
    "E0": "epic:E0-foundation",
    "E1": "epic:E1-identity",
    "E2": "epic:E2-ticket-lifecycle",
    "E3": "epic:E3-collaboration",
    "E4": "epic:E4-sla",
    "E5": "epic:E5-notifications",
    "E6": "epic:E6-reporting",
    "E7": "epic:E7-delivery",
    "E8": "epic:E8-web",
}
HEADER = re.compile(r"^### (TD-\d+): (.+?)\s*$")
META = re.compile(r"\*\*(Epic|Points|Priority|Sprint|Area|Type)\*\*\s+([^·\n]+)")


@dataclass
class Story:
    key: str
    title: str
    epic: str
    points: str
    priority: str
    sprint: str
    area: str
    type: str = "story"
    body: str = ""
    labels: list[str] = field(default_factory=list)

    @property
    def issue_title(self) -> str:
        return f"{self.key} {self.title}"

    @property
    def milestone(self) -> str | None:
        return None if self.sprint.lower() == "icebox" else f"Sprint {self.sprint}"


def parse(path: Path) -> list[Story]:
    lines = path.read_text(encoding="utf-8").splitlines()
    stories: list[Story] = []
    i = 0
    while i < len(lines):
        m = HEADER.match(lines[i])
        if not m:
            i += 1
            continue
        key, title = m.group(1), m.group(2)
        meta_line = lines[i + 1] if i + 1 < len(lines) else ""
        meta = {k.lower(): v.strip() for k, v in META.findall(meta_line)}
        missing = [k for k in ("epic", "points", "priority", "sprint", "area") if k not in meta]
        if missing:
            sys.exit(f"{key}: metadata line missing {missing}: {meta_line!r}")
        body: list[str] = []
        j = i + 2
        while j < len(lines) and not lines[j].startswith("### ") and not lines[j].startswith("## "):
            body.append(lines[j])
            j += 1
        s = Story(
            key=key,
            title=title,
            epic=meta["epic"],
            points=meta["points"],
            priority=meta["priority"],
            sprint=meta["sprint"],
            area=meta["area"],
            type=meta.get("type", "story"),
            body="\n".join(body).strip(),
        )
        s.labels = [
            f"type:{s.type}",
            EPIC_LABELS.get(s.epic, f"epic:{s.epic}"),
            f"priority:{s.priority}",
            f"area:{s.area}",
        ]
        stories.append(s)
        i = j
    return stories


def gh(*args: str, capture: bool = True) -> str:
    res = subprocess.run(["gh", *args], text=True, capture_output=capture)
    if res.returncode != 0:
        raise SystemExit(f"gh {' '.join(args)} failed:\n{res.stderr}")
    return res.stdout if capture else ""


def existing_issues(repo: str) -> dict[str, str]:
    """title -> url for every open or closed issue."""
    out = gh("issue", "list", "--repo", repo, "--state", "all", "--limit", "1000", "--json", "title,url")
    return {it["title"]: it["url"] for it in json.loads(out)}


def issue_body(s: Story, source: str) -> str:
    footer = (
        f"\n\n---\n**Points** {s.points} · **Target sprint** {s.sprint} · **Epic** {s.epic}\n"
        f"_Source: `{source}` — edit the backlog, then re-run `scripts/backlog_to_issues.py`._"
    )
    return s.body + footer


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", default=REPO_DEFAULT)
    ap.add_argument("--backlog", default=str(Path(__file__).resolve().parents[1] / "docs" / "BACKLOG.md"))
    ap.add_argument("--apply", action="store_true", help="create issues (default: dry-run)")
    ap.add_argument("--project", type=int, help="GitHub Project (v2) number to add issues to")
    ap.add_argument("--owner", default="gavinfecko", help="project owner (user or org)")
    ap.add_argument("--only", help="comma-separated keys to limit to, e.g. TD-1,TD-2")
    args = ap.parse_args()

    stories = parse(Path(args.backlog))
    if args.only:
        wanted = {k.strip() for k in args.only.split(",")}
        stories = [s for s in stories if s.key in wanted]
    total_points = sum(int(s.points) for s in stories if s.points.isdigit())
    print(f"{len(stories)} stories, {total_points} points, from {args.backlog}")

    seen = existing_issues(args.repo) if args.apply else {}
    created = skipped = 0
    for s in stories:
        ms = s.milestone or "(no milestone)"
        line = f"  {s.key:<7} {s.points:>2} pts  {ms:<15} {', '.join(s.labels):<70} {s.title}"
        if s.issue_title in seen:
            print("  skip  " + line)
            skipped += 1
            if args.project:  # item-add is idempotent, so re-runs sync the board
                gh("project", "item-add", str(args.project), "--owner", args.owner, "--url", seen[s.issue_title])
            continue
        print("  " + ("create" if args.apply else "would ") + line)
        if not args.apply:
            continue
        cmd = ["issue", "create", "--repo", args.repo, "--title", s.issue_title, "--body", issue_body(s, "docs/BACKLOG.md")]
        for lab in s.labels:
            cmd += ["--label", lab]
        if s.milestone:
            cmd += ["--milestone", s.milestone]
        url = gh(*cmd).strip()
        created += 1
        print(f"         -> {url}")
        if args.project:
            gh("project", "item-add", str(args.project), "--owner", args.owner, "--url", url)
    print(f"\n{'created' if args.apply else 'would create'} {created if args.apply else len(stories) - skipped}, skipped {skipped}")
    if not args.apply:
        print("dry-run: add --apply to create issues, --project N to add them to the board")


if __name__ == "__main__":
    main()
