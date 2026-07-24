#!/usr/bin/env python3
"""
UpgradeTreeGenerator.py

Converts a flat JSON list of Gu (from your markdown parser) into an
Obsidian Canvas (.canvas) file laying out the full upgrade tree/DAG,
including AND-combination junctions (e.g. "Cool Gu + Dope Gu -> Awesome Gu").

Usage:
    python UpgradeTreeGenerator.py input.json output.canvas

Edge semantics (parsed from each Gu's `previousRank` field):
    "Cool Gu, Dope Gu"        -> either Cool Gu OR Dope Gu can upgrade into this Gu
    "Cool Gu + Dope Gu"       -> Cool Gu AND Dope Gu must BOTH be combined
    "Cool Gu + Dope Gu, Awesome Gu"
                                -> (Cool Gu AND Dope Gu) OR (Awesome Gu alone)

AND-groups are represented as a small synthetic junction node in the
canvas, since Canvas edges are single-source: both prerequisites point
into the junction, and the junction points into the resulting Gu.

Layout:
    - X axis = rank tier (min(rank) of the Gu). Junctions sit at the
      midpoint between their prerequisites and their result.
    - Y axis = grouped/stacked by `path`, with a labeled Canvas group
      region drawn around each path's nodes so trees stay visually
      separated on one big canvas.

Any Gu name referenced in a `previousRank`/`nextRank` string that isn't
found in the dataset is reported at the end as a likely typo/missing
entry, and rendered as a small red "MISSING: X" stub node so broken
links are visible directly on the canvas instead of silently dropped.

Gu with neither a `previousRank` nor a `nextRank` (i.e. not part of any
upgrade chain at all) are skipped entirely - they don't belong on an
upgrade-tree canvas.

Within each path, nodes are ordered top-to-bottom using a barycenter
pass: each tier's nodes are sorted by the average row-position of their
already-placed predecessors, so connected chains land in the same row
instead of being scattered alphabetically and forcing edges to snake
across unrelated nodes.
"""

import json
import sys
import uuid
import hashlib
from collections import defaultdict

# ---- Tunable layout constants ----
X_SPACING = 320       # px between rank tiers
Y_SPACING = 140        # px between stacked nodes within a tier
PATH_GAP = 220          # extra vertical gap between different `path` groups
NODE_WIDTH = 260
NODE_HEIGHT = 90
JUNCTION_WIDTH = 200
JUNCTION_HEIGHT = 60
GROUP_PADDING = 60

TYPE_COLORS = {
    "Attack": "1",     # red
    "Tonic": "4",       # green
    "Catalyst": "2",   # orange
    "Defense": "5",     # cyan
    "Utility": "6",     # purple
}


def stable_id(*parts):
    """Deterministic id so re-running the script on the same data
    produces the same node/edge ids (helps if you diff canvas files)."""
    h = hashlib.sha1("::".join(str(p) for p in parts).encode()).hexdigest()
    return h[:16]


def parse_prev_rank(prev_rank_str):
    """Returns a list of OR-groups, each an AND-list of prerequisite names.
    "Cool Gu + Dope Gu, Awesome Gu" -> [["Cool Gu", "Dope Gu"], ["Awesome Gu"]]
    """
    if not prev_rank_str:
        return []
    groups = []
    for or_part in prev_rank_str.split(","):
        and_names = [n.strip() for n in or_part.split("+") if n.strip()]
        if and_names:
            groups.append(and_names)
    return groups


def main():
    if len(sys.argv) < 3:
        print("Usage: python UpgradeTreeGenerator.py input.json output.canvas")
        sys.exit(1)

    in_path, out_path = sys.argv[1], sys.argv[2]

    with open(in_path, "r", encoding="utf-8") as f:
        gu_list = json.load(f)

    missing_refs = set()

    def is_relevant(g):
        """A Gu only belongs on the upgrade-tree canvas if it actually
        participates in a chain (has a previousRank and/or nextRank)."""
        prev = (g.get("previousRank") or "").strip()
        nxt = (g.get("nextRank") or "").strip()
        return bool(prev) or bool(nxt)

    relevant_gu = [g for g in gu_list if is_relevant(g)]
    skipped_count = len(gu_list) - len(relevant_gu)

    # ---- Build node records ----
    # node id -> dict(kind='gu'|'junction'|'missing', ...)
    nodes = {}
    for g in relevant_gu:
        nid = f"gu_{stable_id(g.get('id', g['name']))}"
        rank = g.get("rank") or [0]
        tier = min(rank) if isinstance(rank, list) else rank
        nodes[nid] = {
            "kind": "gu",
            "name": g["name"],
            "path": g.get("path", "Unsorted"),
            "tier": tier,
            "gu": g,
        }

    def get_or_create_missing(name):
        nid = f"missing_{stable_id(name)}"
        if nid not in nodes:
            missing_refs.add(name)
            nodes[nid] = {
                "kind": "missing",
                "name": name,
                "path": "MISSING",
                "tier": 0,
            }
        return nid

    def find_node_id_by_name(name):
        for nid, n in nodes.items():
            if n["kind"] == "gu" and n["name"] == name:
                return nid
        return get_or_create_missing(name)

    # ---- Build edges (and junction nodes for AND-groups) ----
    edges = []  # list of (from_id, to_id)
    for g in relevant_gu:
        child_id = find_node_id_by_name(g["name"])
        groups = parse_prev_rank(g.get("previousRank", ""))
        for and_names in groups:
            if len(and_names) == 1:
                src_id = find_node_id_by_name(and_names[0])
                edges.append((src_id, child_id))
            else:
                # AND-combination: create a junction node
                jkey = "junction_" + stable_id(*sorted(and_names), g["name"])
                if jkey not in nodes:
                    src_ids = [find_node_id_by_name(n) for n in and_names]
                    src_tiers = [
                        nodes[sid]["tier"] for sid in src_ids
                        if nodes[sid]["kind"] != "missing"
                    ]
                    tier = (max(src_tiers) if src_tiers else 0) + 0.5
                    nodes[jkey] = {
                        "kind": "junction",
                        "name": " + ".join(and_names),
                        "path": nodes[child_id]["path"] if nodes[child_id]["kind"] != "missing" else "Unsorted",
                        "tier": tier,
                        "inputs": src_ids,
                    }
                    for sid in src_ids:
                        edges.append((sid, jkey))
                edges.append((jkey, child_id))

    # ---- Layout: group by path, order within each path via barycenter ----
    predecessors = defaultdict(list)
    for src, dst in edges:
        predecessors[dst].append(src)

    paths = sorted({n["path"] for n in nodes.values()})

    # For each path independently: process tiers left-to-right, and within
    # each tier sort nodes by the average row of their already-placed
    # predecessors (barycenter heuristic). Nodes with no placed predecessor
    # fall back to alphabetical, after connected nodes. This keeps chains
    # roughly aligned in a straight row instead of scattering alphabetically
    # and forcing edges to cross over unrelated nodes.
    row_of = {}          # nid -> row index (local to its tier, per path)
    path_max_rows = {}   # path -> widest tier (rows needed) in that path

    for path in paths:
        path_nids = [nid for nid, n in nodes.items() if n["path"] == path]
        tiers = sorted({nodes[nid]["tier"] for nid in path_nids})
        assigned = {}  # nid -> row, filled in as we go tier by tier
        widest = 1
        for tier in tiers:
            tier_nids = [nid for nid in path_nids if nodes[nid]["tier"] == tier]

            def sort_key(nid):
                preds = [p for p in predecessors[nid] if p in assigned]
                if preds:
                    avg_row = sum(assigned[p] for p in preds) / len(preds)
                    return (0, avg_row, nodes[nid]["name"])
                return (1, 0.0, nodes[nid]["name"])

            tier_nids.sort(key=sort_key)
            for i, nid in enumerate(tier_nids):
                assigned[nid] = i
            widest = max(widest, len(tier_nids))
        row_of.update(assigned)
        path_max_rows[path] = widest

    path_y_offset = {}
    cursor_y = 0
    for path in paths:
        path_y_offset[path] = cursor_y
        cursor_y += path_max_rows[path] * Y_SPACING + PATH_GAP

    positions = {}
    for nid, n in nodes.items():
        x = n["tier"] * X_SPACING
        y = path_y_offset[n["path"]] + row_of[nid] * Y_SPACING
        positions[nid] = (x, y)

    # ---- Emit Canvas JSON ----
    canvas_nodes = []
    canvas_edges = []

    for nid, n in nodes.items():
        x, y = positions[nid]
        if n["kind"] == "gu":
            g = n["gu"]
            rank = g.get("rank")
            rank_str = "-".join(str(r) for r in [min(rank), max(rank)]) if rank and max(rank) != min(rank) else (str(rank[0]) if rank else "?")
            gtype = g.get("type", "")
            text = f"**{g['name']}**\nRank {rank_str} {gtype}\n{g.get('cost', '')}"
            canvas_nodes.append({
                "id": nid,
                "type": "text",
                "text": text,
                "x": x,
                "y": y,
                "width": NODE_WIDTH,
                "height": NODE_HEIGHT,
                "color": TYPE_COLORS.get(gtype, ""),
            })
        elif n["kind"] == "junction":
            canvas_nodes.append({
                "id": nid,
                "type": "text",
                "text": f"⊕ {n['name']}",
                "x": x,
                "y": y,
                "width": JUNCTION_WIDTH,
                "height": JUNCTION_HEIGHT,
                "color": "3",  # yellow, visually distinct combination point
            })
        else:  # missing
            canvas_nodes.append({
                "id": nid,
                "type": "text",
                "text": f"⚠ MISSING: {n['name']}",
                "x": x,
                "y": y,
                "width": NODE_WIDTH,
                "height": 60,
                "color": "1",  # red
            })

    for i, (src, dst) in enumerate(edges):
        canvas_edges.append({
            "id": f"edge_{stable_id(src, dst, i)}",
            "fromNode": src,
            "fromSide": "right",
            "toNode": dst,
            "toSide": "left",
        })

    # ---- Group regions per path ----
    for path in paths:
        if path == "MISSING":
            continue
        member_ids = [nid for nid, n in nodes.items() if n["path"] == path]
        if not member_ids:
            continue
        xs = [positions[nid][0] for nid in member_ids]
        ys = [positions[nid][1] for nid in member_ids]
        min_x, max_x = min(xs) - GROUP_PADDING, max(xs) + NODE_WIDTH + GROUP_PADDING
        min_y, max_y = min(ys) - GROUP_PADDING - 30, max(ys) + NODE_HEIGHT + GROUP_PADDING
        canvas_nodes.append({
            "id": f"group_{stable_id(path)}",
            "type": "group",
            "label": path,
            "x": min_x,
            "y": min_y,
            "width": max_x - min_x,
            "height": max_y - min_y,
        })

    canvas = {"nodes": canvas_nodes, "edges": canvas_edges}

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(canvas, f, indent=2, ensure_ascii=False)

    print(f"Wrote {len(canvas_nodes)} nodes and {len(canvas_edges)} edges to {out_path}")
    if skipped_count:
        print(f"Skipped {skipped_count} Gu with no previousRank/nextRank (not part of any upgrade chain)")
    if missing_refs:
        print(f"\n⚠ {len(missing_refs)} referenced Gu name(s) not found in your dataset (likely typos or not-yet-added entries):")
        for name in sorted(missing_refs):
            print(f"   - {name}")


if __name__ == "__main__":
    main()