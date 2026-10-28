"""
Renders the Kingshot sprites in Blender from the CC0 KayKit packs (Kay Lousberg) plus a few models built here.

    python render_sprites.py <repo_root> [--quick] [--only archer,king,...] [--rows N]

Needs `pip install bpy pillow numpy` (Blender 5.x as a Python module) and the KayKit repositories cloned into
$KAYKIT_DIR (default /home/user/kaykit-game-assets, one folder per repo, lower-case names):
    kaykit-character-pack-adventures-1.0   characters (rigged glTF with Running/Idle/Attack actions)
    kaykit-medieval-hexagon-pack-1.0       buildings, trees, rocks

Camera: orthographic, pitched 45 degrees, looking north (+Y). Units: 1 Blender meter = 32 game units.
Animated units are atlases of cols x 8 rows (row d = facing rotated d*45 degrees from south towards east,
the same convention as Game.dirIndex). Outputs go to
    composeApp/src/commonMain/composeResources/drawable/<name>.png
    composeApp/src/commonMain/kotlin/fr/kingshot/game/SpriteData.kt
"""
import math
import os
import sys
import time

import bpy  # must come before bmesh
import bmesh
import numpy as np
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Matrix, Vector
from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else "."
QUICK = "--quick" in sys.argv
ONLY = None
ROWS = 8
for i, a in enumerate(sys.argv):
    if a == "--only":
        ONLY = set(sys.argv[i + 1].split(","))
    if a == "--rows":
        ROWS = int(sys.argv[i + 1])
UPM = 32.0  # game units per meter
UNIT_SCALE = 1.5  # Renderer.UNIT_SCALE: animated units are drawn 1.5x
PITCH = math.radians(45)
TMP = os.environ.get("KINGSHOT_TMP", "/tmp/kingshot_sprites")
ASSETS = os.environ.get("KAYKIT_DIR", "/home/user/kaykit-game-assets")
os.makedirs(TMP, exist_ok=True)

CH = os.path.join(ASSETS, "kaykit-character-pack-adventures-1.0/addons/kaykit_character_pack_adventures/Characters/gltf/")
ACC = os.path.join(ASSETS, "kaykit-character-pack-adventures-1.0/addons/kaykit_character_pack_adventures/Assets/gltf/")
HEX = os.path.join(ASSETS, "kaykit-medieval-hexagon-pack-1.0/addons/kaykit_medieval_hexagon_pack/Assets/gltf/")
KS = 0.72  # KayKit character units to meters (1.6 m tall)

# ----------------------------------------------------------------------------- scene


def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = 12 if QUICK else 48
    sc.cycles.use_denoising = True
    try:
        sc.cycles.denoiser = "OPENIMAGEDENOISE"
    except Exception:
        pass
    sc.cycles.max_bounces = 4
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    sc.view_settings.view_transform = "Standard"
    sc.view_settings.look = "None"
    sc.view_settings.exposure = 0.1
    world = bpy.data.worlds.new("w")
    sc.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.55, 0.66, 0.9, 1)  # sky ambient
    bg.inputs[1].default_value = 0.6

    cam = bpy.data.cameras.new("cam")
    cam.type = "ORTHO"
    co = bpy.data.objects.new("cam", cam)
    sc.collection.objects.link(co)
    sc.camera = co

    def sun(name, rot, energy, color, angle=10, shadow=True):
        l = bpy.data.lights.new(name, "SUN")
        l.energy = energy
        l.color = color
        l.angle = math.radians(angle)
        l.use_shadow = shadow
        o = bpy.data.objects.new(name, l)
        o.rotation_euler = rot
        sc.collection.objects.link(o)

    # Warm afternoon sun from the front-left, cool sky fill from the right, rim from behind.
    sun("key", (math.radians(30), math.radians(-22), math.radians(-30)), 4.4, (1.0, 0.93, 0.8), 9)
    sun("fill", (math.radians(58), math.radians(32), math.radians(40)), 1.2, (0.72, 0.8, 1.0), 25, shadow=False)
    sun("rim", (math.radians(-118), 0, math.radians(12)), 1.6, (0.9, 0.95, 1.0), shadow=False)
    coll = bpy.data.collections.new("Models")
    sc.collection.children.link(coll)
    # Shadow catcher: the ground only shows the shadows of the model, on the transparent film.
    gm = bpy.data.meshes.new("ground")
    gm.from_pydata([(-40, -40, 0), (40, -40, 0), (40, 40, 0), (-40, 40, 0)], [], [(0, 1, 2, 3)])
    go = bpy.data.objects.new("ground", gm)
    go.is_shadow_catcher = True
    sc.collection.objects.link(go)
    return sc, co, coll


SC, CAM, COLL = reset_scene()
MATS = {}


def mat(name, color, rough=0.6, metal=0.0, emit=None, strength=0.0, sss=0.0, coat=0.0, alpha=1.0):
    if name in MATS:
        return MATS[name]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    if emit is not None:
        b.inputs["Emission Color"].default_value = (*emit, 1)
        b.inputs["Emission Strength"].default_value = strength
    if sss > 0:
        b.inputs["Subsurface Weight"].default_value = sss
        b.inputs["Subsurface Radius"].default_value = (0.3, 0.1, 0.05)
    if coat > 0:
        b.inputs["Coat Weight"].default_value = coat
    if alpha < 1:
        b.inputs["Alpha"].default_value = alpha
    m.use_fake_user = True
    MATS[name] = m
    return m


def noise_mat(name, c1, c2, scale=6.0, rough=0.85, bump=0.3):
    """Wood / stone material with a procedural color variation and bump."""
    if name in MATS:
        return MATS[name]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    b.inputs["Roughness"].default_value = rough
    tc = nt.nodes.new("ShaderNodeTexCoord")
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = scale
    noise.inputs["Detail"].default_value = 6
    nt.links.new(tc.outputs["Object"], noise.inputs["Vector"])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (*c1, 1)
    ramp.color_ramp.elements[1].color = (*c2, 1)
    nt.links.new(noise.outputs["Fac"], ramp.inputs["Fac"])
    bmp = nt.nodes.new("ShaderNodeBump")
    bmp.inputs["Strength"].default_value = bump
    nt.links.new(ramp.outputs["Color"], b.inputs["Base Color"])
    nt.links.new(noise.outputs["Fac"], bmp.inputs["Height"])
    nt.links.new(bmp.outputs["Normal"], b.inputs["Normal"])
    m.use_fake_user = True
    MATS[name] = m
    return m


# ----------------------------------------------------------------------------- primitives


def _finish(o, m, parent, smooth, subsurf, bevel):
    COLL.objects.link(o)
    if m is not None:
        o.data.materials.append(m)
    if parent is not None:
        o.parent = parent
    if smooth:
        o.data.polygons.foreach_set("use_smooth", [True] * len(o.data.polygons))
    if bevel:
        mod = o.modifiers.new("bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 3
    if subsurf:
        mod = o.modifiers.new("sub", "SUBSURF")
        mod.levels = subsurf
        mod.render_levels = subsurf
    return o


def _mesh_from_bmesh(name, bm):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    return bpy.data.objects.new(name, me)


def empty(name, loc=(0, 0, 0), parent=None, rot=(0, 0, 0)):
    o = bpy.data.objects.new(name, None)
    o.location = loc
    o.rotation_euler = rot
    COLL.objects.link(o)
    if parent is not None:
        o.parent = parent
    return o


def sphere(loc, scale, m, parent=None, seg=24, rot=(0, 0, 0), subsurf=0):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=seg, v_segments=seg // 2, radius=1.0)
    o = _mesh_from_bmesh("sphere", bm)
    o.location = loc
    o.scale = scale if isinstance(scale, tuple) else (scale, scale, scale)
    o.rotation_euler = rot
    return _finish(o, m, parent, True, subsurf, 0)


def cyl(loc, r1, r2, depth, m, parent=None, rot=(0, 0, 0), seg=24, smooth=True, bevel=0.0, subsurf=0):
    """Cylinder / cone along local Z, centered on loc."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, cap_tris=False, segments=seg, radius1=r1, radius2=r2, depth=depth)
    o = _mesh_from_bmesh("cyl", bm)
    o.location = loc
    o.rotation_euler = rot
    return _finish(o, m, parent, smooth, subsurf, bevel)


def box(loc, size, m, parent=None, rot=(0, 0, 0), bevel=0.0, subsurf=0, smooth=False):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co.x *= size[0]
        v.co.y *= size[1]
        v.co.z *= size[2]
    o = _mesh_from_bmesh("box", bm)
    o.location = loc
    o.rotation_euler = rot
    return _finish(o, m, parent, smooth, subsurf, bevel)


def torus(loc, major, minor, m, parent=None, rot=(0, 0, 0), seg=32):
    bm = bmesh.new()
    for i in range(seg):
        a = 2 * math.pi * i / seg
        for j in range(12):
            b = 2 * math.pi * j / 12
            r = major + minor * math.cos(b)
            bm.verts.new((r * math.cos(a), r * math.sin(a), minor * math.sin(b)))
    bm.verts.ensure_lookup_table()
    for i in range(seg):
        for j in range(12):
            a = i * 12 + j
            b = ((i + 1) % seg) * 12 + j
            c = ((i + 1) % seg) * 12 + (j + 1) % 12
            d = i * 12 + (j + 1) % 12
            bm.faces.new((bm.verts[a], bm.verts[b], bm.verts[c], bm.verts[d]))
    o = _mesh_from_bmesh("torus", bm)
    o.location = loc
    o.rotation_euler = rot
    return _finish(o, m, parent, True, 0, 0)


def tube(points, radius, m, parent=None, taper=0.8):
    cu = bpy.data.curves.new("tube", "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth = radius
    cu.bevel_resolution = 4
    sp = cu.splines.new("NURBS")
    sp.points.add(len(points) - 1)
    for i, p in enumerate(points):
        sp.points[i].co = (*p, 1)
        sp.points[i].radius = 1.0 - taper * i / max(1, len(points) - 1)
    sp.use_endpoint_u = True
    o = bpy.data.objects.new("tube", cu)
    COLL.objects.link(o)
    o.data.materials.append(m)
    if parent is not None:
        o.parent = parent
    return o


def arc(radius, angle, thickness, m, parent=None, seg=16):
    """Bow limb: an arc in the local XZ plane bulging towards -Y... built in XZ, caller rotates."""
    pts = []
    for i in range(seg + 1):
        a = -angle / 2 + angle * i / seg
        pts.append((0, -radius * math.cos(a) + radius, radius * math.sin(a)))
    cu = bpy.data.curves.new("arc", "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth = thickness
    cu.bevel_resolution = 3
    sp = cu.splines.new("POLY")
    sp.points.add(len(pts) - 1)
    for i, p in enumerate(pts):
        sp.points[i].co = (*p, 1)
        t = abs(i - seg / 2) / (seg / 2)
        sp.points[i].radius = 1.0 - 0.55 * t
    o = bpy.data.objects.new("arc", cu)
    COLL.objects.link(o)
    o.data.materials.append(m)
    if parent is not None:
        o.parent = parent
    return o


# ----------------------------------------------------------------------------- materials

M = {}


def build_materials():
    M["wood"] = noise_mat("wood", (0.11, 0.055, 0.022), (0.2, 0.11, 0.045), scale=3.0, rough=0.7, bump=0.25)
    M["wood_dark"] = noise_mat("wood_dark", (0.06, 0.03, 0.013), (0.12, 0.065, 0.028), scale=3.0, rough=0.75, bump=0.25)
    M["wood_cut"] = mat("wood_cut", (0.32, 0.22, 0.11), rough=0.8)
    M["rope"] = mat("rope", (0.72, 0.6, 0.38), rough=0.9)
    M["gold"] = mat("gold", (1.0, 0.76, 0.22), rough=0.28, metal=1.0)
    M["gold_dark"] = mat("gold_dark", (0.72, 0.5, 0.12), rough=0.35, metal=1.0)
    M["ruby"] = mat("ruby", (0.9, 0.08, 0.1), rough=0.1, emit=(0.6, 0.02, 0.03), strength=0.8)
    M["velvet"] = mat("velvet", (0.6, 0.06, 0.08), rough=0.85)
    M["horse"] = mat("horse", (0.93, 0.9, 0.84), rough=0.65, sss=0.1)
    M["mane"] = mat("mane", (0.5, 0.5, 0.54), rough=0.8)
    M["hoof"] = mat("hoof", (0.2, 0.17, 0.16), rough=0.5)
    M["saddle"] = mat("saddle", (0.55, 0.08, 0.08), rough=0.5, coat=0.3)
    M["leather"] = mat("leather", (0.3, 0.17, 0.08), rough=0.55, coat=0.2)
    M["eye"] = mat("eye", (0.02, 0.02, 0.02), rough=0.2)
    M["bow"] = noise_mat("bow", (0.4, 0.24, 0.1), (0.55, 0.36, 0.16), scale=8, rough=0.55, bump=0.1)
    M["string"] = mat("string", (0.9, 0.88, 0.8), rough=0.7)
    M["flag"] = mat("flag", (0.85, 0.12, 0.12), rough=0.8)
    M["coin"] = mat("coin", (1.0, 0.78, 0.2), rough=0.3, metal=1.0)
    M["coin_dark"] = mat("coin_dark", (0.8, 0.55, 0.1), rough=0.35, metal=1.0)


build_materials()


# ----------------------------------------------------------------------------- KayKit import


def import_file(path):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    new = [o for o in bpy.data.objects if o not in before]
    for o in new:
        for c in o.users_collection:
            c.objects.unlink(o)
        COLL.objects.link(o)
    return new


def prop(path, scale, loc=(0, 0, 0), rot_z=0.0, parent=None):
    new = import_file(path)
    root = empty("prop", loc, parent, (0, 0, rot_z))
    for o in new:
        if o.parent is None:
            o.parent = root
    root.scale = scale if isinstance(scale, tuple) else (scale, scale, scale)
    return root


def rgb_to_hsv(a):
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx = a.max(-1)
    mn = a.min(-1)
    d = mx - mn
    h = np.zeros_like(mx)
    m = d > 1e-6
    rr = m & (mx == r)
    gg = m & (mx == g) & ~rr
    bb = m & ~rr & ~gg
    h[rr] = ((g - b)[rr] / d[rr]) % 6
    h[gg] = (b - r)[gg] / d[gg] + 2
    h[bb] = (r - g)[bb] / d[bb] + 4
    h = h * 60
    s = np.where(mx > 1e-6, d / np.maximum(mx, 1e-6), 0)
    return h, s, mx


def recolor(tex_name, rules, out_name):
    """rules: (hue_min, hue_max, sat_min, sat_max, val_min, val_max, target_rgb). Keeps each swatch's shading."""
    out = os.path.join(TMP, out_name + ".png")
    im = Image.open(os.path.join(CH, tex_name)).convert("RGBA")
    a = np.asarray(im).astype(np.float32) / 255.0
    rgb = a[..., :3]
    h, s, v = rgb_to_hsv(rgb)
    res = rgb.copy()
    for hmin, hmax, smin, smax, vmin, vmax, target in rules:
        if hmin <= hmax:
            hm = (h >= hmin) & (h <= hmax)
        else:
            hm = (h >= hmin) | (h <= hmax)
        m = hm & (s >= smin) & (s <= smax) & (v >= vmin) & (v <= vmax)
        if not m.any():
            continue
        t = np.array(target, dtype=np.float32)
        shade = (v[m] / max(1e-3, float(np.median(v[m]))))[:, None]
        res[m] = np.clip(t[None, :] * shade, 0, 1)
    a[..., :3] = res
    Image.fromarray((a * 255).astype(np.uint8)).save(out)
    return out


def retexture(objs, png):
    img = bpy.data.images.load(png, check_existing=True)
    for o in objs:
        if o.type != "MESH":
            continue
        for m in o.data.materials:
            if m is None or not m.use_nodes:
                continue
            for n in m.node_tree.nodes:
                if n.type == "TEX_IMAGE":
                    n.image = img


class Char:
    def __init__(self, name, keep, texture=None):
        new = import_file(CH + name + ".glb")
        self.rig = [o for o in new if o.type == "ARMATURE"][0]
        self.meshes = []
        for o in new:
            if o.type != "MESH":
                continue
            base = o.name.split(".")[0]
            if base in keep:
                self.meshes.append(o)
            else:
                bpy.data.objects.remove(o, do_unlink=True)
        if texture:
            for o in self.meshes:
                for i, m in enumerate(o.data.materials):
                    if m is not None:
                        o.data.materials[i] = m.copy()
            retexture(self.meshes, texture)
        self.root = empty("root")
        self.rig.parent = self.root
        ad = self.rig.animation_data or self.rig.animation_data_create()
        for t in ad.nla_tracks:
            t.mute = True
        self.action = None

    def scale(self, s=KS):
        self.root.scale = (s, s, s)

    def act(self, name):
        act = bpy.data.actions.get(name)
        if act is None:
            act = [a for a in bpy.data.actions if a.name.split(".")[0] == name][0]
        ad = self.rig.animation_data
        ad.action = act
        try:
            if act.slots:
                ad.action_slot = act.slots[0]
        except AttributeError:
            pass
        self.action = act

    def at(self, f):
        """Pose at fraction f (0..1) of the current action."""
        s, e = self.action.frame_range
        fr = s + (e - s) * f
        SC.frame_set(int(fr), subframe=fr - int(fr))
        bpy.context.view_layer.update()

    def capture(self, bones):
        return {b: self.rig.pose.bones[b].matrix_basis.copy() for b in bones}

    def apply(self, pose):
        for b, m in pose.items():
            self.rig.pose.bones[b].matrix_basis = m
        bpy.context.view_layer.update()

    def rotate_bone(self, bone, axis, deg):
        pb = self.rig.pose.bones[bone]
        pb.matrix_basis = pb.matrix_basis @ Matrix.Rotation(math.radians(deg), 4, axis)
        bpy.context.view_layer.update()

    def attach(self, obj, bone):
        """Parent obj to a bone, keeping its current world transform (character in rest pose)."""
        self.rig.data.pose_position = "REST"
        bpy.context.view_layer.update()
        pb = self.rig.pose.bones[bone]
        mw = obj.matrix_world.copy()
        obj.parent = self.rig
        obj.parent_type = "BONE"
        obj.parent_bone = bone
        mb = self.rig.matrix_world @ pb.bone.matrix_local @ Matrix.Translation((0, pb.bone.length, 0))
        obj.matrix_parent_inverse = mb.inverted()
        obj.matrix_world = mw
        self.rig.data.pose_position = "POSE"
        bpy.context.view_layer.update()

    def attach_posed(self, obj, bone):
        """Parent obj to a bone, keeping its current world transform in the CURRENT pose."""
        bpy.context.view_layer.update()
        pb = self.rig.pose.bones[bone]
        mw = obj.matrix_world.copy()
        obj.parent = self.rig
        obj.parent_type = "BONE"
        obj.parent_bone = bone
        mb = self.rig.matrix_world @ pb.matrix @ Matrix.Translation((0, pb.length, 0))
        obj.matrix_parent_inverse = Matrix.Identity(4)
        obj.matrix_basis = mb.inverted() @ mw
        bpy.context.view_layer.update()

    def face(self, d):
        self.root.rotation_euler.z = d * math.pi / 4

    def bone_world(self, bone):
        pb = self.rig.pose.bones[bone]
        return self.rig.matrix_world @ pb.head


# ----------------------------------------------------------------------------- characters


def build_bow():
    """A longbow held upright: limbs in the YZ plane bulging forward (-Y), string behind."""
    root = empty("bow")
    arc(0.9, math.radians(150), 0.045, M["bow"], root)
    tip = 0.9 * math.sin(math.radians(75))
    cyl((0, 0.9 - 0.9 * math.cos(math.radians(75)), 0), 0.012, 0.012, 2 * tip, M["string"], root, seg=6)
    cyl((0, 0, 0), 0.075, 0.075, 0.3, M["leather"], root, seg=10)
    return root


def build_archer():
    c = Char("Rogue_Hooded", {"Rogue_ArmLeft", "Rogue_ArmRight", "Rogue_Body", "Rogue_Head_Hooded", "Rogue_LegLeft",
                              "Rogue_LegRight", "Rogue_Cape"})
    c.act("1H_Ranged_Aiming")
    c.at(0.0)
    bow = build_bow()
    hand = c.bone_world("hand.r")
    bow.location = hand + Vector((0.0, -0.08, 0))
    bow.rotation_euler = (math.radians(-6), 0, 0)
    c.attach_posed(bow, "hand.r")
    quiver = prop(ACC + "quiver.gltf", 1.0)
    quiver.location = (-0.2, 0.42, 1.15)
    quiver.rotation_euler = (math.radians(20), math.radians(-25), math.radians(15))
    c.attach(quiver, "chest")
    return c


def knight_texture(name, armor, accent=None, chain=None):
    rules = [(185, 215, 0.0, 0.2, 0.45, 1.0, armor)]  # steel plates
    rules.append((185, 215, 0.0, 0.35, 0.05, 0.45, chain or armor))  # chain mail and dark steel
    if accent is not None:
        rules.append((340, 15, 0.4, 1.0, 0.3, 1.0, accent))
    return recolor("knight_texture.png", rules, name)


def build_knight():
    tex = knight_texture("tex_knight_red", (0.78, 0.12, 0.1), (0.95, 0.75, 0.2), (0.24, 0.2, 0.22))
    c = Char("Knight", {"Knight_ArmLeft", "Knight_ArmRight", "Knight_Body", "Knight_Head", "Knight_Helmet",
                        "Knight_LegLeft", "Knight_LegRight", "1H_Sword", "Spike_Shield"}, tex)
    return c


def build_boss():
    tex = knight_texture("tex_boss", (0.5, 0.04, 0.06), (0.1, 0.08, 0.08), (0.14, 0.1, 0.12))
    c = Char("Knight", {"Knight_ArmLeft", "Knight_ArmRight", "Knight_Body", "Knight_Head", "Knight_Helmet",
                        "Knight_LegLeft", "Knight_LegRight", "2H_Sword", "Knight_Cape"}, tex)
    bone = mat("bone", (0.85, 0.8, 0.68), rough=0.5)
    for sx in (-1, 1):
        h = empty("horn", (sx * 0.4, 0.0, 2.05), rot=(math.radians(-10), math.radians(sx * 50), 0))
        cyl((0, 0, 0.22), 0.1, 0.0, 0.5, bone, h, seg=10)
        c.attach(h, "head")
    return c


def build_crown(c):
    parts = []
    z = 2.12
    parts.append(torus((0, 0, z), 0.4, 0.05, M["gold"], seg=24))
    parts.append(cyl((0, 0, z + 0.08), 0.4, 0.4, 0.14, M["gold"], seg=24))
    parts.append(sphere((0, 0, z + 0.14), (0.36, 0.36, 0.22), M["velvet"], seg=16))
    for i in range(6):
        a = i * math.pi / 3 + math.pi / 6
        x, y = math.cos(a) * 0.39, math.sin(a) * 0.39
        parts.append(cyl((x, y, z + 0.26), 0.07, 0.0, 0.26, M["gold"], seg=8))
        parts.append(sphere((x * 1.02, y * 1.02, z + 0.1), 0.045, M["ruby"], seg=10))
    for p in parts:
        c.attach(p, "head")


def build_horse():
    """Chibi white horse facing -Y, origin at the ground under its belly. Returns (root, body, legs, head)."""
    root = empty("horse")
    body = empty("hbody", (0, 0, 0), root)
    h = M["horse"]
    sphere((0, 0.05, 0.8), (0.3, 0.5, 0.28), h, body, subsurf=1)
    sphere((0, -0.36, 0.8), (0.27, 0.28, 0.27), h, body, subsurf=1)
    sphere((0, 0.44, 0.8), (0.28, 0.27, 0.27), h, body, subsurf=1)
    cyl((0, -0.58, 1.18), 0.17, 0.12, 0.64, h, body, rot=(math.radians(30), 0, 0), subsurf=1)
    head = empty("hhead", (0, -0.74, 1.42), body)
    box((0, -0.12, 0), (0.24, 0.44, 0.26), h, head, rot=(math.radians(-32), 0, 0), bevel=0.06, subsurf=1, smooth=True)
    sphere((0, -0.35, -0.15), (0.11, 0.13, 0.1), h, head, subsurf=1)
    for sx in (-1, 1):
        sphere((sx * 0.115, -0.13, 0.06), 0.035, M["eye"], head)
        cyl((sx * 0.09, 0.04, 0.2), 0.045, 0.0, 0.15, h, head, rot=(math.radians(-15), math.radians(sx * 20), 0), seg=8)
    # Mane along the neck and a forelock
    for i in range(7):
        t = i / 6
        sphere((0, -0.3 - 0.4 * t, 1.08 + 0.52 * t), (0.075, 0.11, 0.13), M["mane"], body, subsurf=1)
    sphere((0, -0.05, 0.22), (0.1, 0.13, 0.09), M["mane"], head, subsurf=1)
    tube([(0, 0.7, 0.92), (0, 0.84, 0.7), (0, 0.8, 0.42)], 0.07, M["mane"], body, taper=0.6)
    legs = []
    for x, y in ((-0.16, -0.34), (0.16, -0.34), (-0.16, 0.38), (0.16, 0.38)):
        piv = empty("leg", (x, y, 0.66), body)
        cyl((0, 0, -0.3), 0.08, 0.06, 0.62, h, piv, subsurf=1)
        cyl((0, 0, -0.64), 0.075, 0.07, 0.1, M["hoof"], piv, seg=12)
        legs.append(piv)
    # Saddle and cloth
    box((0, 0.06, 0.98), (0.5, 0.56, 0.03), M["velvet"], body, bevel=0.01)
    box((0, 0.06, 1.04), (0.34, 0.4, 0.12), M["saddle"], body, bevel=0.04, subsurf=1, smooth=True)
    torus((0, 0.06, 1.0), 0.3, 0.02, M["gold_dark"], body, seg=24)
    # Bridle
    torus((0, -1.06, 1.3), 0.13, 0.015, M["leather"], head, rot=(math.radians(60), 0, 0), seg=16)
    return root, body, legs, head


def pose_horse(body, legs, head, phase, moving):
    if not moving:
        for l in legs:
            l.rotation_euler.x = 0
        body.location.z = 0
        head.rotation_euler.x = 0
        return
    s = math.sin(phase)
    for i, l in enumerate(legs):
        sign = 1 if i in (0, 3) else -1
        l.rotation_euler.x = sign * s * 0.55
    body.location.z = 0.04 * (1 - math.cos(2 * phase)) / 2
    head.rotation_euler.x = 0.08 * math.sin(phase + 0.5)


def build_king():
    tex = knight_texture("tex_king", (0.98, 0.76, 0.24), (0.8, 0.08, 0.1))
    c = Char("Knight", {"Knight_ArmLeft", "Knight_ArmRight", "Knight_Body", "Knight_Head", "Knight_Cape",
                        "Knight_LegLeft", "Knight_LegRight", "1H_Sword"}, tex)
    build_crown(c)
    return c


# ----------------------------------------------------------------------------- procedural statics


def build_tower():
    wood = M["wood"]
    dark = M["wood_dark"]
    for x in (-1.0, 1.0):
        for y in (-0.8, 0.8):
            cyl((x, y, 1.25), 0.11, 0.1, 2.5, dark, seg=10, bevel=0.0)
    for y in (-0.8, 0.8):
        box((0, y, 1.3), (2.2, 0.08, 0.1), wood, rot=(0, math.radians(52), 0))
        box((0, y, 1.3), (2.2, 0.08, 0.1), wood, rot=(0, math.radians(-52), 0))
    for x in (-1.0, 1.0):
        box((x, 0, 0.7), (0.08, 1.8, 0.1), wood, rot=(math.radians(50), 0, 0))
        box((x, 0, 0.7), (0.08, 1.8, 0.1), wood, rot=(math.radians(-50), 0, 0))
    # Platform planks
    for i in range(7):
        box((-1.2 + i * 0.4, 0, 2.5), (0.37, 2.0, 0.1), wood if i % 2 else dark, bevel=0.015)
    # Railing
    for x in (-1.25, 1.25):
        for y in (-0.95, 0.95):
            cyl((x, y, 2.85), 0.05, 0.04, 0.7, dark, seg=8)
    for y in (-0.95, 0.95):
        box((0, y, 3.15), (2.6, 0.05, 0.08), wood)
    for x in (-1.25, 1.25):
        box((x, 0, 3.15), (0.05, 2.0, 0.08), wood)
    # Flag pole on the back-left corner
    cyl((-1.25, 0.95, 3.6), 0.03, 0.025, 1.3, dark, seg=8)
    box((-0.95, 0.95, 4.05), (0.55, 0.02, 0.3), M["flag"], rot=(0, 0, 0))


def build_stake():
    cyl((0, 0, 0.85), 0.24, 0.2, 1.7, M["wood"], seg=12, bevel=0.03)
    cyl((0, 0, 1.86), 0.2, 0.0, 0.34, M["wood_cut"], seg=12)
    torus((0, 0, 1.25), 0.23, 0.035, M["rope"], seg=20)
    torus((0, 0, 0.5), 0.23, 0.035, M["rope"], seg=20)


def build_coin(flat):
    r = 0.36
    o = cyl((0, 0, 0), r, r, 0.1, M["coin"], seg=32, bevel=0.02)
    torus((0, 0, 0.05), r * 0.75, 0.018, M["coin_dark"], seg=28)
    torus((0, 0, -0.05), r * 0.75, 0.018, M["coin_dark"], seg=28)
    sphere((0, 0, 0.05), (r * 0.3, r * 0.3, 0.02), M["coin_dark"], seg=12)
    sphere((0, 0, -0.05), (r * 0.3, r * 0.3, 0.02), M["coin_dark"], seg=12)
    if flat:
        for ob in list(COLL.objects):
            ob.location.z += 0.05
    else:
        root = empty("coinroot", (0, 0, r + 0.0))
        for ob in list(COLL.objects):
            if ob is not root:
                ob.parent = root
        root.rotation_euler = (math.pi / 2, 0, 0)
        return root


# ----------------------------------------------------------------------------- rendering

SPRITES = {}  # name -> dict(w,h,ax,ay,ppu,cols,rows,cells=[(col,row,path)])


def setup_camera(w, h, ax, ay, ppm):
    SC.render.resolution_x = w
    SC.render.resolution_y = h
    CAM.data.ortho_scale = max(w, h) / ppm
    s = (ay - h / 2) / ppm
    target = Vector(((w / 2 - ax) / ppm, s * math.sin(PITCH), s * math.cos(PITCH)))
    d = 30.0
    CAM.location = target + Vector((0, -d * math.cos(PITCH), d * math.sin(PITCH)))
    CAM.rotation_euler = (math.pi / 2 - PITCH, 0, 0)
    bpy.context.view_layer.update()


def render_cell(name, col, row, w, h, ax, ay, ppm):
    setup_camera(w, h, ax, ay, ppm)
    path = os.path.join(TMP, "%s_%d_%d.png" % (name, row, col))
    SC.render.filepath = path
    bpy.ops.render.render(write_still=True)
    return path


def unit_atlas(name, w, h, ax, ay, ppm, frames, c, extra=None):
    """frames: list of callables fn(c) that pose the character. Renders ROWS directions."""
    ppu = ppm * UNIT_SCALE / UPM
    info = SPRITES[name] = dict(w=w, h=h, ax=ax, ay=ay, ppu=ppu, cols=len(frames), rows=8, cells=[])
    for d in range(ROWS):
        c.face(d)
        for i, fn in enumerate(frames):
            fn(c)
            info["cells"].append((i, d, render_cell(name, i, d, w, h, ax, ay, ppm)))


def static_sprite(name, size_px, ppm, center=(0, 0, 0), k=1.0):
    """Renders a static model at a generous resolution, crops to its alpha box and keeps the ground anchor."""
    w = h = size_px
    setup_camera(w, h, w / 2, h / 2, ppm)
    # move the camera target to center
    CAM.location += Vector(center)
    bpy.context.view_layer.update()
    path = os.path.join(TMP, name + "_0_0.png")
    SC.render.filepath = path
    bpy.ops.render.render(write_still=True)
    uv = world_to_camera_view(SC, CAM, Vector((0, 0, 0)))
    ax = uv.x * w
    ay = (1 - uv.y) * h
    im = Image.open(path).convert("RGBA")
    bbox = im.getchannel("A").point(lambda a: 255 if a > 12 else 0).getbbox() or (0, 0, 1, 1)
    im = im.crop(bbox)
    im.save(path)
    SPRITES[name] = dict(w=im.width, h=im.height, ax=ax - bbox[0], ay=ay - bbox[1], ppu=ppm / UPM * k, cols=1, rows=1,
                         cells=[(0, 0, path)])


def clear_models():
    for o in list(bpy.data.objects):
        if o is CAM or o.type == "LIGHT" or o.name == "ground":
            continue
        bpy.data.objects.remove(o, do_unlink=True)
    for a in list(bpy.data.actions):
        bpy.data.actions.remove(a)
    bpy.data.orphans_purge(do_recursive=True)
    SC.frame_set(1)


def want(name):
    return ONLY is None or name in ONLY


def walk_frames(c, action, n):
    def pose(c, f=0.0):
        c.act(action)
        c.at(f)
    return [(lambda c, f=i / n: pose(c, f)) for i in range(n)]


def action_frames(action, ts):
    def mk(t):
        def pose(c):
            c.act(action)
            c.at(t)
        return pose
    return [mk(t) for t in ts]


def all_sprites():
    t0 = time.time()
    # ---- Archer: idle (aiming), 6 run frames, 3 shoot frames
    if want("archer"):
        clear_models()
        c = build_archer()
        c.scale()
        frames = action_frames("1H_Ranged_Aiming", [0.0]) + walk_frames(c, "Running_A", 6) + \
            action_frames("1H_Ranged_Shoot", [0.15, 0.45, 0.8])
        unit_atlas("archer", 200, 230, 100, 205, 96, frames, c)

    # ---- Red knight: 6 run frames, 3 attack frames
    if want("knight"):
        clear_models()
        c = build_knight()
        c.scale()
        frames = walk_frames(c, "Running_A", 6) + action_frames("1H_Melee_Attack_Chop", [0.3, 0.55, 0.8])
        unit_atlas("knight", 220, 240, 110, 215, 96, frames, c)

    # ---- Boss: 4 walk frames, 3 attack frames, rendered at 64 px/m (big sprite)
    if want("boss"):
        clear_models()
        c = build_boss()
        c.scale(KS * 1.9)
        frames = walk_frames(c, "Walking_A", 4) + action_frames("2H_Melee_Attack_Slice", [0.25, 0.5, 0.8])
        unit_atlas("boss", 280, 290, 140, 262, 64, frames, c)

    # ---- King on his horse: idle + 6 gallop frames
    if want("king"):
        clear_models()
        hroot, hbody, legs, hhead = build_horse()
        c = build_king()
        c.act("Sit_Chair_Pose")
        c.at(0)
        c.rotate_bone("upperleg.l", "Y", -35)
        c.rotate_bone("upperleg.r", "Y", 35)
        c.rotate_bone("upperleg.l", "X", -25)
        c.rotate_bone("upperleg.r", "X", -25)
        c.rotate_bone("lowerleg.l", "X", 40)
        c.rotate_bone("lowerleg.r", "X", 40)
        seat = c.capture(["upperleg.l", "upperleg.r", "lowerleg.l", "lowerleg.r", "hips", "spine", "chest",
                          "upperarm.l", "lowerarm.l", "upperarm.r", "lowerarm.r", "hand.l", "hand.r", "head"])
        c.scale()
        c.root.parent = hbody
        c.root.location = (0, 0.1, 1.02)
        rider = c

        class KingRig:
            def face(self, d):
                hroot.rotation_euler.z = d * math.pi / 4

        def frame(phase, moving):
            def pose(_):
                rider.act("Sit_Chair_Pose")
                rider.at(0)
                rider.apply(seat)
                pose_horse(hbody, legs, hhead, phase, moving)
                bpy.context.view_layer.update()
            return pose

        frames = [frame(0, False)] + [frame(2 * math.pi * i / 6, True) for i in range(6)]
        unit_atlas("king", 300, 300, 150, 268, 96, frames, KingRig())

    # ---- Buildings from the hexagon pack
    if want("castle"):
        clear_models()
        prop(HEX + "buildings/blue/building_barracks_blue.gltf", 4.6)
        static_sprite("castle", 900, 64, center=(0, 0, 3.2))
    if want("house"):
        clear_models()
        prop(HEX + "buildings/blue/building_archeryrange_blue.gltf", 2.9, rot_z=math.radians(10))
        static_sprite("house", 700, 64, center=(0, 0, 2.3))
    if want("tower"):
        clear_models()
        build_tower()
        static_sprite("tower", 700, 96, center=(0, 0, 2.2))
    if want("stake"):
        clear_models()
        build_stake()
        static_sprite("stake", 300, 96, center=(0, 0, 1.0))
    if want("pine0"):
        clear_models()
        prop(HEX + "decoration/nature/tree_single_A.gltf", 2.6)
        static_sprite("pine0", 500, 96, center=(0, 0, 1.5))
    if want("pine1"):
        clear_models()
        prop(HEX + "decoration/nature/tree_single_A.gltf", (2.4, 2.4, 3.0), rot_z=math.radians(140))
        static_sprite("pine1", 500, 96, center=(0, 0, 1.7))
    if want("roundtree"):
        clear_models()
        prop(HEX + "decoration/nature/tree_single_B.gltf", 2.5, rot_z=math.radians(60))
        static_sprite("roundtree", 500, 96, center=(0, 0, 1.5))
    if want("rock0"):
        clear_models()
        prop(HEX + "decoration/nature/rock_single_C.gltf", 4.2)
        static_sprite("rock0", 300, 96, center=(0, 0, 0.4))
    if want("rock1"):
        clear_models()
        prop(HEX + "decoration/nature/rock_single_E.gltf", 3.6, rot_z=math.radians(200))
        static_sprite("rock1", 300, 96, center=(0, 0, 0.3))
    # ---- Coins: 8 spin frames of a standing coin, and one flat coin
    if want("coin_spin"):
        clear_models()
        root = build_coin(False)
        cells = []
        w = h = 90
        for i in range(8):
            root.rotation_euler = (math.pi / 2, 0, i * math.pi / 8)
            bpy.context.view_layer.update()
            cells.append((i, 0, render_cell("coin_spin", i, 0, w, h, 45, 78, 96)))
        SPRITES["coin_spin"] = dict(w=w, h=h, ax=45, ay=78, ppu=3.0, cols=8, rows=1, cells=cells)
    if want("coin_flat"):
        clear_models()
        build_coin(True)
        w, h = 90, 70
        cells = [(0, 0, render_cell("coin_flat", 0, 0, w, h, 45, 40, 96))]
        SPRITES["coin_flat"] = dict(w=w, h=h, ax=45, ay=40, ppu=3.0, cols=1, rows=1, cells=cells)
    print("rendered in %.0fs" % (time.time() - t0))


def pack():
    res_dir = os.path.join(ROOT, "composeApp/src/commonMain/composeResources/drawable")
    os.makedirs(res_dir, exist_ok=True)
    for name, info in SPRITES.items():
        atlas = Image.new("RGBA", (info["w"] * info["cols"], info["h"] * info["rows"]), (0, 0, 0, 0))
        for col, row, path in info["cells"]:
            im = Image.open(path).convert("RGBA")
            atlas.paste(im, (col * info["w"], row * info["h"]))
        atlas.save(os.path.join(res_dir, name + ".png"), optimize=True)
        print("atlas", name, atlas.size)
    kt_path = os.path.join(ROOT, "composeApp/src/commonMain/kotlin/fr/kingshot/game/SpriteData.kt")
    existing = {}
    if os.path.exists(kt_path):
        import re
        for m in re.finditer(r"val (\w+) = SpriteInfo\(([^)]*)\)", open(kt_path).read()):
            existing[m.group(1)] = m.group(2)

    def camel(s):
        parts = s.split("_")
        return parts[0] + "".join(p.capitalize() for p in parts[1:])

    for name, info in SPRITES.items():
        existing[camel(name)] = "%d, %d, %sf, %sf, %sf, %d, %d" % (
            info["w"], info["h"], round(info["ax"], 1), round(info["ay"], 1), round(info["ppu"], 3), info["cols"], info["rows"])
    order = ["archer", "knight", "boss", "king", "castle", "house", "tower", "stake", "pine0", "pine1", "roundtree",
             "rock0", "rock1", "coinSpin", "coinFlat"]
    names = [n for n in order if n in existing] + [n for n in existing if n not in order]
    lines = [
        "package fr.kingshot.game",
        "",
        "// Generated by art/blender/render_sprites.py (Blender + KayKit CC0 models). Do not edit by hand.",
        "",
        "/** Layout of a sprite atlas: cells of [w]x[h] px, [cols] frames per row, one row per direction. */",
        "class SpriteInfo(val w: Int, val h: Int, val ax: Float, val ay: Float, val ppu: Float, val cols: Int, val rows: Int)",
        "",
        "object SpriteData {",
    ] + ["    val %s = SpriteInfo(%s)" % (n, existing[n]) for n in names] + ["}", ""]
    os.makedirs(os.path.dirname(kt_path), exist_ok=True)
    with open(kt_path, "w") as f:
        f.write("\n".join(lines))


all_sprites()
pack()
