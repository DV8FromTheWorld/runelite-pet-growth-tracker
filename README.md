# Pet Growth Tracker

Tracks the growth and hunger of your kitten/cat or puppy/dog, and warns you before it runs away or stops growing. One overlay, either species.

Forked from [pieterjanbuntinx/kitten-tracker](https://github.com/pieterjanbuntinx/kitten-tracker), extended to also track puppies/dogs.

## How it works

- **Cats** — ported from the original Kitten Tracker unchanged: growth is inferred from overhead text on each 90-second tick, falling back to checking for a growth-blocking interface (bank, GE, ...) when that can't be seen. Warns you before your kitten runs away from hunger or neglect.
- **Dogs** — read directly from the account's own `PUPPY_GROWTH_TRACKER`/`PUPPY_FED_TIMER` varbits, so there's no inference and no persistence needed - the server already remembers it. Feed thresholds (15/25/30 minutes) are read from [the OSRS Wiki](https://oldschool.runescape.wiki/w/Puppy#Hunger).

Both species are classified by NPC id and handled through a shared `PetHandler` interface, under the package `net.dv8tion.runelite.petgrowthtracker`. See `CatTracker`/`CatPetHandler`/`CatGrowthGate` and `DogTracker`/`DogPetHandler` for the full breakdown - the class-level javadoc on each explains the design.

## Licensing

BSD 2-Clause, see [LICENSE](LICENSE). This is a derivative work of [kitten-tracker](https://github.com/pieterjanbuntinx/kitten-tracker); its original copyright notices (2018 Nachtmerrie, 2021 Pieter-Jan Buntinx) are retained, both in the repository-level LICENSE and in the source files that still carry its original growth-tracking algorithm.

## Not yet done

- `icon.png` at the repo root, required by the plugin hub's build validation.
