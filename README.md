# Footprints

Walk across sand, dirt or snow on a Paper or Folia server and you leave prints behind you.
They take the colour of the ground, fade out and disappear. Nothing they leave survives a
restart.

## How it works

A print is a `TextDisplay` holding a white glyph tinted with the colour configured for that
surface. Text displays have `setTextOpacity`, so fading costs one metadata packet instead of
a stack of half transparent textures, and a single pair of textures covers every kind of
ground.

The server does not tick display entities: no AI, no physics, no collision boxes. What a
print costs is packets. One when it spawns, a few while it fades, one when it goes away.

Limits that keep the entity count flat:

- a print every `step-distance` blocks walked rather than one per step
- `max-per-player` prints per player, 12 by default, where the oldest gets dropped
- `view-range` of 0.35, roughly 22 blocks, against the default 64
- `setPersistent(false)`, so prints never reach the chunk file
- the whole trail is erased on quit, teleport and world change

Thirty players walking at once come to about 360 entities.

## Install

1. Drop the jar into `plugins/` and start the server.
2. Hand players the resource pack. `resourcepack/` is an ordinary pack: zip its contents, or
   copy `assets/` into the pack you already ship.
3. Restart the server.

Fonts are one of the few things Minecraft merges across packs, so the two providers in
`assets/minecraft/font/default.json` sit next to the vanilla ones rather than replacing
them. Without the pack the plugin still runs, players just see nothing on the ground.

## Commands

`/footprints` with no arguments toggles your own prints, and the choice is stored on the
player.

| command | what it does | permission |
| --- | --- | --- |
| `/footprints toggle` | turn your prints on or off | everyone |
| `/footprints clear` | wipe the prints you left | everyone |
| `/footprints stamp` | drop a single print where you stand | `footprints.admin` |
| `/footprints reload` | reread `config.yml` | `footprints.admin` |
| `/footprints status` | how many prints are alive | everyone |

## Config

Every surface has a colour, a starting opacity and a lifetime in ticks:

```yaml
surfaces:
  snow:
    color: '#8FA6C4'
    opacity: 195
    lifetime: 320
    blocks:
      - SNOW_BLOCK
      - SNOW
      - POWDER_SNOW
```

Two values are easier to eyeball than to reason about. `forward-offset` shifts the print
along the view direction, because a line of text is not drawn centred on its entity, and
`lift` raises it off the block so it stops z-fighting. Place one with `/footprints stamp`,
edit the file, run `/footprints reload`, look again.

## Notes for anyone reading the source

Text renders from one side only. The quad gets laid flat by rotating it -90 degrees around
X. At +90 it lies flat too, but then the normal points down into the block, and the print
turns invisible from above while the entity is still there and still selectable. Setting
`face-up: false` flips the sign back if that ever needs testing again.

The glyphs live at U+EB00 and U+EB01 in the Private Use Area. If you move them, keep both
feet within columns 4 to 11 of the texture. A bitmap glyph advances to its rightmost lit
column, so a shape whose mirror image ends one column short is centred differently, and the
left print drifts away from the right one. `tools/make_glyphs.py` draws both textures from
ASCII art and prints the advance it ends up with.

## Building

```
./gradlew build
```

Java 21 against `paper-api` 1.21.11, nothing shaded. The jar lands in `build/libs/`.

## License

MIT.
