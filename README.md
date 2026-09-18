# Pet Growth Tracker RuneLite plugin

Tracks the growth and hunger of your kitten/cat or puppy/dog, and warns you before you'd lose it or before its growth stalls. One overlay, one plugin, either species.

This is a fork of [kitten-tracker](https://github.com/pieterjanbuntinx/kitten-tracker), restructured so the same plugin also understands puppies/dogs. The kitten tracker's overlay style is kept for both species: plain title + label/value rows, not a custom-painted card.

## Architecture

A player only ever has one follower at a time, so the plugin's job is to work out which species it is and hand every RuneLite event to that species' handler. Both species are structured the same way, under the base package `net.dv8tion.runelite.petgrowthtracker`:

```
…cat                                    …dog
├── FollowerKind   (id → life stage)    ├── FollowerKind   (id → life stage)
├── CatTracker     (pure growth clock)  ├── DogTracker     (pure growth clock)
├── CatGrowthGate  (Client-dependent    │
│                   pause detection)    │
└── CatPetHandler  (RuneLite adapter)   └── DogPetHandler  (RuneLite adapter)
```

- `PetLifeStage` - the common interface both `FollowerKind` enums implement: `isTracked()` (is this id one of ours) and `isFullyGrown()` (has it reached its final stage). A cat has three life stages (kitten, cat, overgrown cat) and a dog has two (puppy, dog) because that's genuinely how the two species differ in-game; this interface only unifies the two questions the plugin needs answered to route a follower.
- `PetHandler` - the interface both species' handlers implement: follower changed, game tick, chat message, dialog text, menu click, game state change, config change, and a `PetStatus` for the overlay to paint.
- `PetTrackerPlugin` - the only class that touches RuneLite's event bus. It classifies the current follower by asking each species' `FollowerKind.getFromFollowerId()` and routes every event to whichever one claims it. A follower neither recognises (a boss pet, a skilling pet) gets no handler at all - nothing is shown, guessed, or persisted for it.
- `PetOverlay` - a generic panel (title + label/value rows) that paints whatever `PetStatus` the active handler returns. This is the original Kitten Tracker's `OverlayPanel`/`LineComponent` look, generalised.
- `CatTracker` / `DogTracker` - the pure growth/hunger/attention clocks, holding no RuneLite types. `CatTracker`'s numbers and rules are the original `KittenPlugin`'s, unchanged.
- `CatPetHandler` / `DogPetHandler` - the thin RuneLite adapters: chat/dialog parsing, notifications, and config-driven persistence around their tracker.

### Why the two trackers aren't the same shape

A cat's growth has no server-exposed signal at all - the only thing RuneLite can observe is the kitten's overhead text each time a 90-second tick completes, which is unreliable in crowded areas. `CatTracker` reconstructs growth from that, and `CatGrowthGate` exists purely to cover the gap: when overhead text can't be seen, it checks whether a known growth-blocking interface (bank, GE, journal, ...) is open instead. All of that is real, tuned-over-years client-side inference, because it's the best information available.

A dog's growth turns out to need none of that. The account carries two live varbits for the currently-followed puppy - `VarbitID.PUPPY_GROWTH_TRACKER` (active-growth minutes) and `VarbitID.PUPPY_FED_TIMER` (minutes since last feed) - confirmed directly against the RuneLite client jar (puppies are very recent content, shipped 2026-09-08). With the server already doing the accounting, `DogTracker` just reads it: no banked-growth clock, no "Guess age" dialog parsing, no menu-click feed detection with a refusal-window rollback, and no growth-blocking interface list - none of the client-side inference the cat side needs is even possible to get wrong, because there's nothing left to infer. `DogPetHandler` accordingly has no persistence either: re-reading the varbit after a relog gives the exact answer, where the cat side has to reconstruct its estimate from `PetTrackerConfig`.

So the two trackers are structurally parallel (id classification → tracker → handler) but not identical in what they compute, because they're built on genuinely different information: nothing about the cat side's overhead-text/interface-blocking inference was ever an option for dogs to fall back on, so once the varbit pair was found, there was nothing left needing that kind of workaround.

### Follower classification

Both species classify a follower by NPC id, in contiguous ranges Jagex actually ships:

- Cats: `net.runelite.api.NpcID` - `KITTEN_5591`..`HELLKITTEN`, `CAT_1619`..`HELLCAT`, `LAZY_CAT`..`LAZY_HELLCAT`, `WILY_CAT`..`WILY_HELLCAT`, `OVERGROWN_CAT`..`OVERGROWN_HELLCAT`.
- Dogs: `net.runelite.api.gameval.NpcID` - `LABRADOR_YELLOW`..`YORKIE_YELLOW` (36 ids: 12 breeds × 3 colours, adult) and `LABRADOR_YELLOW_PUPPY`..`YORKIE_YELLOW_PUPPY` (the same 36, puppy stage). These only exist under the newer `gameval` id class - puppies are 2026-09-08 content - which is why the dog side imports `gameval.NpcID` while the cat side still uses the legacy `NpcID` (matching what the original `KittenPlugin` already did elsewhere for newer interface/item ids).

Because the id alone says whether a dog follower is a puppy or already grown, `DogPetHandler` never has to wait for a "grown into a dog" chat line to know - an adult dog is marked fully grown the instant it's seen.

### Adding a third species

Add a `FollowerKind` enum implementing `PetLifeStage`, a pure `XTracker` for its clock, an `XPetHandler` implementing `PetHandler`, and teach `PetTrackerPlugin.classifyAndDispatch` to check the new `FollowerKind` too. `PetOverlay` needs no changes - it only ever renders `PetStatus`. Only add hidden persisted fields to `PetTrackerConfig` if the species has no server-exposed growth signal to re-read after a relog, the way cats don't and dogs do.

## Cat mechanics (unchanged from Kitten Tracker)

- Kitten grows one growth tick every 90s.
    - Upon reaching 90s, this growth tick does not progress if the player is in an interface (dialog, bank menu, etc.).
        - Note: This does not include all interfaces. If you can interact with the game while the interface is open (e.g. settings/world map is open, and you can still run around/skill/etc.), then that interface will not stall kitten growth.
        - It will progress immediately after exiting that interface, as long as the kitten has a chance to grow (ex: growth does not progress if player AFKs to logout in a bank interface)
        - If you are only in an interface during a different time of that tick progress (ex: banking during seconds 40-70 of the 90s tick), the kitten's growth will be unaffected by you being in that interface, as long as you're not in an interface at 90s.
    - The kitten's progress within that 90s tick gets reset each time it is picked up, or when the player logs out/hops worlds.
- Kitten has overhead text each and every time it grows a growth tick, so as the timer ticks down, it waits for that overhead text before continuing to the next growth tick.
  - If you are at a very crowded place with many players/pets/etc., sometimes your kitten will not render, preventing the plugin from seeing the overhead text. In this case, the plugin instead checks (via `CatGrowthGate`) for some common interfaces that would prevent growth.
- Kitten can only request hunger/attention at the time when a growth tick progresses.

Kitten's need for attention:
- Attention requests (and run-away time) are *supposed* to be 4.5 minutes apart but can be delayed by a hunger notification, which always takes precedence.
- Double stroke adds 36 minutes until the kitten runs away. Single stroke adds 24 minutes (actually variable in-game). Ball of wool adds 60 minutes.
- Attention warnings are 9 minutes and 4.5 minutes before the kitten runs away.

## Dog mechanics

- A puppy becomes a dog after 180 minutes on the `PUPPY_GROWTH_TRACKER` varbit - i.e. three hours of growth the server itself counted as active.
- `PUPPY_FED_TIMER` counts minutes since the last feed. `DogTracker` reads that directly against three fixed cutoffs, read verbatim from [the wiki's Puppy page](https://oldschool.runescape.wiki/w/Puppy#Hunger): "Your puppy is getting quite hungry" at 15 minutes, "Your puppy is very hungry" at 25, and growth actually pauses ("Your puppy has stopped growing...") at 30.
- Reading `PUPPY_FED_TIMER` directly, rather than watching `PUPPY_GROWTH_TRACKER` for a stall (an earlier version of this did that instead, reasoning it wouldn't need to know Jagex's real cutoff to be correct), is what makes a feed register immediately - the growth counter itself only moves once a minute, so waiting on it made *ending* a pause exactly as slow to notice as starting one.
- Puppies do not run away - an unfed puppy simply stops growing until fed again.
- The "Hungry in" row counts down to the 30-minute pause point, turning amber at 15 and red at 25 to mirror the game's own two warnings, then switching to "stopped growing" text once actually paused (a frozen countdown at zero would be more confusing than informative). Notifications fire at all three stages, the same two-tier-warning-then-consequence shape as the cat side's hunger/attention notifications.

## Licensing

BSD 2-Clause, see [LICENSE](LICENSE). This is a derivative work of kitten-tracker, and its copyright notices are retained accordingly: the files carrying the original growth-tick algorithm (`KittenPlugin.java`, `KittenConfig.java`, now split across `CatTracker.java`/`CatPetHandler.java`/`CatGrowthGate.java`/`PetTrackerConfig.java`) still carry their original 2018 header crediting Nachtmerrie, and the repository-level LICENSE keeps 2021's Pieter-Jan Buntinx alongside it.

## Not yet done

- **Icon**: the plugin hub's build validation requires an `icon.png` at the repo root. Not included yet - add one before submitting.
