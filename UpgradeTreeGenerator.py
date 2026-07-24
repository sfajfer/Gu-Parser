#!/usr/bin/env python3
"""
gu_to_canvas.py

Converts a flat JSON list of Gu (from your markdown parser) into an
Obsidian Canvas (.canvas) file laying out the full upgrade tree/DAG,
including AND-combination junctions (e.g. "Cool Gu + Dope Gu -> Awesome Gu").

Usage:
    python gu_to_canvas.py input.json output.canvas

Edge semantics (parsed from each Gu's `previousRank` field):
    "Cool Gu, Dope Gu"        -> either Cool Gu OR Dope Gu can upgrade into this Gu
    "Cool Gu + Dope Gu"       -> Cool Gu AND Dope Gu must BOTH be combined
    "Cool Gu + Dope Gu, Awesome Gu"
                                -> (Cool Gu AND Dope Gu) OR (Awesome Gu alone)

AND-groups are represented as a small synthetic junction node in the
canvas, since Canvas edges are single-source: both prerequisites point
into the junction, and the junction points into the resulting Gu.

Layout:
    - X axis = graph depth (longest path from a root prerequisite), NOT
      rank number. A node sits one column right of its latest-placed
      predecessor, so junctions and multi-rank-spanning Gu land in a
      sensible column instead of being squeezed to fit a rank grid.
    - Y axis = grouped/stacked by `path`. Within a path, a node with a
      single predecessor inherits that predecessor's row outright (kept
      in a straight horizontal line); a node with multiple predecessors
      (e.g. an AND-junction) takes their average row. Rows are only
      shifted when two nodes would otherwise collide, so straight chains
      are preserved even if it leaves visual gaps elsewhere. Nodes whose
      only predecessor is in a different `path` can't be aligned this
      way and fall back to being appended after the last-placed node.

Any Gu name referenced in a `previousRank`/`nextRank` string that isn't
found in the dataset is reported at the end as a likely typo/missing
entry, and rendered as a small red "MISSING: X" stub node so broken
links are visible directly on the canvas instead of silently dropped.

Gu with neither a `previousRank` nor a `nextRank` (i.e. not part of any
upgrade chain at all) are skipped entirely - they don't belong on an
upgrade-tree canvas.
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
    "Attack": "#e93147",           # red
    "Tonic": "#08b94e",             # green
    "Catalyst": "#e8973f",         # orange
    "Guard": "#08b7c4",             # cyan
    "Divination": "#7852ee",       # purple
    "Celerity": "#e0c43e",         # yellow
    "Manifestation": "#3f8ae8",   # blue
    "Carver": "#c43f8a",             # magenta
    "Container": "#8a8a8a",         # gray
    "Concealment": "#4a4a6a",     # slate
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
        print("Usage: python gu_to_canvas.py input.json output.canvas")
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
        nodes[nid] = {
            "kind": "gu",
            "name": g["name"],
            "path": g.get("path", "Unsorted"),
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
                    nodes[jkey] = {
                        "kind": "junction",
                        "name": " + ".join(and_names),
                        "path": nodes[child_id]["path"] if nodes[child_id]["kind"] != "missing" else "Unsorted",
                        "inputs": src_ids,
                    }
                    for sid in src_ids:
                        edges.append((sid, jkey))
                edges.append((jkey, child_id))

    # ---- Layout ----
    predecessors = defaultdict(list)
    for src, dst in edges:
        predecessors[dst].append(src)

    # X axis: longest-path layering. A node's layer = 1 + the deepest
    # layer among its predecessors (0 if it has none). This is driven
    # purely by graph structure, not by rank number, so junctions and
    # Gu that span multiple ranks land in a sensible column instead of
    # being squeezed to fit a rank-aligned grid.
    layer_of = {}

    def compute_layer(nid, visiting):
        if nid in layer_of:
            return layer_of[nid]
        if nid in visiting:
            layer_of[nid] = 0  # cycle guard; shouldn't happen in valid data
            return 0
        visiting.add(nid)
        preds = predecessors.get(nid, [])
        layer_of[nid] = 0 if not preds else 1 + max(compute_layer(p, visiting) for p in preds)
        visiting.discard(nid)
        return layer_of[nid]

    for nid in nodes:
        compute_layer(nid, set())

    paths = sorted({n["path"] for n in nodes.values()})

    # How many children does each node have, globally? Used to pick a
    # shared node's "primary" parent - the more exclusive (lower fan-out)
    # one - when it has more than one.
    out_degree = defaultdict(int)
    for src, _dst in edges:
        out_degree[src] += 1

    # ---- Y axis: reserve a contiguous vertical band per family ----
    # Resolving row conflicts one node at a time (even with a priority
    # order) can still let a shared/merge node wedge itself between two
    # rows that belong to an unrelated sibling group. Instead, every node
    # picks exactly one "primary parent" - its most exclusive same-path
    # predecessor - turning the DAG into a proper spanning tree. Each root
    # of that tree (and everything under it) then gets a strictly
    # non-overlapping vertical band, sized to its subtree, via the
    # standard tree-layout technique of centering a parent over its
    # children's rows. Any other (non-primary) predecessor relationship -
    # an AND-junction's other input, or a Gu reachable from two different
    # chains - is still drawn as an edge; it just doesn't influence row
    # placement, so it may legitimately cross into another family's band.
    # That crossing reflects the data (the node really is shared) rather
    # than being an artifact of the layout.
    primary_parent = {}
    for path in paths:
        path_nid_set = {nid for nid, n in nodes.items() if n["path"] == path}
        for nid in path_nid_set:
            same_path_preds = [p for p in predecessors[nid] if p in path_nid_set]
            if not same_path_preds:
                primary_parent[nid] = None
            elif len(same_path_preds) == 1:
                primary_parent[nid] = same_path_preds[0]
            else:
                primary_parent[nid] = min(same_path_preds, key=lambda p: (out_degree[p], nodes[p]["name"]))

    primary_children = defaultdict(list)
    for nid, parent in primary_parent.items():
        if parent is not None:
            primary_children[parent].append(nid)
    for parent, kids in primary_children.items():
        kids.sort(key=lambda nid: (layer_of[nid], nodes[nid]["name"]))

    leaf_count = {}

    def compute_leaf_count(nid, visiting):
        if nid in leaf_count:
            return leaf_count[nid]
        if nid in visiting:
            leaf_count[nid] = 1  # cycle guard; shouldn't happen in valid data
            return 1
        visiting.add(nid)
        kids = primary_children.get(nid, [])
        leaf_count[nid] = 1 if not kids else sum(compute_leaf_count(c, visiting) for c in kids)
        visiting.discard(nid)
        return leaf_count[nid]

    for nid in nodes:
        compute_leaf_count(nid, set())

    row_of = {}

    def assign_rows(nid, row_start):
        """Lay out nid's primary subtree starting at row_start. Leaves
        consume one row each, in order; an internal node is centered
        over the rows its own children ended up on. Returns the next
        free row after this entire subtree's band."""
        kids = primary_children.get(nid, [])
        if not kids:
            row_of[nid] = row_start
            return row_start + 1
        cursor = row_start
        for c in kids:
            cursor = assign_rows(c, cursor)
        row_of[nid] = round(sum(row_of[c] for c in kids) / len(kids))
        return cursor

    path_max_rows = {}
    for path in paths:
        path_nid_set = [nid for nid, n in nodes.items() if n["path"] == path]
        local_roots = sorted(
            (nid for nid in path_nid_set if primary_parent[nid] is None),
            key=lambda nid: (layer_of[nid], nodes[nid]["name"]),
        )
        cursor = 0
        for root in local_roots:
            cursor = assign_rows(root, cursor)
        path_max_rows[path] = cursor

    path_y_offset = {}
    cursor_y = 0
    for path in paths:
        path_y_offset[path] = cursor_y
        cursor_y += path_max_rows[path] * Y_SPACING + PATH_GAP

    positions = {}
    for nid, n in nodes.items():
        x = layer_of[nid] * X_SPACING
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