# Minecraft Replay Exporter

An addon for [Flashback](https://modrinth.com/mod/flashback) that allows you to export your replays into [Blender](https://www.blender.org/).

**This mod is still in development. It is buggy and should not be used in a production environment!** (If you do use it in production, let me know how it goes!)

**[Example](https://youtu.be/eDdBe3me0es)**

# Installation

**Minecraft**

1. Download the jar file from the Releases tab.
2. Add the jar to your `mods` folder, alongside Flashback and the Fabric API.

**Blender**

1. Download the zip file from the Releases tab. *Do not extract it!*
2. In Blender, open your preferences and go to the Add-ons tab.
3. In the top right, click "Install..." and navigate to the zip file in the window that opens.

# Usage

## Exporting

To begin, record the replay you wish to export with Flashback and open it in the editor. Position the camera where you want the export region to be centered.

> Warning: nether portals and other forms of cross-dimension travel are not currently supported.

Click "Export to Blender" in the editor's menu bar. This opens a window with the following settings:

- `Radius (chunks)`: The number of chunks around the camera to export.

- `Lower Section` / `Upper Section`: The vertical range (in 16-block sections) to export.

- `Start Tick` / `End Tick`: The part of the replay to export. Defaults to the range covered by your keyframes, or the whole replay.

- `Framerate`: How many frames per second to capture.

- `Fluid Mode`: How fluids get exported.

Flashback will play through the replay (hold ESC to cancel), then the file is written in the background. A small window shows the progress.

Blocks are quite expensive to import and export, and your export region will *significantly* impact how long the file takes to process. It is recommended to determine the bare minimum of what must be exported in your scene and clamp your export bounds to that.

<img title="" src="doc/images/export_settings.png" alt="The Export Settings menu" data-align="center" width="647">

Once you're ready, click the "Export" button at the bottom left and wait for it to complete. It is common for the window to appear frozen while the initial block world is captured.

## Importing

After exporting from Minecraft, you should be left with a `.replay` file, which can be imported into Blender using the provided addon.

Once you've ensured the addon is installed, create a new project and go to `File > Import > Minecraft Replay File (.replay)`. Navigate to your file, but before you import it, see the settings on the right:

<img src="doc/images/import_settings.png" title="" alt="Replay Import Settings" data-align="center">

The main two you need to worry about are `Import World` and `Import Entities`. While you usually want to import both, they each can take quite a bit of time, so it may be useful to disable one or the other.

Everything else can be left as default.

Once you're ready, click "Import Minecraft Replay" at the bottom. Blender will appear to hang for a few minutes. Don't worry; this is because the data-set of a Minecraft world is quite large, and Blender provides no way to update the UI while it is processing.

Once it is finished importing, you should have a Minecraft world and most of its entities in your scene! If something goes wrong, make sure to submit a bug report!
