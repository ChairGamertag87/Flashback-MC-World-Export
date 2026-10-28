from typing import IO, Sequence
from bmesh.types import BMesh
import bpy
from bpy.types import Image, Mesh, MeshLoopColor
from mathutils import Matrix, Vector

COLOR_LAYER = "tint"

def add_mesh(mesh1: BMesh, mesh2: Mesh, matrix: Matrix=Matrix.Identity(4), color: list[float]=[1,1,1,1]):
    """Add the contents of a mesh into another mesh.

    Args:
        mesh1 (Mesh): The base mesh.
        mesh2 (Mesh): The mesh to add.
        offset (Sequence[float, float, float]): Offset vector
    """
    if not COLOR_LAYER in mesh2.vertex_colors:
        mesh2.vertex_colors.new(name=COLOR_LAYER)
    
    vcolors = mesh2.vertex_colors[COLOR_LAYER]
    i = 0
    for poly in mesh2.polygons.values():
        for idx in poly.loop_indices:
            vcolors.data[idx].color = (color[0], color[1], color[2], color[3])

    mesh2.transform(matrix)
    mesh1.from_mesh(mesh2)
    mesh2.transform(matrix.inverted())

def import_image(file: IO[bytes], name: str, alpha=True, is_data=False) -> Image:
    """Pack an image from an IO stream into the current blend.

    Args:
        file (IO[bytes]): Raw data of PNG file.
        name (str): Name to give the datablock.
        alpha (bool, optional): Use alpha channel. Defaults to True.
        is_data (bool, optional): Create image with non-color data color space. Defaults to False.

    Returns:

        [type]: Loaded image datablock.
    """
    data = file.read()

    image = bpy.data.images.new(name, 1024, 1024, alpha=alpha, is_data=is_data)
    image.file_format = 'PNG'
    image.pack(data=data, data_len=len(data))
    image.source = 'FILE'

    return image
def new_action(owner: bpy.types.ID, name: str) -> bpy.types.Action:
    """Create an action and assign it to the owner, with a slot on blender 4.4+"""
    anim_data = owner.animation_data_create()
    action = bpy.data.actions.new(name=name)
    anim_data.action = action
    if hasattr(action, 'slots') and anim_data.action_slot is None:
        anim_data.action_slot = action.slots.new(owner.id_type, owner.name)
    return action

def action_fcurves(action: bpy.types.Action):
    """Fcurve collection of an action, blender 5.0 moved these into per slot channelbags"""
    if hasattr(action, 'fcurves'):
        return action.fcurves
    from bpy_extras import anim_utils
    return anim_utils.action_ensure_channelbag_for_slot(action, action.slots[0]).fcurves
