"""
Menu key art and gold UI icons for Kingshot, rendered with the same Blender/KayKit setup as render_sprites.py.

    KINGSHOT_LIB=1 python render_ui.py <repo_root> [--only splash,icons] [--quick]

Outputs (scratch in $KINGSHOT_TMP, finals in composeApp/src/commonMain/composeResources/drawable/):
    menu_bg.png      1080x1920 key art: the king and his archers before the castle at golden hour
    ui_icon_*.png    160x160 gold icons (gear, star, crown, shield, sword, bow, hammer, lock, chest, trophy, book,
                     scroll, heart, speaker, vibrate, play, pause, close, arrow, flag, hourglass, skull, gift, medal)
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
os.environ["KINGSHOT_LIB"] = "1"
import bpy  # noqa: E402
from mathutils import Vector  # noqa: E402
from PIL import Image, ImageFilter  # noqa: E402

import render_sprites as R  # noqa: E402

M = R.M if hasattr(R, "M") else R.MATS
OUT = os.path.join(R.ROOT, "composeApp/src/commonMain/composeResources/drawable")
ONLY = R.ONLY
SC, CAM = R.SC, R.CAM
HEXW, HEXH = 2.0, 2.31  # hex tile bbox (flat sides along X, points along Y)


def want(n):
    return ONLY is None or n in ONLY


def tile(name, col, row, z=0.0, scale=1.0, rot=0.0, sub="base"):
    x = (col + (0.5 if row % 2 else 0.0)) * HEXW * scale
    y = row * HEXH * 0.75 * scale
    return R.prop(R.HEX + "tiles/%s/%s.gltf" % (sub, name), scale, (x, y, z), rot)


def hexpos(col, row, scale=1.0):
    return ((col + (0.5 if row % 2 else 0.0)) * HEXW * scale, row * HEXH * 0.75 * scale)


# ----------------------------------------------------------------------------- key art


def splash(out_png, w=1080, h=1920):
    R.clear_models()
    rnd = random.Random(7)
    S = 1.0  # tile scale
    # ground: 11 x 14 hex grid, grassy, with a road running from the bottom to the castle hill
    road = {(5, 1): ("hex_road_A", 0), (5, 2): ("hex_road_A", 0), (5, 3): ("hex_road_B", math.radians(60)), (5, 4): ("hex_road_A", 0),
            (5, 5): ("hex_road_A", 0), (5, 6): ("hex_road_A", 0), (5, 7): ("hex_road_A", 0)}
    for row in range(-1, 14):
        for col in range(-2, 11):
            if (col, row) in road:
                n, rz = road[(col, row)]
                tile(n, col, row, 0, S, rz, "roads")
            else:
                tile("hex_grass", col, row, 0, S, rnd.choice((0, 1, 2)) * math.radians(60))
    # castle hill: raised tiles (bottom blocks under grass) around (5, 9)
    hill = [(5, 9), (4, 9), (6, 9), (5, 10), (4, 10), (5, 8), (4, 8), (6, 8), (5, 11), (6, 10), (3, 9), (7, 9)]
    for (c, r) in hill:
        tile("hex_grass_bottom", c, r, 0.0, S)
        tile("hex_grass", c, r, 1.0, S)
    for (c, r) in [(4, 11), (6, 11), (3, 10), (7, 10), (3, 8), (7, 8), (5, 7)]:
        tile("hex_grass_sloped_low", c, r, 0.0, S, rnd.choice((0, 1, 2, 3, 4, 5)) * math.radians(60))
    cx, cy = hexpos(5, 9, S)
    R.prop(R.HEX + "buildings/blue/building_castle_blue.gltf", 1.55, (cx, cy + 0.3, 1.0), math.radians(180))
    for (c, r, rz) in [(3, 9, 0), (7, 9, 0)]:
        x, y = hexpos(c, r, S)
        R.prop(R.HEX + "buildings/blue/building_tower_A_blue.gltf", 1.2, (x, y, 1.0), rz)
    for (c, r) in [(4, 10), (6, 10)]:
        x, y = hexpos(c, r, S)
        R.prop(R.HEX + "buildings/blue/building_home_A_blue.gltf", 1.0, (x, y, 1.0), rnd.uniform(-0.5, 0.5))
    x, y = hexpos(4, 8, S)
    R.prop(R.HEX + "decoration/props/flag_blue.gltf", 1.6, (x - 0.4, y - 0.5, 1.0))
    x, y = hexpos(6, 8, S)
    R.prop(R.HEX + "decoration/props/flag_blue.gltf", 1.6, (x + 0.4, y - 0.5, 1.0))
    # village along the road
    for (c, r, n, rz) in [(3, 4, "building_home_B_blue", 0.4), (7, 5, "building_windmill_blue", -0.3), (3, 6, "building_tavern_blue", 0.6),
                          (7, 3, "building_archeryrange_blue", -0.6), (8, 6, "building_well_blue", 0)]:
        x, y = hexpos(c, r, S)
        R.prop(R.HEX + "buildings/blue/%s.gltf" % n, 1.0, (x, y, 0), rz)
    # forests and rocks
    for (c, r, n) in [(1, 7, "trees_A_large"), (2, 8, "trees_B_large"), (9, 7, "trees_A_large"), (9, 9, "trees_B_medium"),
                      (1, 3, "trees_B_medium"), (9, 1, "trees_A_medium"), (2, 11, "trees_A_large"), (8, 11, "trees_B_large"),
                      (0, 5, "trees_A_small"), (10, 4, "trees_B_small"), (1, 10, "trees_B_large"), (8, 8, "trees_A_small"),
                      (2, 2, "rock_single_B"), (8, 2, "rock_single_D"), (3, 2, "tree_single_A"), (7, 7, "tree_single_B")]:
        x, y = hexpos(c, r, S)
        R.prop(R.HEX + "decoration/nature/%s.gltf" % n, 1.0, (x, y, 0), rnd.uniform(0, 6.28))
    # mountains behind
    for (c, r, n, k) in [(2, 12, "mountain_A_grass_trees", 1.8), (5, 13, "mountain_B_grass_trees", 2.3), (8, 12, "mountain_C_grass_trees", 1.7),
                         (0, 11, "hills_A_trees", 1.4), (10, 11, "hills_B_trees", 1.4), (-1, 9, "hills_C_trees", 1.2), (11, 8, "hills_A_trees", 1.2),
                         (-3, 12, "mountain_A_grass", 1.5), (13, 12, "mountain_C_grass", 1.5)]:
        x, y = hexpos(c, r, S)
        R.prop(R.HEX + "decoration/nature/%s.gltf" % n, k, (x, y, 0), rnd.uniform(0, 6.28))
    for (x, y, z, k) in [(-4, 30, 7.5, 1.6), (16, 32, 8.5, 1.8), (6, 36, 9.5, 1.4)]:
        R.prop(R.HEX + "decoration/nature/cloud_big.gltf", k, (x, y, z), rnd.uniform(0, 6.28))
    # heroes on the road: the warrior king at a gallop, three blue archers aiming north
    kx, ky = hexpos(5, 2, S)
    hroot, c, pose = R.mounted(R.build_king_warrior)
    pose(0.9, True)
    hroot.location = (kx + 0.25, ky - 0.1, 0.0)
    hroot.rotation_euler.z = math.radians(-28)
    hroot.scale = (1.15, 1.15, 1.15)
    for i, (dx, dy, rz) in enumerate([(-1.7, 0.9, -12), (1.55, 1.1, 10), (-0.6, 2.1, -4)]):
        a = R.build_archer()
        a.scale()
        a.root.location = (kx + dx, ky + dy, 0)
        a.root.rotation_euler.z = math.radians(180 + rz)  # KayKit faces -Y: turn to face north
    # red knights charging from the north-west and north-east
    for (dx, dy, rz) in [(-3.6, 5.2, 150), (-2.9, 6.1, 140), (-4.3, 6.4, 155), (4.0, 5.6, -150), (3.3, 6.5, -140)]:
        k = R.build_knight()
        k.act("Running_A")
        k.at(rnd.uniform(0, 1))
        k.scale()
        k.root.location = (kx + dx, ky + dy, 0)
        k.root.rotation_euler.z = math.radians(rz)
    bpy.context.view_layer.update()

    # golden hour: warm key from the back-right, cool fill, strong rim
    for o in list(SC.collection.objects):
        if o.type == "LIGHT":
            bpy.data.objects.remove(o)
    def sun(name, rot, energy, color, angle=3, shadow=True):
        l = bpy.data.lights.new(name, "SUN")
        l.energy, l.color, l.angle, l.use_shadow = energy, color, math.radians(angle), shadow
        o = bpy.data.objects.new(name, l)
        o.rotation_euler = rot
        SC.collection.objects.link(o)
    sun("key", (math.radians(62), 0, math.radians(150)), 3.0, (1.0, 0.84, 0.62), 2.5)
    sun("fill", (math.radians(50), 0, math.radians(-30)), 1.1, (0.6, 0.72, 1.0), 20, shadow=False)
    sun("rim", (math.radians(75), 0, math.radians(175)), 2.5, (1.0, 0.85, 0.6), 4, shadow=False)
    SC.world.node_tree.nodes["Background"].inputs[0].default_value = (0.6, 0.62, 0.75, 1)
    SC.world.node_tree.nodes["Background"].inputs[1].default_value = 0.7
    for o in list(SC.collection.objects):
        if o.name == "ground":
            o.hide_render = True
    # perspective camera: low, from the south, castle in the upper third, heroes in the lower half
    CAM.data.type = "PERSP"
    CAM.data.lens = 38
    CAM.data.sensor_fit = "VERTICAL"
    CAM.data.sensor_height = 36
    SC.render.resolution_x, SC.render.resolution_y = w, h
    SC.render.film_transparent = True
    SC.cycles.samples = 24 if R.QUICK else 96
    cam_pos = Vector((kx + 0.6, ky - 10.5, 6.2))
    target = Vector((kx + 0.2, ky + 7.0, 1.2))
    d = target - cam_pos
    CAM.location = cam_pos
    CAM.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
    bpy.context.view_layer.update()
    raw = out_png + ".raw.png"
    SC.render.filepath = raw
    bpy.ops.render.render(write_still=True)
    composite_sky(raw, out_png, w, h)


def composite_sky(raw, out_png, w, h):
    """Sky gradient + sun glow behind the transparent render, light haze towards the horizon."""
    import numpy as np
    sky = np.zeros((h, w, 3), dtype=np.float32)
    yy = np.linspace(0, 1, h)[:, None]
    top = np.array([0.08, 0.14, 0.36]); mid = np.array([0.62, 0.42, 0.52]); hor = np.array([1.0, 0.72, 0.42])
    t = np.clip(yy / 0.42, 0, 1)  # horizon is at ~42 % of the height
    col = np.where(t[..., None] < 0.55, top + (mid - top) * (t[..., None] / 0.55), mid + (hor - mid) * ((t[..., None] - 0.55) / 0.45))
    sky[:] = col
    xx = np.linspace(0, 1, w)[None, :]
    glow = np.exp(-(((xx - 0.66) ** 2) / 0.012 + ((yy - 0.40) ** 2) / 0.004))
    sky += glow[..., None] * np.array([1.0, 0.85, 0.55]) * 0.9
    rays = np.exp(-((yy - 0.42) ** 2) / 0.03) * 0.25
    sky += rays[..., None] * np.array([1.0, 0.8, 0.55])
    bg = Image.fromarray((np.clip(sky, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")
    fg = Image.open(raw).convert("RGBA")
    # haze: fade the far scene (upper band) towards the sky colour
    haze = Image.new("RGBA", (w, h), (255, 200, 150, 0))
    a = (np.clip((0.52 - yy) / 0.32, 0, 1) ** 1.8 * 95).astype(np.uint8)
    haze.putalpha(Image.fromarray(np.repeat(a, w, axis=1), "L"))
    fga = fg.getchannel("A")
    haze.putalpha(Image.fromarray((np.array(haze.getchannel("A")) * (np.array(fga) / 255.0)).astype(np.uint8), "L"))
    out = Image.alpha_composite(bg, fg)
    out = Image.alpha_composite(out, haze)
    # vignette
    vx = np.abs(np.linspace(-1, 1, w))[None, :]; vy = np.abs(np.linspace(-1, 1, h))[:, None]
    v = np.clip(1 - 0.35 * (vx ** 2.5 + vy ** 3), 0.5, 1)
    arr = np.array(out).astype(np.float32)
    arr[..., :3] *= v[..., None]
    Image.fromarray(arr.astype(np.uint8), "RGBA").convert("RGB").save(out_png, optimize=True)


# ----------------------------------------------------------------------------- gold icons


def gold_mats():
    if "ui_gold" not in M:
        M["ui_gold"] = R.mat("ui_gold", (1.0, 0.72, 0.22), rough=0.35, metal=1.0)
        M["ui_gold_dark"] = R.mat("ui_gold_dark", (0.55, 0.33, 0.08), rough=0.45, metal=1.0)
        M["ui_ruby"] = R.mat("ui_ruby", (0.85, 0.08, 0.1), rough=0.2, coat=1.0)
        M["ui_iron"] = R.mat("ui_iron", (0.25, 0.26, 0.3), rough=0.5, metal=0.8)
        M["ui_white"] = R.mat("ui_white", (0.95, 0.95, 0.92), rough=0.6)


def goldify(objs):
    for o in objs:
        if o.type == "MESH":
            o.data.materials.clear()
            o.data.materials.append(M["ui_gold"])
            for p in o.data.polygons:
                p.use_smooth = True


def star(points=5, r1=1.0, r2=0.45, depth=0.3):
    import bmesh
    bm = bmesh.new()
    top, bot = [], []
    for i in range(points * 2):
        a = math.pi / 2 + i * math.pi / points
        r = r1 if i % 2 == 0 else r2
        top.append(bm.verts.new((r * math.cos(a), r * math.sin(a), depth / 2)))
        bot.append(bm.verts.new((r * math.cos(a), r * math.sin(a), -depth / 2)))
    bm.faces.new(top); bm.faces.new(list(reversed(bot)))
    n = len(top)
    for i in range(n):
        bm.faces.new((top[i], bot[i], bot[(i + 1) % n], top[(i + 1) % n]))
    o = R._mesh_from_bmesh("star", bm)
    o.data.materials.append(M["ui_gold"])
    R.COLL.objects.link(o)
    return o


def bbox(root):
    bpy.context.view_layer.update()
    pts = [o.matrix_world @ Vector(c) for o in [root] + list(root.children_recursive) if o.type == "MESH" for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    return lo, hi


def fit(root, size=1.7, tilt_y=0.0, upright=True):
    """Turns an imported prop so its thin axis faces the camera (-Y), scales its largest extent to `size`, centers it."""
    lo, hi = bbox(root)
    ext = hi - lo
    thin = min(range(3), key=lambda i: ext[i])
    if thin == 0:
        root.rotation_euler = (0, 0, math.radians(90))
    elif thin == 2:
        root.rotation_euler = (math.radians(90), 0, 0)
    lo, hi = bbox(root)
    ext = hi - lo
    if upright and ext.x > ext.z:  # long axis along X: stand it up
        root.rotation_euler.rotate_axis("Y", math.radians(90))
        lo, hi = bbox(root)
        ext = hi - lo
    k = size / max(ext.x, ext.y, ext.z)
    root.scale = (k, k, k)
    lo, hi = bbox(root)
    c = (lo + hi) / 2
    root.location = root.location - c
    if tilt_y:
        root.rotation_euler.rotate_axis("Y", tilt_y)
    bpy.context.view_layer.update()


def build_icon(name):
    g, gd, ruby, iron = M["ui_gold"], M["ui_gold_dark"], M["ui_ruby"], M["ui_iron"]
    root = R.empty("icon")
    if name == "gear":
        root.rotation_euler = (math.radians(90), 0, 0)  # disc facing the camera
        R.cyl((0, 0, 0), 0.72, 0.72, 0.3, g, root, seg=48, bevel=0.03)
        for i in range(8):
            a = i * math.pi / 4
            R.box((0.78 * math.cos(a), 0.78 * math.sin(a), 0), (0.34, 0.26, 0.3), g, root, rot=(0, 0, a), bevel=0.03)
        R.cyl((0, 0, 0), 0.3, 0.3, 0.34, iron, root, seg=32)
    elif name == "star":
        st = star(5, 1.0, 0.48, 0.5)
        st.rotation_euler = (math.radians(90), 0, 0)
    elif name == "crown":
        r, z = 0.8, -0.35
        R.torus((0, 0, z), r, 0.06, g, root, seg=32)
        R.cyl((0, 0, z + 0.1), r, r, 0.18, g, root, seg=32)
        R.sphere((0, 0, z + 0.18), (r * 0.9, r * 0.9, 0.3), M["velvet"], root, seg=24)
        for i in range(6):
            a = i * math.pi / 3 + math.pi / 6
            x, y = math.cos(a) * (r - 0.01), math.sin(a) * (r - 0.01)
            R.cyl((x, y, z + 0.36), 0.1, 0.0, 0.38, g, root, seg=8)
            R.sphere((x * 1.02, y * 1.02, z + 0.12), 0.07, ruby, root, seg=12)
    elif name == "shield":
        objs = R.import_file(R.ACC + "shield_round.gltf")
        goldify(objs)
        for o in objs:
            if o.parent is None:
                o.parent = root
        fit(root, 1.7)
        lo, hi = bbox(root)
        R.sphere((0, lo.y - 0.05, 0), 0.2, ruby)
    elif name == "sword":
        objs = R.import_file(R.ACC + "sword_1handed.gltf"); goldify(objs)
        for o in objs:
            if o.parent is None: o.parent = root
        fit(root, 1.9, tilt_y=math.radians(35))
    elif name == "bow":
        b = R.build_bow()
        b.parent = root
        for o in [b] + list(b.children_recursive):
            if o.type == "MESH":
                o.data.materials.clear(); o.data.materials.append(g)
        fit(root, 1.8, tilt_y=math.radians(-20))
    elif name == "hammer":
        hm = R.build_hammer(); hm.parent = root
        for o in [hm] + list(hm.children_recursive):
            if o.type == "MESH":
                o.data.materials.clear(); o.data.materials.append(g)
        root.rotation_euler = (0, 0, math.radians(35)); root.location = (0, 0, -0.5)
    elif name == "lock":
        R.box((0, 0, -0.25), (1.3, 0.5, 1.0), g, root, bevel=0.08)
        R.torus((0, 0, 0.45), 0.42, 0.11, gd, root, rot=(math.radians(90), 0, 0))
        R.cyl((0, -0.26, -0.3), 0.14, 0.14, 0.1, iron, root, rot=(math.radians(90), 0, 0))
    elif name == "chest":
        R.box((0, 0, -0.35), (1.5, 1.0, 0.7), M["wood"], root, bevel=0.03)
        R.cyl((0, 0, 0.0), 0.5, 0.5, 1.5, M["wood"], root, rot=(0, math.radians(90), 0), seg=24)
        R.box((0, 0, 0.0), (1.56, 0.1, 0.1), g, root); R.box((0, 0, -0.35), (1.56, 1.06, 0.1), g, root)
        for x in (-0.5, 0.5):
            R.box((x, 0, -0.1), (0.1, 1.08, 1.25), g, root)
        R.box((0, -0.52, -0.1), (0.3, 0.1, 0.35), g, root)
    elif name == "trophy":
        R.cyl((0, 0, -0.75), 0.55, 0.55, 0.14, gd, root, seg=32)
        R.cyl((0, 0, -0.5), 0.12, 0.12, 0.4, g, root)
        R.sphere((0, 0, 0.1), (0.62, 0.62, 0.7), g, root)
        R.cyl((0, 0, 0.55), 0.62, 0.7, 0.3, g, root, seg=32)
        for sx in (-1, 1):
            R.torus((sx * 0.72, 0, 0.1), 0.32, 0.06, g, root, rot=(math.radians(90), 0, 0))
    elif name == "book":
        objs = R.import_file(R.ACC + "spellbook_closed.gltf")
        for o in objs:
            if o.parent is None: o.parent = root
        fit(root, 1.6, tilt_y=math.radians(12))
    elif name == "scroll":
        R.box((0, 0, 0), (1.1, 0.02, 1.4), M["ui_white"], root)
        for z in (0.72, -0.72):
            R.cyl((0, 0, z), 0.14, 0.14, 1.4, M["wood"], root, rot=(0, math.radians(90), 0))
            for x in (-0.72, 0.72):
                R.sphere((x, 0, z), 0.15, g, root)
        R.box((0, -0.03, 0.3), (0.7, 0.02, 0.08), iron, root); R.box((0, -0.03, 0.0), (0.7, 0.02, 0.08), iron, root)
        R.box((0, -0.03, -0.3), (0.5, 0.02, 0.08), iron, root)
    elif name == "heart":
        R.sphere((-0.36, 0, 0.3), 0.5, ruby, root); R.sphere((0.36, 0, 0.3), 0.5, ruby, root)
        R.cyl((0, 0, -0.25), 0.78, 0.0, 1.1, ruby, root, seg=48)
    elif name == "speaker":
        R.box((-0.55, 0, 0), (0.45, 0.4, 0.5), g, root, bevel=0.03)
        R.cyl((-0.15, 0, 0), 0.32, 0.65, 0.6, g, root, rot=(0, math.radians(90), 0), seg=32)
        for r in (0.6, 0.9):
            R.arc(r, math.radians(80), 0.09, g, root)
    elif name == "vibrate":
        R.box((0, 0, 0), (0.7, 0.14, 1.25), iron, root, bevel=0.03)
        R.box((0, -0.08, 0), (0.56, 0.02, 1.0), M["ui_white"], root)
        for sx in (-1, 1):
            for i, r in enumerate((0.55, 0.8)):
                R.box((sx * (0.6 + i * 0.2), 0, 0), (0.08, 0.12, 0.5 + i * 0.25), g, root)
    elif name == "play":
        import bmesh
        bm = bmesh.new()
        v = [bm.verts.new(p) for p in ((-0.6, 0.15, 0.8), (-0.6, 0.15, -0.8), (0.8, 0.15, 0), (-0.6, -0.15, 0.8), (-0.6, -0.15, -0.8), (0.8, -0.15, 0))]
        bm.faces.new(v[:3]); bm.faces.new(list(reversed(v[3:])))
        for i in range(3):
            bm.faces.new((v[i], v[3 + i], v[3 + (i + 1) % 3], v[(i + 1) % 3]))
        o = R._mesh_from_bmesh("play", bm); o.data.materials.append(g); o.parent = root; R.COLL.objects.link(o)
    elif name == "pause":
        R.box((-0.35, 0, 0), (0.42, 0.3, 1.4), g, root, bevel=0.05); R.box((0.35, 0, 0), (0.42, 0.3, 1.4), g, root, bevel=0.05)
    elif name == "close":
        R.box((0, 0, 0), (1.7, 0.3, 0.42), g, root, rot=(0, math.radians(45), 0), bevel=0.05)
        R.box((0, 0, 0), (1.7, 0.3, 0.42), g, root, rot=(0, math.radians(-45), 0), bevel=0.05)
    elif name == "arrow":
        R.box((-0.25, 0, 0), (1.1, 0.3, 0.42), g, root, bevel=0.05)
        R.cyl((0.55, 0, 0), 0.6, 0.0, 0.7, g, root, rot=(0, math.radians(90), 0), seg=3)
    elif name == "flag":
        objs = R.import_file(R.HEX + "decoration/props/flag_blue.gltf")
        for o in objs:
            if o.parent is None: o.parent = root
        fit(root, 1.9)
    elif name == "hourglass":
        for z in (0.7, -0.7):
            R.cyl((0, 0, z), 0.62, 0.62, 0.12, M["wood"], root, seg=32)
        R.cyl((0, 0, 0.35), 0.5, 0.08, 0.6, M["ui_white"], root, seg=32)
        R.cyl((0, 0, -0.35), 0.08, 0.5, 0.6, M["ui_white"], root, seg=32)
        R.cyl((0, 0, -0.5), 0.05, 0.4, 0.3, g, root, seg=32)
        for a in (0, 2.09, 4.19):
            R.cyl((0.56 * math.cos(a), 0.56 * math.sin(a), 0), 0.05, 0.05, 1.4, g, root)
    elif name == "skull":
        R.sphere((0, 0, 0.2), (0.7, 0.65, 0.65), M["ui_white"], root)
        R.box((0, -0.2, -0.45), (0.6, 0.5, 0.4), M["ui_white"], root, bevel=0.06)
        for sx in (-1, 1):
            R.sphere((sx * 0.26, -0.55, 0.2), 0.17, iron, root)
        R.cyl((0, -0.6, -0.1), 0.07, 0.07, 0.2, iron, root, seg=3, rot=(math.radians(90), 0, 0))
    elif name == "gift":
        R.box((0, 0, -0.15), (1.2, 1.2, 1.0), ruby, root, bevel=0.03)
        R.box((0, 0, 0.42), (1.3, 1.3, 0.24), ruby, root, bevel=0.03)
        R.box((0, 0, 0.0), (1.32, 0.26, 1.32), g, root); R.box((0, 0, 0.0), (0.26, 1.32, 1.32), g, root)
        R.torus((-0.3, 0, 0.75), 0.25, 0.08, g, root, rot=(math.radians(90), 0, 0))
        R.torus((0.3, 0, 0.75), 0.25, 0.08, g, root, rot=(math.radians(90), 0, 0))
    elif name == "medal":
        R.cyl((0, 0, -0.3), 0.7, 0.7, 0.16, g, root, seg=48, bevel=0.03, rot=(math.radians(90), 0, 0))
        R.cyl((0, -0.06, -0.3), 0.5, 0.5, 0.1, gd, root, seg=48, rot=(math.radians(90), 0, 0))
        s = star(5, 0.32, 0.15, 0.14); s.location = (0, -0.14, -0.3); s.rotation_euler = (math.radians(90), 0, 0)
        for sx in (-1, 1):
            R.box((sx * 0.28, 0, 0.55), (0.3, 0.1, 1.0), M["velvet"], root, rot=(0, sx * math.radians(-18), 0))
    elif name == "coins":
        for i, (x, z) in enumerate(((-0.35, -0.55), (0.3, -0.45), (0.0, -0.1), (-0.2, 0.3), (0.25, 0.4))):
            R.cyl((x, (i % 2) * 0.2 - 0.1, z), 0.5, 0.5, 0.16, g, root, seg=40, rot=(math.radians(90), 0, 0))
    bpy.context.view_layer.update()
    return root


def icons(names, px=160):
    gold_mats()
    for n in names:
        R.clear_models()
        build_icon(n)
        for o in list(SC.collection.objects):
            if o.name == "ground":
                o.hide_render = True
        CAM.data.type = "ORTHO"
        SC.render.resolution_x = SC.render.resolution_y = px
        SC.render.film_transparent = True
        SC.cycles.samples = 16 if R.QUICK else 64
        CAM.data.ortho_scale = 2.4
        p = math.radians(12)
        target = Vector((0, 0, 0))
        CAM.location = target + Vector((0, -30 * math.cos(p), 30 * math.sin(p)))
        CAM.rotation_euler = (math.pi / 2 - p, 0, 0)
        bpy.context.view_layer.update()
        path = os.path.join(R.TMP, "icon_%s.png" % n)
        SC.render.filepath = path
        bpy.ops.render.render(write_still=True)
        im = Image.open(path).convert("RGBA")
        # dark outline + soft drop shadow so the icon reads on any background
        a = im.getchannel("A")
        outline = a.filter(ImageFilter.MaxFilter(5))
        shadow = Image.new("RGBA", im.size, (40, 20, 0, 0)); shadow.putalpha(outline.filter(ImageFilter.GaussianBlur(2)))
        edge = Image.new("RGBA", im.size, (60, 35, 5, 0)); edge.putalpha(outline)
        out = Image.alpha_composite(shadow, edge)
        out = Image.alpha_composite(out, im)
        out.save(os.path.join(OUT, "ui_icon_%s.png" % n), optimize=True)
        print("icon", n)


ICONS = ["gear", "star", "crown", "shield", "sword", "bow", "hammer", "lock", "chest", "trophy", "book", "scroll", "heart",
         "speaker", "vibrate", "play", "pause", "close", "arrow", "flag", "hourglass", "skull", "gift", "medal", "coins"]

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    if want("splash"):
        splash(os.path.join(OUT, "menu_bg.png"))
        print("splash done")
    if want("icons") or (ONLY and any(n in ICONS for n in ONLY)):
        names = [n for n in ICONS if ONLY is None or "icons" in ONLY or n in ONLY]
        icons(names)
