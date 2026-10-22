## 1.3.11
- Added a more page to the main menu, so the account switcher, the hosting button, the social menu and the cosmetics stop crowding the OneClient logo
- Added a Performance page with a low-end mode that caps the frame rate at 60 FPS, shortens the render and simulation distances, cuts the particles, drops the entity shadows and turns the motion blur off on weak machines
- The performance settings no longer write the game's video options to disk, so the launcher stays the one that owns them
- Added an adaptive mode that caps the game at 30 FPS while the frame rate stays low and gives the cap back once it recovers
- Added a performance report that tells you what holds the frame rate back, including the most common cause on a laptop: the game drawing on the processor instead of the graphics card
- The performance report can put the game back on OpenGL when Polydium switched it to Vulkan and the Vulkan driver is the slow part
- Added `/polyplus performance`, which prints the frame rate, the renderer, the graphics backend and everything that holds the frame rate back
- The performance report names the bundled performance mod and the version of it that is running
- The performance report also covers the main menu, so a slow menu is named as well
- The main menu now lowers its frame cap when the machine cannot reach it, and puts it back when it can
- The 3D player preview on the main menu is no longer redrawn for every single frame
- Added a frame rate cap to the performance settings, so the game can be held at the refresh rate, 120, 60 or 30 frames a second instead of running uncapped
- The low-end mode now also drops the graphics to the fast setting and the mipmaps, and the game keeps the frame rate cap it had before

## 1.3.10
- Fixed a crash with Cubes Without Borders when using a custom fullscreen resolution with Vulkan
- Fixed the game getting stuck on "Preparing for world creation" with Vulkan on 1.21.11 when there are no singleplayer worlds
- Fixed a crash with Xaero's Minimap's entity radar set to icons on Polydium's Vulkan backend on 1.21.11/26.1