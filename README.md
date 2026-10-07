# PlayerVaults-NeoForge-21.11.42.1.0.0
This is a port to NeoForge of PlayerVaults-by-JJ, with a few fixed features
You can download the already compiled mod from build/libs/; otherwise, download or clone this repo and compile it yourself. In the root of the directory, run ./gradlew build to compile. If you want to test the mod yourself without installing Minecraft. run ./gradlew runClient, and Gradle will download a NeoForge 21.11.42 (Minecraft 1.21.11) client and load it so you can play the game and test the mod. Once the client loads. Make a new world with the cheats setting toggled on; this is so you can run the following commands in the game's chat to test the mod

- /pv                         (opens vault 1 in the vault GUI)
- /pv [number 1 to 1000000]   (opens the vault of that number)
- /pv give                    (gives you the vault item; right-click it to open vault 1)

The vault GUI has buttons to change to a different vault. You can store items in any vault. The mod converts the items to JSON code and stores them in a config text file

Commands and usage:
- /pv
- /pv [number]

- /pv [player] [number] (opens another player's vault)
- /pv give
