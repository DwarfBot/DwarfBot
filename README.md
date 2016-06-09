# DwarfBot

A project for converting Dwarf Fortress screenshots from one tileset to another.

### Setup

Requires JDK 8

```sh
$ git clone 'https://github.com/Choco31415/DwarfBot.git' 
$ cd DwarfBot
```

The preferred way to run any of the interfaces (`cli` and `web` for now, `gui` and `android` coming soon) is below. It will allow you to specify command-line arguments as well.

```sh
$ # Build all subprojects into standalone jars
$ ./gradlew shadowJar
$ # and run
$ java -jar <subproject>/build/libs/<subproject>-all.jar [arguments...]
```
