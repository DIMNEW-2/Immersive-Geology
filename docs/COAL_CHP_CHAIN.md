# The coal CHP chain (угольная ТЭЦ)

*English design note; Russian summary at the end. / Английское описание разработки; русское резюме в
конце.*

This mod addition implements a coal-fired **combined heat and power plant** - a ТЭЦ - as a chain of
Immersive Geology multiblocks and recipes, following the order a real station runs its processes.
Existing machines are reused where they already do the job (the Ball Mill is the pulverising mill,
the Steam Turbine and Alternator are the turbo-generator); five new multiblocks and three new
materials fill the gaps.

```
 coal ore ──crusher──▶ grit ──ball mill──▶ pulverised coal
                                                │
                                                ▼
 feed water ◀──────────── condensate ◀── COAL BOILER ──▶ superheated steam ──▶ STEAM TURBINE ──▶ ALTERNATOR ──▶ FE
     ▲                                          │                                  │
     │                                          ├─▶ flue gas ─▶ ELECTROSTATIC    └─▶ exhaust steam
     │                                          │               PRECIPITATOR            │
     │                                          │                 │        │            ▼
     │                                          │            fly ash    clean gas   STEAM CONDENSER ◀─┐
     │                                          └─▶ bottom ash         (stack)         │        │      │
     │                                                                               condensate │      │
     │                                                                                          ▼      │
     └──────────────────────── preheated feed water (bonus) ◀── hot water ◀─ DISTRICT HEATER    COOLING TOWER
                                                                            (network supply → town)  ▲
                                                                                                     │
                                                              circulating water (heated) ────────────┘
```

## Machines

| machine | registry | structure (x,y,z) | master / trigger | registration flavour |
|---|---|---|---|---|
| Coal Boiler (пылеугольный котёл) | `coal_boiler` | 5×7×5 | (2,1,2) / (2,1,4) | `stone(...)` → active, non-mirrored |
| Electrostatic Precipitator (электрофильтр) | `electrostatic_precipitator` | 5×9×4 | (2,0,2) / (2,1,3) | metal, mirrored |
| Steam Condenser (поверхностный конденсатор) | `steam_condenser` | 4×3×4 | (1,0,1) / (1,1,3) | metal, mirrored |
| Cooling Tower (градирня) | `cooling_tower` | 7×9×7 | (3,0,3) / (3,1,6) | `mirroredStone(...)`, mirrored, no power |
| District Heater (сетевой подогреватель) | `district_heater` | 3×4×3 | (1,1,1) / (1,0,1) | metal, mirrored |

Port coordinates are given in structure space; "trigger side" is +Z (the side the player forms from),
"far side" is -Z, the flanks are ±X.

### Coal Boiler
- fuel (item) in: **top** of the roof bunker (2,6,2);
- feed water (fluid) in: **trigger side** of the drum row (2,4,4); accepts water *or* network hot
  water - preheated feed water grants +6% efficiency (regenerative feed water heating);
- superheated steam (fluid) out: **far side** of the superheater row (2,3,0);
- flue gas (fluid) out: **far side** of the roof duct (2,6,0);
- bottom ash (item) out: **trigger side** of the foundation hopper, discharged to (2,0,5);
- energy in: **trigger side** of the furnace corner (4,1,4) - 120 FE/t idle, 480 FE/t firing
  (draft fans, coal feeders, feed pumps); without it there is no draft and the fire dies;
- redstone: control panel (2,6,4).

Thermal model: furnace heat 0-100 with gain while burning and slow passive loss; evaporation scales
with `heat/70`; drum pressure rises when the steam cannot leave and the safety valve lifts above 90,
wasting steam; load tracking feeds an efficiency readout (partial and overloaded operation both cost
efficiency, as in reality).

### Electrostatic Precipitator
- flue gas in: **far side** inlet duct (2,1,0); energy in: **trigger side** (2,1,3);
- fly ash (item) out: hoppers discharged to (2,0,4); cleaned gas vents from the stack (particles);
- 24 mB/t throughput, 16 FE per mB, 99% capture; unpowered fields capture nothing and the gas backs
  up, choking the boiler - the induced-draft-fan dependency of a real plant.

### Steam Condenser
- exhaust steam / turbine condensate in: **far side** top (1,2,0);
- circulating (cooling) water in: flank (0,1,1); heated water out: flank (3,1,1);
- condensate (feed water) out: **trigger side** bottom (1,0,3); energy in: (1,1,3), 60 FE/t;
- condenses up to 8 mB/t, each mB of steam heating 6 mB of circulating water (latent-heat balance).

### Cooling Tower
- heated water in: **trigger side** mid height (3,4,6); cooled water out: **trigger side** basin
  (3,0,6); make-up water in: flank (0,0,3);
- natural draft: **no power, no redstone**. Performance = f(biome temperature, rain, altitude);
  ~2% of the cooled flow evaporates and must be replaced from the make-up tank or the tower runs at
  half rate with a dry basin.

### District Heater
- extraction steam in: **far side** top (1,3,0); network return water in: flank (0,1,1);
- hot network supply out: flank (2,1,1); condensate out: **trigger side** bottom (1,0,2);
- energy in: flank (0,0,1), 40 FE/t; redstone (2,2,2);
- 1 mB of extraction steam heats 4 mB of network water - the cogeneration stage that makes the plant
  a ТЭЦ.

## Materials and fuels

| material | forms | notes |
|---|---|---|
| `fly_ash` (Золошлак) | POWDER = fly ash, GRIT = bottom ash | pozzolan; crafting recipe substitutes it for rods/wires in reinforced concrete |
| `flue_gas` (Дымовой газ) | fluid, gas density, 430 K | boiler → precipitator |
| `hot_water` (Сетевая горячая вода) | fluid, 420 K | district heater product, cooling tower input, preheated feed water |
| lignite / bituminous / anthracite | + GRIT, + POWDER | crusher → grit, ball mill → pulverised coal |

Boiler fuels (`immersivegeology:boiler_fuel`, data-driven): steam yields follow real lower heating
values (lignite ~13, bituminous ~24, anthracite ~28 MJ/kg) scaled to mB; pulverised coal always beats
lump coal of the same grade (complete suspension burning vs. grate losses); flue gas volume is ~1.5×
the steam volume, matching the real gas-to-steam mass ratio; bottom ash chances follow typical ash
contents (lignite highest, coke lowest).

## Recipes added

- `boiler_fuel/*`: ten fuel schedules (three pulverised coals, three lump coals, coal, charcoal,
  coal coke, coke dust);
- `crushing/crush_coal_<coal>_to_grit`, `ballmill/pulverise_<coal>[_from_ore]`: coal preparation;
- `crafting/craft_reinforced_concrete_with_fly_ash`: fly ash as pozzolanic binder;
- material chains (`setupRecipeStages` of the three coal materials) add the same pulverisation steps
  to the manual's process trees.

## Real-plant numbers behind the tuning

| quantity | real plant | in game |
|---|---|---|
| boiler efficiency (КПД котла) | 90-94% pulverised, 78-84% lump | `efficiency` field of the fuel (0.78-0.95) |
| steam per kg coal | ~7-8 kg | 240-560 mB per item |
| flue gas per kg coal | ~1.4-1.6 kg steam-equivalent | 1.5× steam |
| ash split fly/bottom | ~80/20 | precipitator capture vs. `bottom_ash_chance` |
| ESP capture | 99-99.7% | 99% while powered |
| condenser cooling water ratio | ~50:1 by mass at 10 K rise | 6:1 by volume (game scale) |
| cooling tower evaporation | 1-2% of circulation | 2% |
| cogeneration fuel utilisation | 60-75% vs 35-40% condensing | district heater sells heat + preheated feed water bonus |

## Русское резюме

Добавлена цепочка угольной ТЭЦ: размол угля до пыли (дробилка → шаровая мельница), пылеугольный котёл
с тепловой инерцией, тягой и предохранительным клапаном, электрофильтр с улавливанием золы-уноса и
дымовой трубой, поверхностный конденсатор, замыкающий водопаровой контур, градирня с естественной
тягой и погодозависимой производительностью, и сетевой подогреватель - ступень когенерации, которая и
делает станцию ТЭЦ. Три новых материала (золошлак, дымовой газ, сетевая горячая вода) и десять
топливных регламентов котла привязаны к реальным теплотам сгорания и зольности углей. Раскладка
патрубков каждой машины - в таблице выше и в статьях инженера (manual). Инструкции по моделям
мультиблоков - в `MULTIBLOCK_MODELS.md` / `MULTIBLOCK_MODELS.ru.md`.
