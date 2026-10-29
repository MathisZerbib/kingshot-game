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

    # High warm sun from the front-left (short, soft shadows that stay under the model), sky fill, rim from behind.
    sun("key", (math.radians(17), math.radians(-14), math.radians(-30)), 4.0, (1.0, 0.93, 0.8), 24)
    sun("fill", (math.radians(58), math.radians(32), math.radians(40)), 1.5, (0.72, 0.8, 1.0), 25, shadow=False)
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
    M["grass"] = mat("grass", (0.2, 0.42, 0.12), rough=0.9)
    M["grass_light"] = mat("grass_light", (0.35, 0.58, 0.16), rough=0.9)
    M["petal_w"] = mat("petal_w", (0.95, 0.95, 0.9), rough=0.6)
    M["petal_y"] = mat("petal_y", (1.0, 0.8, 0.15), rough=0.6)
    M["petal_p"] = mat("petal_p", (0.95, 0.35, 0.55), rough=0.6)
    M["bush"] = mat("bush", (0.16, 0.4, 0.14), rough=0.85)
    M["bush_light"] = mat("bush_light", (0.3, 0.55, 0.2), rough=0.85)
    M["velvet_blue"] = mat("velvet_blue", (0.08, 0.2, 0.62), rough=0.85)
    M["steel"] = mat("steel", (0.55, 0.58, 0.62), rough=0.35, metal=1.0)
    M["horse_brown"] = mat("horse_brown", (0.42, 0.22, 0.1), rough=0.65, sss=0.1)
    M["horse_grey"] = mat("horse_grey", (0.58, 0.58, 0.6), rough=0.65, sss=0.1)
    M["mane_dark"] = mat("mane_dark", (0.12, 0.08, 0.06), rough=0.8)
    M["wolf"] = mat("wolf", (0.075, 0.065, 0.085), rough=0.95, sss=0.02)
    M["wolf_dark"] = mat("wolf_dark", (0.03, 0.025, 0.035), rough=0.95)
    M["eye_red"] = mat("eye_red", (0.9, 0.15, 0.05), rough=0.3, emit=(1.0, 0.2, 0.05), strength=1.5)
    M["iron"] = mat("iron", (0.3, 0.3, 0.33), rough=0.5, metal=1.0)
    M["club"] = noise_mat("club", (0.2, 0.11, 0.05), (0.32, 0.2, 0.09), scale=4.0, rough=0.75, bump=0.3)
    M["skin_green"] = mat("skin_green", (0.45, 0.62, 0.3), rough=0.7, sss=0.1)


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


BLUE = (0.14, 0.4, 0.92)
GOLD = (0.98, 0.76, 0.24)


def rogue_texture(name, cloth, trim=None):
    """Recolors the green hood/cape (and teal trims) of the rogue palette; the yellow-green details become `trim`."""
    rules = [(135, 195, 0.5, 1.0, 0.15, 1.0, cloth)]
    if trim is not None:
        rules.append((60, 75, 0.5, 1.0, 0.5, 1.0, trim))
    return recolor("rogue_texture.png", rules, name)


def barbarian_texture(name, cloth):
    """Recolors the blue cloth of the barbarian palette."""
    return recolor("barbarian_texture.png", [(195, 215, 0.3, 0.6, 0.3, 0.8, cloth)], name)


def build_archer():
    tex = rogue_texture("tex_archer_blue", BLUE, GOLD)
    c = Char("Rogue_Hooded", {"Rogue_ArmLeft", "Rogue_ArmRight", "Rogue_Body", "Rogue_Head_Hooded", "Rogue_LegLeft",
                              "Rogue_LegRight", "Rogue_Cape"}, tex)
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


def build_crown(c, z=2.12, r=0.4, cushion="velvet"):
    parts = []
    parts.append(torus((0, 0, z), r, 0.05, M["gold"], seg=24))
    parts.append(cyl((0, 0, z + 0.08), r, r, 0.14, M["gold"], seg=24))
    parts.append(sphere((0, 0, z + 0.14), (r * 0.9, r * 0.9, 0.22), M[cushion], seg=16))
    for i in range(6):
        a = i * math.pi / 3 + math.pi / 6
        x, y = math.cos(a) * (r - 0.01), math.sin(a) * (r - 0.01)
        parts.append(cyl((x, y, z + 0.26), 0.07, 0.0, 0.26, M["gold"], seg=8))
        parts.append(sphere((x * 1.02, y * 1.02, z + 0.1), 0.045, M["ruby"], seg=10))
    for p in parts:
        c.attach(p, "head")


def build_horse(coat="horse", mane="mane"):
    """Chibi horse facing -Y, origin at the ground under its belly. Returns (root, body, legs, head)."""
    root = empty("horse")
    body = empty("hbody", (0, 0, 0), root)
    h = M[coat]
    M_mane = M[mane]
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
        sphere((0, -0.3 - 0.4 * t, 1.08 + 0.52 * t), (0.075, 0.11, 0.13), M_mane, body, subsurf=1)
    sphere((0, -0.05, 0.22), (0.1, 0.13, 0.09), M_mane, head, subsurf=1)
    tube([(0, 0.7, 0.92), (0, 0.84, 0.7), (0, 0.8, 0.42)], 0.07, M_mane, body, taper=0.6)
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
    torus((0, -0.3, -0.1), 0.14, 0.016, M["leather"], head, rot=(math.radians(58), 0, 0), seg=16)
    return root, body, legs, head


def pose_horse(body, legs, head, phase, moving):
    if not moving:
        for l in legs:
            l.rotation_euler.x = 0
            l.location.z = 0.66
        body.location.z = 0
        body.rotation_euler.x = 0
        head.rotation_euler.x = 0
        return
    s = math.sin(phase)
    for i, l in enumerate(legs):
        sign = 1 if i in (0, 3) else -1
        l.rotation_euler.x = sign * s * 0.85
        l.location.z = 0.66 + 0.1 * max(0.0, sign * math.cos(phase))  # lift the leg that swings forward
    body.location.z = 0.07 * (1 - math.cos(2 * phase)) / 2
    body.rotation_euler.x = 0.06 * math.sin(2 * phase)
    head.rotation_euler.x = 0.12 * math.sin(phase + 0.5)


def build_king():
    tex = knight_texture("tex_king", (0.98, 0.76, 0.24), (0.8, 0.08, 0.1))
    c = Char("Knight", {"Knight_ArmLeft", "Knight_ArmRight", "Knight_Body", "Knight_Head",
                        "Knight_LegLeft", "Knight_LegRight", "1H_Sword"}, tex)
    build_crown(c)
    # Short red mantle on the shoulders instead of the long cape (which hid the horse from behind)
    c.attach(sphere((0, 0.12, 1.25), (0.52, 0.3, 0.2), M["velvet"], seg=16), "chest")
    return c


def build_hammer():
    """A war hammer along +Z: wooden handle, steel head on top."""
    root = empty("hammer")
    cyl((0, 0, 0.65), 0.05, 0.045, 1.3, M["wood"], root, seg=10)
    box((0, 0, 1.32), (0.64, 0.3, 0.3), M["steel"], root, bevel=0.04)
    box((0, 0, 1.32), (0.7, 0.12, 0.12), M["gold_dark"], root, bevel=0.02)
    torus((0, 0, 0.22), 0.05, 0.014, M["leather"], root, seg=12)
    return root


def build_king_warrior():
    """Gold plate armour with blue details, blue mantle, sword: tanky melee king."""
    tex = knight_texture("tex_king_w", GOLD, BLUE)
    c = Char("Knight", {"Knight_ArmLeft", "Knight_ArmRight", "Knight_Body", "Knight_Head",
                        "Knight_LegLeft", "Knight_LegRight", "1H_Sword"}, tex)
    build_crown(c)
    c.attach(sphere((0, 0.12, 1.25), (0.52, 0.3, 0.2), M["velvet_blue"], seg=16), "chest")
    c.horse = ("horse", "mane")
    return c


def build_king_ranger():
    """Blue hooded ranger with a gold-trimmed crown over the hood and a longbow: fast, fragile, long range."""
    tex = rogue_texture("tex_king_r", BLUE, GOLD)
    c = Char("Rogue_Hooded", {"Rogue_ArmLeft", "Rogue_ArmRight", "Rogue_Body", "Rogue_Head_Hooded", "Rogue_LegLeft",
                              "Rogue_LegRight"}, tex)
    build_crown(c, z=2.2, r=0.42, cushion="velvet_blue")
    quiver = prop(ACC + "quiver.gltf", 1.0)
    quiver.location = (-0.2, 0.42, 1.15)
    quiver.rotation_euler = (math.radians(20), math.radians(-25), math.radians(15))
    c.attach(quiver, "chest")

    def weapon():
        bow = build_bow()
        hand = c.bone_world("hand.r")
        bow.location = hand + Vector((0.0, -0.08, 0))
        bow.rotation_euler = (math.radians(-6), 0, 0)
        c.attach_posed(bow, "hand.r")
    c.weapon = weapon
    c.horse = ("horse_brown", "mane_dark")
    return c


def build_king_builder():
    """Broad barbarian in blue cloth with a crown and a big war hammer: cheap buildings, repairs."""
    tex = barbarian_texture("tex_king_b", BLUE)
    c = Char("Barbarian", {"Barbarian_ArmLeft", "Barbarian_ArmRight", "Barbarian_Body", "Barbarian_Head",
                           "Barbarian_LegLeft", "Barbarian_LegRight"}, tex)
    build_crown(c, z=2.1, r=0.42)
    c.attach(sphere((0, 0.12, 1.22), (0.56, 0.32, 0.2), M["leather"], seg=16), "chest")

    def weapon():
        h = build_hammer()
        hand = c.bone_world("hand.r")
        h.location = hand + Vector((0.0, -0.02, -0.3))
        h.rotation_euler = (math.radians(-38), 0, 0)
        c.attach_posed(h, "hand.r")
    c.weapon = weapon
    c.horse = ("horse_grey", "mane")
    return c


KINGS = {"king_warrior": build_king_warrior, "king_ranger": build_king_ranger, "king_builder": build_king_builder}
SEAT_BONES = ["upperleg.l", "upperleg.r", "lowerleg.l", "lowerleg.r", "hips", "spine", "chest",
              "upperarm.l", "lowerarm.l", "upperarm.r", "lowerarm.r", "hand.l", "hand.r", "head"]


def seat_rider(c):
    """Puts the rider in the saddle pose and returns the captured bone pose."""
    c.act("Sit_Chair_Pose")
    c.at(0)
    c.rotate_bone("upperleg.l", "Y", -35)
    c.rotate_bone("upperleg.r", "Y", 35)
    c.rotate_bone("upperleg.l", "X", -25)
    c.rotate_bone("upperleg.r", "X", -25)
    c.rotate_bone("lowerleg.l", "X", 40)
    c.rotate_bone("lowerleg.r", "X", 40)
    seat = c.capture(SEAT_BONES)
    if getattr(c, "weapon", None):
        c.weapon()
    return seat


def mounted(build_rider):
    """Builds horse + seated rider. Returns (horse root, rider, pose(phase, moving) callable)."""
    c = build_rider()
    hroot, hbody, legs, hhead = build_horse(*getattr(c, "horse", ("horse", "mane")))
    seat = seat_rider(c)
    c.scale()
    c.root.parent = hbody
    c.root.location = (0, 0.1, 0.8)

    def pose(phase, moving):
        c.act("Sit_Chair_Pose")
        c.at(0)
        c.apply(seat)
        pose_horse(hbody, legs, hhead, phase, moving)
        bpy.context.view_layer.update()
    return hroot, c, pose


def king_atlas(name, build_rider):
    clear_models()
    hroot, c, pose = mounted(build_rider)

    class KingRig:
        def face(self, d):
            hroot.rotation_euler.z = d * math.pi / 4

    def frame(phase, moving):
        return lambda _: pose(phase, moving)

    frames = [frame(0, False)] + [frame(2 * math.pi * i / 6, True) for i in range(6)]
    unit_atlas(name, 300, 300, 150, 268, 96, frames, KingRig())


def portrait(name, build_rider, px=256):
    """Head-and-shoulders bust of the class hero, slightly turned, on a transparent background."""
    clear_models()
    c = build_rider()
    c.act("Idle")
    c.at(0.3)
    c.scale()
    c.root.rotation_euler.z = math.radians(-28)
    bpy.context.view_layer.update()
    SC.render.resolution_x = SC.render.resolution_y = px
    CAM.data.ortho_scale = 1.25
    p = math.radians(8)
    target = Vector((0.0, 0.0, 1.22))
    CAM.location = target + Vector((0, -30 * math.cos(p), 30 * math.sin(p)))
    CAM.rotation_euler = (math.pi / 2 - p, 0, 0)
    bpy.context.view_layer.update()
    path = os.path.join(TMP, name + "_0_0.png")
    SC.render.filepath = path
    bpy.ops.render.render(write_still=True)
    clean_alpha(path)
    SPRITES[name] = dict(w=px, h=px, ax=px / 2, ay=px / 2, ppu=1.0, cols=1, rows=1, cells=[(0, 0, path)])


def build_palisade(gate=None):
    """A 2.8 m wooden palisade segment along X (same footprint as wall_stone). gate: None, 'closed' or 'open'."""
    L = 2.8
    wood, dark, cut = M["wood"], M["wood_dark"], M["wood_cut"]

    def log(x, h, r=0.15, parent=None, i=0):
        cyl((x, 0, h / 2), r, r * 0.9, h, wood if i % 2 else dark, parent, seg=10)
        cyl((x, 0, h + 0.12), r * 0.9, 0.0, 0.26, cut, parent, seg=10)

    if gate is None:
        n = 9
        for i in range(n):
            x = -L / 2 + 0.155 + i * (L - 0.31) / (n - 1)
            log(x, 1.45 + 0.06 * ((i * 7) % 3), i=i)
        for z in (0.5, 1.1):
            box((0, 0.17, z), (L, 0.09, 0.14), dark, bevel=0.01)
        for x in (-L / 2 + 0.155, L / 2 - 0.155):
            for z in (0.5, 1.1):
                torus((x, 0.03, z), 0.2, 0.028, M["rope"], rot=(0, math.pi / 2, 0), seg=14)
        return
    for sx in (-1, 1):
        log(sx * (L / 2 - 0.19), 2.05, r=0.19)
    box((0, 0, 2.22), (L, 0.34, 0.2), dark, bevel=0.02)
    box((0, 0.02, 2.42), (0.9, 0.3, 0.2), wood, bevel=0.02)
    for sx in (-1, 1):
        hinge = empty("hinge", (sx * (L / 2 - 0.4), 0, 0))
        leaf = empty("leaf", (0, 0, 0), hinge)
        for j in range(4):
            log(-sx * (0.12 + j * 0.26), 1.45, r=0.125, parent=leaf, i=j)
        for z in (0.45, 1.05):
            box((-sx * 0.5, 0.14, z), (1.02, 0.06, 0.12), dark, leaf)
        box((-sx * 0.5, 0.15, 0.75), (1.05, 0.05, 0.1), dark, leaf, rot=(0, 0, sx * math.radians(32)))
        if gate == "open":
            hinge.rotation_euler.z = sx * math.radians(105)


# ----------------------------------------------------------------------------- v5: new enemies, units and buildings


def build_wolf():
    """Chibi grey wolf facing -Y, origin under its belly. Returns (root, body, legs, head)."""
    root = empty("wolf")
    root.scale = (1.6, 1.6, 1.6)
    body = empty("wbody", (0, 0, 0), root)
    fur, dark = M["wolf"], M["wolf_dark"]
    sphere((0, 0.05, 0.42), (0.2, 0.42, 0.2), fur, body, subsurf=1)
    sphere((0, -0.3, 0.46), (0.2, 0.22, 0.2), fur, body, subsurf=1)
    sphere((0, 0.38, 0.42), (0.18, 0.2, 0.18), dark, body, subsurf=1)
    head = empty("whead", (0, -0.5, 0.58), body)
    sphere((0, 0, 0), (0.18, 0.19, 0.17), fur, head, subsurf=1)
    box((0, -0.2, -0.05), (0.15, 0.24, 0.13), dark, head, bevel=0.04, subsurf=1, smooth=True)
    sphere((0, -0.32, -0.01), 0.045, M["eye"], head)
    for sx in (-1, 1):
        sphere((sx * 0.085, -0.11, 0.06), 0.032, M["eye_red"], head)
        cyl((sx * 0.1, 0.04, 0.2), 0.055, 0.0, 0.18, dark, head, rot=(math.radians(-15), math.radians(sx * 22), 0), seg=8)
    for i in range(4):
        tt = i / 3
        sphere((0, 0.5 + 0.36 * tt, 0.44 + 0.16 * tt), (0.085 - 0.02 * tt, 0.12, 0.085 - 0.02 * tt), dark, body, subsurf=1)
    legs = []
    for x, y in ((-0.11, -0.26), (0.11, -0.26), (-0.11, 0.28), (0.11, 0.28)):
        piv = empty("wleg", (x, y, 0.32), body)
        cyl((0, 0, -0.15), 0.055, 0.045, 0.32, fur, piv, subsurf=1)
        sphere((0, -0.02, -0.3), (0.06, 0.07, 0.04), dark, piv)
        legs.append(piv)
    return root, body, legs, head


def pose_wolf(body, legs, head, phase, moving, bite=0.0):
    if moving:
        s = math.sin(phase)
        for i, l in enumerate(legs):
            sign = 1 if i in (0, 3) else -1
            l.rotation_euler.x = sign * s * 1.0
            l.location.z = 0.32 + 0.06 * max(0.0, sign * math.cos(phase))
        body.location.z = 0.06 * (1 - math.cos(2 * phase)) / 2
        body.rotation_euler.x = 0.08 * math.sin(2 * phase)
    else:
        for l in legs:
            l.rotation_euler.x = 0
            l.location.z = 0.32
        body.location.z = 0
        body.rotation_euler.x = 0
    head.rotation_euler.x = math.radians(-35) * bite
    head.location.y = -0.5 - 0.12 * bite
    head.location.z = 0.58 - 0.08 * bite


def build_ram():
    """Battering ram: wooden cart with a roof and a hanging log; +Y is the back. Returns (root, log pivot)."""
    root = empty("ram")
    wood, dark = M["wood"], M["wood_dark"]
    for sx in (-1, 1):
        box((sx * 0.55, 0, 0.42), (0.1, 2.3, 0.1), dark, root)
        box((sx * 0.55, 0, 1.55), (0.1, 2.3, 0.1), dark, root)
        for y in (-1.0, 0.0, 1.0):
            cyl((sx * 0.55, y, 1.0), 0.055, 0.05, 1.2, dark, root, seg=8)
        box((sx * 0.4, 0.15, 1.78), (0.9, 1.7, 0.05), wood, root, rot=(0, sx * math.radians(28), 0))
        for y in (-0.8, 0.8):
            cyl((sx * 0.68, y, 0.3), 0.3, 0.3, 0.12, dark, root, rot=(0, math.pi / 2, 0), seg=16)
            cyl((sx * 0.68, y, 0.3), 0.08, 0.08, 0.16, M["iron"], root, rot=(0, math.pi / 2, 0), seg=10)
    for y in (-1.0, 1.0):
        box((0, y, 1.55), (1.2, 0.1, 0.1), dark, root)
    box((0, 0.15, 2.0), (0.2, 1.7, 0.08), dark, root)
    log = empty("ramlog", (0, 0, 1.55), root)
    cyl((0, 0, -0.6), 0.17, 0.17, 2.7, wood, log, rot=(math.pi / 2, 0, 0), seg=14)
    cyl((0, -1.42, -0.6), 0.2, 0.15, 0.36, M["iron"], log, rot=(math.pi / 2, 0, 0), seg=12)
    torus((0, -1.1, -0.6), 0.19, 0.03, M["iron"], log, rot=(math.pi / 2, 0, 0), seg=16)
    for y in (-0.6, 0.6):
        cyl((0, y, -0.3), 0.022, 0.022, 0.6, M["rope"], log, seg=6)
    return root, log


def build_spear():
    root = empty("spear")
    cyl((0, 0, 0.6), 0.03, 0.028, 1.7, M["wood"], root, seg=8)
    cyl((0, 0, 1.6), 0.06, 0.0, 0.3, M["steel"], root, seg=8)
    torus((0, 0, 1.42), 0.04, 0.012, M["gold_dark"], root, seg=10)
    return root


def build_club():
    root = empty("club")
    cyl((0, 0, 0.55), 0.06, 0.17, 1.3, M["club"], root, seg=12, bevel=0.03)
    for i in range(6):
        a = i * math.pi / 3
        cyl((math.cos(a) * 0.16, math.sin(a) * 0.16, 1.0), 0.04, 0.0, 0.16, M["iron"], root,
            rot=(0, math.pi / 2, a), seg=6)
    torus((0, 0, 0.1), 0.065, 0.015, M["leather"], root, seg=10)
    return root


def hold(c, obj, bone="hand.r", offset=(0, 0, 0), rot=(0, 0, 0), action="Idle"):
    """Attaches a prop to a hand in the given action's first frame; it then follows every animation."""
    c.act(action)
    c.at(0.0)
    obj.location = c.bone_world(bone) + Vector(offset)
    obj.rotation_euler = rot
    c.attach_posed(obj, bone)


def build_enemy_archer():
    rules = [(135, 195, 0.5, 1.0, 0.15, 1.0, (0.75, 0.1, 0.1)), (60, 75, 0.5, 1.0, 0.5, 1.0, (0.15, 0.12, 0.12))]
    tex = recolor("rogue_texture.png", rules, "tex_archer_red")
    c = Char("Rogue_Hooded", {"Rogue_ArmLeft", "Rogue_ArmRight", "Rogue_Body", "Rogue_Head_Hooded", "Rogue_LegLeft",
                              "Rogue_LegRight", "Rogue_Cape"}, tex)
    hold(c, build_bow(), offset=(0, -0.08, 0), rot=(math.radians(-6), 0, 0), action="1H_Ranged_Aiming")
    return c


def build_shieldman():
    tex = knight_texture("tex_shieldman", (0.62, 0.1, 0.08), (0.2, 0.18, 0.2), (0.2, 0.17, 0.18))
    c = Char("Knight", {"Knight_ArmLeft", "Knight_ArmRight", "Knight_Body", "Knight_Head", "Knight_Helmet",
                        "Knight_LegLeft", "Knight_LegRight", "1H_Sword"}, tex)
    shield = prop(ACC + "shield_square_color.gltf", 1.15)
    retexture([o for o in COLL.objects if o.type == "MESH" and o.parent is shield], tex)
    sr = [float(v) for v in os.environ.get("SHIELD_ROT", "0,0,0").split(",")]
    so = [float(v) for v in os.environ.get("SHIELD_OFF", "0,-0.1,0.05").split(",")]
    hold(c, shield, bone="hand.l", offset=tuple(so), rot=tuple(math.radians(v) for v in sr), action="Idle")
    return c


def build_ogre():
    rules = [(15, 35, 0.25, 0.5, 0.75, 1.0, (0.45, 0.62, 0.3)),   # skin -> green
             (195, 215, 0.3, 0.6, 0.3, 0.8, (0.35, 0.15, 0.1))]   # blue cloth -> dark leather
    tex = recolor("barbarian_texture.png", rules, "tex_ogre")
    c = Char("Barbarian", {"Barbarian_ArmLeft", "Barbarian_ArmRight", "Barbarian_Body", "Barbarian_Head",
                           "Barbarian_LegLeft", "Barbarian_LegRight"}, tex)
    for sx in (-1, 1):
        h = empty("tusk", (sx * 0.12, -0.42, 1.78), rot=(math.radians(-20), 0, 0))
        cyl((0, 0, 0.08), 0.04, 0.0, 0.18, M["bone"] if "bone" in M else mat("bone", (0.85, 0.8, 0.68), rough=0.5), h, seg=8)
        c.attach(h, "head")
    hold(c, build_club(), offset=(0, -0.05, -0.2), rot=(math.radians(-30), 0, 0), action="2H_Melee_Idle")
    return c


def build_dark_lord():
    rules = [(178, 196, 0.5, 1.0, 0.3, 1.0, (0.2, 0.05, 0.3)),   # teal robe -> deep purple
             (8, 26, 0.6, 1.0, 0.6, 1.0, (0.65, 0.05, 0.08)),    # orange trims -> crimson
             (15, 35, 0.25, 0.45, 0.8, 1.0, (0.55, 0.6, 0.62))]  # skin -> pale grey
    tex = recolor("mage_texture.png", rules, "tex_dark_lord")
    c = Char("Mage", {"Mage_ArmLeft", "Mage_ArmRight", "Mage_Body", "Mage_Head", "Mage_LegLeft", "Mage_LegRight",
                      "2H_Staff", "Mage_Cape"}, tex)
    for sx in (-1, 1):
        sphere((sx * 0.11, -0.38, 1.42), 0.045, M["eye_red"], seg=10)
    for o in list(COLL.objects):
        if o.type == "MESH" and o.name.startswith("Sphere") and o.parent is None:
            c.attach(o, "head")
    crown = empty("ironcrown", (0, 0, 1.7))
    torus((0, 0, 0), 0.34, 0.045, M["iron"], crown, seg=20)
    for i in range(5):
        a = i * 2 * math.pi / 5 + math.pi / 2
        cyl((math.cos(a) * 0.34, math.sin(a) * 0.34, 0.12), 0.05, 0.0, 0.28, M["iron"], crown, seg=6)
    c.attach(crown, "head")
    return c


def build_lancer():
    tex = knight_texture("tex_lancer", (0.3, 0.42, 0.9), GOLD, (0.22, 0.2, 0.24))
    c = Char("Knight", {"Knight_ArmLeft", "Knight_ArmRight", "Knight_Body", "Knight_Head", "Knight_Helmet",
                        "Knight_LegLeft", "Knight_LegRight"}, tex)
    shield = prop(ACC + "shield_round_color.gltf", 1.3)
    hold(c, shield, bone="hand.l", offset=(0.0, -0.16, 0.08), rot=(0, 0, math.radians(90)), action="Idle")
    hold(c, build_spear(), offset=(0, -0.02, -0.5), rot=(math.radians(-12), 0, 0), action="Idle")
    return c


def build_archer_elite():
    tex = rogue_texture("tex_archer_elite", (0.1, 0.3, 0.8), GOLD)
    c = Char("Rogue_Hooded", {"Rogue_ArmLeft", "Rogue_ArmRight", "Rogue_Body", "Rogue_Head_Hooded", "Rogue_LegLeft",
                              "Rogue_LegRight", "Rogue_Cape"}, tex)
    c.act("1H_Ranged_Aiming")
    c.at(0.0)
    bow = build_bow()
    bow.location = c.bone_world("hand.r") + Vector((0.0, -0.08, 0))
    bow.rotation_euler = (math.radians(-6), 0, 0)
    c.attach_posed(bow, "hand.r")
    quiver = prop(ACC + "quiver.gltf", 1.0)
    quiver.location = (-0.2, 0.42, 1.15)
    quiver.rotation_euler = (math.radians(20), math.radians(-25), math.radians(15))
    c.attach(quiver, "chest")
    plume = empty("plume", (0, 0.1, 2.15), rot=(math.radians(35), 0, 0))
    tube([(0, 0, 0), (0, 0.05, 0.22), (0, 0.18, 0.4)], 0.05, M["flag"], plume, taper=0.4)
    c.attach(plume, "head")
    return c


def build_ballista():
    """Tower-top ballista aimed to the front-left (-X, -Y): post, swivel, bow limbs, string and a loaded bolt."""
    root = empty("ballista", rot=(0, 0, math.radians(35)))
    wood, dark, steel = M["wood"], M["wood_dark"], M["steel"]
    cyl((0, 0, 0.25), 0.12, 0.1, 0.5, dark, root, seg=10)
    box((0, 0, 0.06), (0.9, 0.9, 0.12), wood, root, bevel=0.02)
    box((0, 0.1, 0.62), (0.16, 1.5, 0.12), wood, root, rot=(math.radians(-12), 0, 0), bevel=0.02)
    for sx in (-1, 1):
        box((sx * 0.45, -0.5, 0.72), (0.9, 0.07, 0.1), dark, root, rot=(0, 0, sx * math.radians(-18)), bevel=0.01)
    cyl((0, -0.5 - 0.08, 0.72), 0.012, 0.012, 1.75, M["string"], root, rot=(0, math.pi / 2, 0), seg=6)
    cyl((0, -0.1, 0.7), 0.03, 0.03, 1.3, steel, root, rot=(math.radians(78), 0, 0), seg=8)
    cyl((0, -0.72, 0.84), 0.06, 0.0, 0.22, steel, root, rot=(math.radians(78), 0, 0), seg=8)
    cyl((0, 0.55, 0.55), 0.1, 0.1, 0.1, steel, root, rot=(0, math.pi / 2, 0), seg=12)
    return root


V5_NAMES = ("wolf", "enemy_archer", "shieldman", "ram", "ogre", "dark_lord", "lancer", "archer_elite",
            "farm", "barracks", "ballista_top", "mine", "boulder",
            "market", "blacksmith", "church", "stable", "watermill", "well", "lumbermill")


def v5_sprites():
    if want("wolf"):
        clear_models()
        root, body, legs, head = build_wolf()

        class Rig:
            def face(self, d):
                root.rotation_euler.z = d * math.pi / 4

        def frame(phase, moving, bite=0.0):
            return lambda _: pose_wolf(body, legs, head, phase, moving, bite) or bpy.context.view_layer.update()
        frames = [frame(2 * math.pi * i / 6, True) for i in range(6)] + [frame(0.5, False, 0.5), frame(0.5, False, 1.0)]
        unit_atlas("wolf", 210, 180, 105, 155, 96, frames, Rig())
    if want("enemy_archer"):
        clear_models()
        c = build_enemy_archer()
        c.scale()
        frames = action_frames("1H_Ranged_Aiming", [0.0]) + walk_frames(c, "Walking_A", 4) + \
            action_frames("1H_Ranged_Shoot", [0.15, 0.45, 0.8])
        unit_atlas("enemy_archer", 200, 230, 100, 205, 96, frames, c)
    if want("shieldman"):
        clear_models()
        c = build_shieldman()
        c.scale()
        frames = walk_frames(c, "Walking_A", 6) + action_frames("1H_Melee_Attack_Chop", [0.3, 0.55, 0.8])
        unit_atlas("shieldman", 240, 240, 120, 215, 96, frames, c)
    if want("ram"):
        clear_models()
        root, log = build_ram()

        class Rig:
            def face(self, d):
                root.rotation_euler.z = d * math.pi / 4

        def frame(a):
            def pose(_):
                log.rotation_euler.x = math.radians(a)
                bpy.context.view_layer.update()
            return pose
        unit_atlas("ram", 300, 280, 150, 245, 96, [frame(a) for a in (0, 9, 0, -9)], Rig())
    if want("ogre"):
        clear_models()
        c = build_ogre()
        c.scale(KS * 1.9)
        frames = walk_frames(c, "Walking_A", 4) + action_frames("2H_Melee_Attack_Chop", [0.25, 0.5, 0.8])
        unit_atlas("ogre", 300, 300, 150, 272, 64, frames, c)
    if want("dark_lord"):
        clear_models()
        c = build_dark_lord()
        c.scale(KS * 1.75)
        frames = walk_frames(c, "Walking_A", 4) + action_frames("Spellcast_Shoot", [0.2, 0.5, 0.8])
        unit_atlas("dark_lord", 300, 320, 150, 290, 64, frames, c)
    if want("lancer"):
        clear_models()
        c = build_lancer()
        c.scale()
        frames = action_frames("Idle", [0.0]) + walk_frames(c, "Running_A", 6) + \
            action_frames("1H_Melee_Attack_Stab", [0.3, 0.55, 0.8])
        unit_atlas("lancer", 240, 250, 120, 225, 96, frames, c)
    if want("archer_elite"):
        clear_models()
        c = build_archer_elite()
        c.scale(KS * 1.12)
        frames = action_frames("1H_Ranged_Aiming", [0.0]) + walk_frames(c, "Running_A", 6) + \
            action_frames("1H_Ranged_Shoot", [0.15, 0.45, 0.8])
        unit_atlas("archer_elite", 220, 250, 110, 225, 96, frames, c)
    for name, path, scale, rot, h in (
            ("farm", "buildings/blue/building_windmill_blue.gltf", 2.6, 20, 2.6),
            ("barracks", "buildings/blue/building_tavern_blue.gltf", 2.7, 0, 2.0),
            ("mine", "buildings/blue/building_mine_blue.gltf", 2.6, 10, 1.6)):
        if want(name):
            clear_models()
            prop(HEX + path, scale, rot_z=math.radians(rot))
            static_sprite(name, 700, 64, center=(0, 0, h))
    for name, path, scale, rot, h in (
            ("market", "buildings/blue/building_market_blue.gltf", 2.6, 15, 1.8),
            ("blacksmith", "buildings/blue/building_blacksmith_blue.gltf", 2.6, -10, 1.9),
            ("church", "buildings/blue/building_church_blue.gltf", 2.5, 10, 2.6),
            ("stable", "buildings/blue/building_home_B_blue.gltf", 2.6, 20, 1.8),
            ("watermill", "buildings/blue/building_watermill_blue.gltf", 2.5, 0, 2.0),
            ("well", "buildings/blue/building_well_blue.gltf", 2.4, 0, 1.2),
            ("lumbermill", "buildings/blue/building_lumbermill_blue.gltf", 2.6, -15, 1.8)):
        if want(name):
            clear_models()
            prop(HEX + path, scale, rot_z=math.radians(rot))
            static_sprite(name, 700, 64, center=(0, 0, h))
    if want("ballista_top"):
        clear_models()
        build_ballista()
        static_sprite("ballista_top", 400, 96, center=(0, 0, 0.5))
    if want("boulder"):
        clear_models()
        prop(HEX + "buildings/neutral/projectile_catapult.gltf", 2.0)
        static_sprite("boulder", 200, 96, center=(0, 0, 0.3))


V4_NAMES = ("king_warrior", "king_ranger", "king_builder", "portrait_warrior", "portrait_ranger", "portrait_builder",
            "wall_wood", "wall_wood_v", "gate_wood_closed", "gate_wood_open", "gate_wood_closed_v", "gate_wood_open_v")


def v4_sprites():
    for name, builder in KINGS.items():
        if want(name):
            king_atlas(name, builder)
    for cls, builder in (("warrior", build_king_warrior), ("ranger", build_king_ranger), ("builder", build_king_builder)):
        if want("portrait_" + cls):
            portrait("portrait_" + cls, builder)
    for suffix, rot in (("", 0.0), ("_v", math.pi / 2)):
        if want("wall_wood" + suffix):
            clear_models()
            root = empty("wall", rot=(0, 0, rot))
            build_palisade()
            for o in list(COLL.objects):
                if o.parent is None and o is not root:
                    o.parent = root
            static_sprite("wall_wood" + suffix, 400, 96, center=(0, 0, 0.8))
        for state in ("closed", "open"):
            if want("gate_wood_%s%s" % (state, suffix)):
                clear_models()
                root = empty("wall", rot=(0, 0, rot))
                build_palisade(state)
                for o in list(COLL.objects):
                    if o.parent is None and o is not root:
                        o.parent = root
                static_sprite("gate_wood_%s%s" % (state, suffix), 400, 96, center=(0, 0, 1.0))


def logo(out_png):
    """Hero shot of the warrior king at a gallop, 3/4 view, low camera, for the app icon (transparent PNG)."""
    clear_models()
    hroot, c, pose = mounted(build_king_warrior)
    pose(1.1, True)
    hroot.rotation_euler.z = math.radians(-32)
    bpy.context.view_layer.update()
    px = 1024
    SC.render.resolution_x = SC.render.resolution_y = px
    CAM.data.ortho_scale = 3.3
    p = math.radians(16)
    target = Vector((0.0, 0.0, 1.35))
    CAM.location = target + Vector((0, -30 * math.cos(p), 30 * math.sin(p)))
    CAM.rotation_euler = (math.pi / 2 - p, 0, 0)
    bpy.context.view_layer.update()
    SC.render.filepath = out_png
    bpy.ops.render.render(write_still=True)
    clean_alpha(out_png)


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


def clean_alpha(path):
    """Removes the faint veil the shadow catcher leaves on the whole cell and lightens the shadows."""
    im = Image.open(path).convert("RGBA")
    a = np.asarray(im).astype(np.float32)
    alpha = a[..., 3]
    lum = a[..., :3].max(-1)
    shadow = (lum < 40) & (alpha < 250)
    alpha = np.where(alpha < 26, 0, (alpha - 26) * (255.0 / 229.0))
    alpha = np.where(shadow, alpha * 0.78, alpha)
    a[..., 3] = np.clip(alpha, 0, 255)
    Image.fromarray(a.astype(np.uint8)).save(path)


RESUME = os.environ.get("KINGSHOT_RESUME") == "1"


def cell_done(path):
    """With KINGSHOT_RESUME=1, cells already rendered in TMP are kept (lets an interrupted run continue)."""
    if not RESUME or not os.path.exists(path):
        return False
    try:
        Image.open(path).verify()
        return True
    except Exception:
        return False


def render_cell(name, col, row, w, h, ax, ay, ppm):
    setup_camera(w, h, ax, ay, ppm)
    path = os.path.join(TMP, "%s_%d_%d.png" % (name, row, col))
    if cell_done(path):
        return path
    SC.render.filepath = path
    bpy.ops.render.render(write_still=True)
    clean_alpha(path)
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
    clean_alpha(path)
    uv = world_to_camera_view(SC, CAM, Vector((0, 0, 0)))
    ax = uv.x * w
    ay = (1 - uv.y) * h
    im = Image.open(path).convert("RGBA")
    bbox = im.getchannel("A").point(lambda a: 255 if a > 2 else 0).getbbox() or (0, 0, 1, 1)
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
        c.root.location = (0, 0.1, 0.8)
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
    # ---- Age 2/3 fortifications and buildings (hexagon pack)
    WS = 1.4  # wall scale: one segment = 2.8 m = 90 units
    for suffix, rot in (("", 0.0), ("_v", math.pi / 2)):
        if want("wall_stone" + suffix):
            clear_models()
            prop(HEX + "buildings/neutral/wall_straight.gltf", WS, rot_z=rot)
            static_sprite("wall_stone" + suffix, 400, 96, center=(0, 0, 0.7))
        for state in ("closed", "open"):
            if want("gate_%s%s" % (state, suffix)):
                clear_models()
                root = prop(HEX + "buildings/neutral/wall_straight_gate.gltf", WS, rot_z=rot)
                for o in list(COLL.objects):
                    if "door_left" in o.name or "door_right" in o.name:
                        sx = 1 if "left" in o.name else -1
                        hinge = empty("hinge", (sx * 0.675, 0, 0), root)
                        o.parent = hinge
                        o.location = (sx * 0.45 - sx * 0.675, 0, 0)
                        if state == "open":
                            hinge.rotation_euler.z = sx * math.radians(105)
                static_sprite("gate_%s%s" % (state, suffix), 400, 96, center=(0, 0, 0.8))
    if want("tower_stone"):
        clear_models()
        prop(HEX + "buildings/blue/building_tower_base_blue.gltf", 1.75, rot_z=math.radians(15))
        static_sprite("tower_stone", 600, 96, center=(0, 0, 1.4))
    if want("tower_royal"):
        clear_models()
        prop(HEX + "buildings/blue/building_tower_base_blue.gltf", (2.0, 2.0, 2.1), rot_z=math.radians(15))
        cyl((-0.7, 0.6, 3.9), 0.035, 0.03, 1.6, M["wood_dark"], seg=8)
        box((-0.35, 0.6, 4.45), (0.65, 0.025, 0.36), M["flag"])
        torus((0, 0, 3.02), 0.98, 0.05, M["gold"], seg=32)
        static_sprite("tower_royal", 700, 96, center=(0, 0, 1.8))
    if want("castle_royal"):
        clear_models()
        prop(HEX + "buildings/blue/building_castle_blue.gltf", 3.0)
        static_sprite("castle_royal", 1000, 64, center=(0, 0, 5.0))
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


def ground_mat(name, kind):
    """Top-down ground materials: layered noise for color, bump for relief."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    b = nt.nodes["Principled BSDF"]
    b.inputs["Roughness"].default_value = 0.95
    tc = nt.nodes.new("ShaderNodeTexCoord")
    big = nt.nodes.new("ShaderNodeTexNoise")
    big.inputs["Scale"].default_value = 0.6
    big.inputs["Detail"].default_value = 4
    nt.links.new(tc.outputs["Object"], big.inputs["Vector"])
    fine = nt.nodes.new("ShaderNodeTexNoise")
    fine.inputs["Scale"].default_value = {"grass": 28.0, "road": 14.0, "cliff": 6.0}[kind]
    fine.inputs["Detail"].default_value = 12
    fine.inputs["Roughness"].default_value = 0.75
    nt.links.new(tc.outputs["Object"], fine.inputs["Vector"])
    vor = nt.nodes.new("ShaderNodeTexVoronoi")
    vor.inputs["Scale"].default_value = {"grass": 40.0, "road": 70.0, "cliff": 9.0}[kind]
    nt.links.new(tc.outputs["Object"], vor.inputs["Vector"])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    if kind == "grass":
        c1, c2, c3 = (0.06, 0.2, 0.035), (0.14, 0.36, 0.07), (0.3, 0.5, 0.11)
    elif kind == "road":
        c1, c2, c3 = (0.2, 0.12, 0.05), (0.36, 0.24, 0.12), (0.52, 0.38, 0.2)
    else:
        c1, c2, c3 = (0.1, 0.1, 0.12), (0.24, 0.24, 0.27), (0.42, 0.42, 0.45)
    ramp.color_ramp.elements[0].color = (*c1, 1)
    ramp.color_ramp.elements[1].color = (*c3, 1)
    mid = ramp.color_ramp.elements.new(0.5)
    mid.color = (*c2, 1)
    mixf = nt.nodes.new("ShaderNodeMath")
    mixf.operation = "MULTIPLY_ADD"
    nt.links.new(big.outputs["Fac"], mixf.inputs[0])
    mixf.inputs[1].default_value = 0.6
    nt.links.new(fine.outputs["Fac"], mixf.inputs[2])
    # Noise Fac only spans ~0.35..0.65: stretch it to the full ramp
    scale = nt.nodes.new("ShaderNodeMapRange")
    scale.inputs["From Min"].default_value = 0.62
    scale.inputs["From Max"].default_value = 0.98
    nt.links.new(mixf.outputs[0], scale.inputs["Value"])
    if kind != "grass":
        # rocks and pebbles also show in the color, not only in the bump
        vc = nt.nodes.new("ShaderNodeMath")
        vc.operation = "MULTIPLY_ADD"
        nt.links.new(vor.outputs["Distance"], vc.inputs[0])
        vc.inputs[1].default_value = 0.45 if kind == "cliff" else 0.2
        nt.links.new(scale.outputs["Result"], vc.inputs[2])
        nt.links.new(vc.outputs[0], ramp.inputs["Fac"])
    else:
        nt.links.new(scale.outputs["Result"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], b.inputs["Base Color"])
    bmp = nt.nodes.new("ShaderNodeBump")
    bmp.inputs["Strength"].default_value = 0.9 if kind == "cliff" else 0.35
    if kind == "grass":
        nt.links.new(fine.outputs["Fac"], bmp.inputs["Height"])
    else:
        mixb = nt.nodes.new("ShaderNodeMath")
        mixb.operation = "MULTIPLY_ADD"
        nt.links.new(vor.outputs["Distance"], mixb.inputs[0])
        mixb.inputs[1].default_value = 0.7 if kind == "cliff" else 0.25
        nt.links.new(fine.outputs["Fac"], mixb.inputs[2])
        nt.links.new(mixb.outputs[0], bmp.inputs["Height"])
    nt.links.new(bmp.outputs["Normal"], b.inputs["Normal"])
    return m


def make_seamless(im):
    """Offset-and-blend trick: the tile's seams are crossfaded with the opposite edges."""
    w, h = im.size
    a = np.asarray(im.convert("RGB")).astype(np.float32)
    sh = np.roll(np.roll(a, w // 2, axis=1), h // 2, axis=0)
    yy, xx = np.mgrid[0:h, 0:w]
    dx = np.minimum(xx, w - 1 - xx) / (w / 2)
    dy = np.minimum(yy, h - 1 - yy) / (h / 2)
    wgt = np.clip(np.minimum(dx, dy) * 1.6, 0, 1)[..., None]
    out = sh * (1 - wgt) + a * wgt
    return Image.fromarray(out.astype(np.uint8))


def ground_tile(name, kind, meters=8.0, px=512):
    clear_models()
    for o in list(bpy.data.objects):
        if o.name == "ground":
            o.hide_render = True
    me = bpy.data.meshes.new("tile")
    r = meters
    me.from_pydata([(-r, -r, 0), (r, -r, 0), (r, r, 0), (-r, r, 0)], [], [(0, 1, 2, 3)])
    o = bpy.data.objects.new("tile", me)
    o.data.materials.append(ground_mat(name, kind))
    COLL.objects.link(o)
    SC.render.resolution_x = SC.render.resolution_y = px
    CAM.data.ortho_scale = meters
    CAM.location = (0, 0, 30)
    CAM.rotation_euler = (0, 0, 0)
    SC.render.film_transparent = False
    lights = [ob for ob in bpy.data.objects if ob.type == "LIGHT"]
    saved = [(l, l.data.energy) for l in lights]
    for l, e in saved:
        l.data.energy = e * 0.42  # a flat ground seen from above catches the full sun: tone it down
    path = os.path.join(TMP, name + "_0_0.png")
    SC.render.filepath = path
    bpy.ops.render.render(write_still=True)
    for l, e in saved:
        l.data.energy = e
    SC.render.film_transparent = True
    for ob in list(bpy.data.objects):
        if ob.name == "ground":
            ob.hide_render = False
    im = make_seamless(Image.open(path))
    im.save(path)
    SPRITES[name] = dict(w=px, h=px, ax=0, ay=0, ppu=px / (meters * UPM), cols=1, rows=1, cells=[(0, 0, path)])


def build_tuft(n, lean, color_blades, flowers=None, seed=0):
    import random
    rnd = random.Random(seed)
    for i in range(n):
        a = rnd.uniform(0, 2 * math.pi)
        tilt = rnd.uniform(0.35, 0.75) * lean
        h = rnd.uniform(0.3, 0.5)
        cyl((math.cos(a) * 0.05, math.sin(a) * 0.05, h / 2), 0.025, 0.0, h, color_blades,
            rot=(tilt * math.sin(a), -tilt * math.cos(a), 0), seg=5, smooth=False)
    if flowers:
        for i in range(3):
            a = rnd.uniform(0, 2 * math.pi)
            x, y = math.cos(a) * 0.09, math.sin(a) * 0.09
            cyl((x, y, 0.15), 0.012, 0.012, 0.3, M["grass"], seg=5)
            sphere((x, y, 0.32), 0.05, flowers, seg=10)
            sphere((x, y, 0.345), 0.02, M["petal_y"] if flowers is not M["petal_y"] else M["petal_w"], seg=8)


def build_bush():
    for i, (x, y, z, r) in enumerate(((0, 0, 0.3, 0.38), (0.3, 0.1, 0.22, 0.3), (-0.28, -0.05, 0.24, 0.3), (0.05, -0.28, 0.2, 0.26), (-0.05, 0.3, 0.2, 0.26))):
        sphere((x, y, z), r, M["bush"] if i % 2 else M["bush_light"], seg=16, subsurf=1)


def build_stump():
    cyl((0, 0, 0.18), 0.3, 0.27, 0.36, M["wood_dark"], seg=12)
    cyl((0, 0, 0.365), 0.27, 0.27, 0.01, M["wood_cut"], seg=12)
    torus((0, 0, 0.37), 0.16, 0.008, M["wood_dark"], seg=20)
    torus((0, 0, 0.37), 0.08, 0.008, M["wood_dark"], seg=20)
    for a in (0.4, 2.1, 3.9):
        cyl((math.cos(a) * 0.3, math.sin(a) * 0.3, 0.05), 0.08, 0.04, 0.3, M["wood_dark"], rot=(math.radians(-80) * math.sin(a), math.radians(80) * math.cos(a), 0), seg=8)


def terrain_sprites():
    if want("grass_tile"):
        ground_tile("grass_tile", "grass")
    if want("road_tile"):
        ground_tile("road_tile", "road")
    if want("cliff_tile"):
        ground_tile("cliff_tile", "cliff", meters=6.0)
    for name, args in (("tuft0", (5, 1.0, "grass", None, 1)), ("tuft1", (7, 0.8, "grass_light", None, 2)),
                       ("flower0", (4, 0.9, "grass", "petal_w", 3)), ("flower1", (4, 0.9, "grass_light", "petal_p", 4))):
        if want(name):
            clear_models()
            n, lean, blades, fl, seed = args
            build_tuft(n, lean, M[blades], M[fl] if fl else None, seed)
            static_sprite(name, 160, 96, center=(0, 0, 0.15))
    if want("bush"):
        clear_models()
        build_bush()
        static_sprite("bush", 260, 96, center=(0, 0, 0.3))
    if want("stump"):
        clear_models()
        build_stump()
        static_sprite("stump", 200, 96, center=(0, 0, 0.2))
    props = (("barrel", "decoration/props/barrel.gltf", 1.6, 0.4), ("crate", "decoration/props/crate_A_big.gltf", 1.6, 0.4),
             ("tent", "decoration/props/tent.gltf", 2.6, 0.7), ("wheelbarrow", "decoration/props/wheelbarrow.gltf", 1.7, 0.3),
             ("lumber", "decoration/props/resource_lumber.gltf", 1.7, 0.3), ("weaponrack", "decoration/props/weaponrack.gltf", 1.7, 0.5))
    for name, path, scale, h in props:
        if want(name):
            clear_models()
            prop(HEX + path, scale, rot_z=math.radians(20))
            static_sprite(name, 300, 96, center=(0, 0, h))


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
             "rock0", "rock1", "coinSpin", "coinFlat", "wallStone", "wallStoneV", "gateClosed", "gateOpen", "gateClosedV",
             "gateOpenV", "towerStone", "towerRoyal", "castleRoyal", "grassTile", "roadTile", "cliffTile", "tuft0", "tuft1",
             "flower0", "flower1", "bush", "stump", "barrel", "crate", "tent", "wheelbarrow", "lumber", "weaponrack",
             "kingWarrior", "kingRanger", "kingBuilder", "portraitWarrior", "portraitRanger", "portraitBuilder",
             "wallWood", "wallWoodV", "gateWoodClosed", "gateWoodOpen", "gateWoodClosedV", "gateWoodOpenV",
             "wolf", "enemyArcher", "shieldman", "ram", "ogre", "darkLord", "lancer", "archerElite",
             "farm", "barracks", "ballistaTop", "mine", "boulder",
             "market", "blacksmith", "church", "stable", "watermill", "well", "lumbermill"]
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


if ONLY is not None and "logo" in ONLY:
    logo(os.path.join(TMP, "logo_fg.png"))
    sys.exit(0)
all_sprites()
if ONLY is not None and any(n in ONLY for n in V4_NAMES):
    v4_sprites()
if ONLY is not None and any(n in ONLY for n in V5_NAMES):
    v5_sprites()
if ONLY is not None and any(n in ONLY for n in ("grass_tile", "road_tile", "cliff_tile", "tuft0", "tuft1", "flower0", "flower1",
                                                  "bush", "stump", "barrel", "crate", "tent", "wheelbarrow", "lumber", "weaponrack")):
    terrain_sprites()
pack()
