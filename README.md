# PlayerVaults-NeoForge-21.11.42.1.0.0
this is a port to neoforge of PlayerVaults-by-JJ. with a few fixed features
you can download the already compiled mod from build/libs/ otherwise download or clone this repo and compile it your self. in the root of the directory run ./gradlew build and it will compile. if you want to test the mod yourself without installing minecraft. run ./gradlew runClient and gradle will download a NeoForge 21.11.42 (minecraft 1.21.11) client and load it so you can play the game and test the mod. once the client loads. make a new world with the cheats setting toggled on this is so you can run the following commands in the games chat to test the mod

- /pv                         (opens vault 1 in the vault GUI)
- /pv <number 1 to 1000000>   (opens the vault of that number)
- /pv give                    (gives you the vault item, right click it to open vault 1)

the vault GUI has buttons to change to a different vault. you can store items in any vault. the mod converts the items to json code and stores them in a config text file

commands and usage:
/pv,
/pv <number>,

/pv <player> <number> (opens another players vault),
/pv give,
