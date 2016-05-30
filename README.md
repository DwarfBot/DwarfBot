# DwarfBot

A project for converting Dwarf Fortress screenshots from one tileset to another.

### Setup

Requires JDK 7

```sh
$ # Make sure you have the --recursive, so it won't cause errors
$ git clone --recursive 'https://github.com/Choco31415/DwarfBot.git' 
$ cd DwarfBot
```

```sh
$ # Run once
$ ./gradlew run
```

```sh
$ # Build redistributable jar
$ ./gradlew shadowJar
$ # and run
$ java -jar build/libs/DwarfBot-all.jar
```
