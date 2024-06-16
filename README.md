# <a href="https://github.com/xCollateral/VulkanMod"> <img src="./src/main/resources/assets/vulkanmod/Vlogo.png" width="30" height="30"/> </a> VulkanMod

This is a fabric mod that introduces a brand new **Vulkan** based voxel rendering engine to **Minecraft java** in order to both replace the default OpenGL renderer and bring performance improvements.

### Why?
- Highly experimental project that overhauls and modernizes the internal renderer for Minecraft. <br>
- Updates the renderer from OpenGL 3.2 to Vulkan 1.2 with partial 1.1 (Vulkan 1.1 compatibility with other devices is not guaranteed). <br>
- Provides a potential reference for a future-proof Vulkan codebase for Minecraft Java. <br>
- Utilizes the VulkanAPI to allow for capabilities not always possible with OpenGL. <br>
- Including reduced CPU Overhead and use of newer, modern hardware capabilities. <br>

### Demonstration Video (by xCollateral):

[![Demostration Video](http://img.youtube.com/vi/sbr7UxcAmOE/0.jpg)](https://youtu.be/sbr7UxcAmOE)

## FAQ
- Remember to check the [Wiki](https://github.com/xCollateral/VulkanMod/wiki) we wrote before asking for support!

## Installation

### Download Links:

- [![CurseForge](https://cf.way2muchnoise.eu/full_635429_downloads.svg?badge_style=flat)](https://www.curseforge.com/minecraft/mc-mods/vulkanmod)

- [![Modrinth Downloads](https://img.shields.io/modrinth/dt/JYQhtZtO?logo=modrinth&label=Modrinth%20Downloads)](https://modrinth.com/mod/vulkanmod/versions)

- [![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/xCollateral/VulkanMod/total?style=flat-square&logo=github&label=Github%20Downloads)](https://github.com/xCollateral/VulkanMod/releases)

### Install guide:
>1) Install the [fabric modloader](https://fabricmc.net).
>1) Download and put the `Vulkanmod.jar` file into `.minecraft/mods`
>1) Enjoy !

## Useful links
<table>
    <tr>
      <th> SaintPlayz/Shadow Discord server</th>
      <th> Ko-Fi (xCollateral)</th>
    </tr>
  <tr>
    <td style="text-align:center"> 
        <a href="https://discord.com/invite/CHX4dUSTqs"> 
            <img alt="Discord" align="top" src="https://img.shields.io/discord/963180553547419670?style=flat-square&logo=discord&logoColor=%23FFFFFF&label=SaintPlayz/Shadow%20%20discord%20server&labelColor=%235865F2&color=%235865F2">
        </a>
     </td>
    <td>
        <a href="https://ko-fi.com/V7V7CHHJV">
            <img alt="Static Badge" align="top" src="https://img.shields.io/badge/KoFi-%23ff5e5b?logo=ko-fi&logoColor=%23FFFFFF&link=https%3A%2F%2Fko-fi.com%2FV7V7CHHJV">
        </a>
    </td>
  </tr>
</table>


## Features

### Optimizations:
>- [x] Multiple chunk culling algorithms
>- [x] Reduced CPU overhead
>- [x] Improved GPU performance
>- [x] Indirect Draw mode (reduces CPU overhead)
>- [x] Chunk rendering optimizations

### New changes:
>- [x] Native Wayland support
>- [x] GPU selector
>- [x] Windowed fullscreen mode
>- [x] Revamped graphic settings menu
>- [x] Resizable render frame queue
>- [ ] Shader support
>- [ ] Removed Herobrine

## Known Issue with VKMod on PojavLauncher:
- Few bugs with **ASR** enabled.
- Some devices doesn't support.

## Notes
- This mod is still in development, please report issues in the [issue tab](https://github.com/xCollateral/VulkanMod/issues) with logs attached!
- This mode isn't just "minecraft on vulkan" (e.g: [zink](https://docs.mesa3d.org/drivers/zink.html) ), it is a full rewrite of the minecraft renderer.
- This will not support 32-bit devices **(e.g. Desktops, Mobile, etc...)** but this modified version may still works on **Desktop** (not sure).
- The **modifier** has no plan to publish this publicly on **any platforms (e.g YouTube, Mediafire, etc...)**.
- This modification of **VulkanMod** is **UNOFFICIAL**.
- Use main branch **"1.20.4"** and don't worry about **Malware**, or **any viruses** because this **modified** version doesn't **APPLY** any **malicious files or codes** and trust me, I have **5 YEARS** of coding experience and I know to trace **any malicious files** at all.
- Please **DON'T report any crashes/bugs or upload** on **VulkanMod Discord** related to **PojavLauncher**.
- This is for my personal use, but you can try it for yourself **(device compatibility is not guaranteed)**.

