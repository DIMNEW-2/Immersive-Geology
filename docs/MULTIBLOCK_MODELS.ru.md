# Добавление моделей мультиблочным структурам Immersive Geology

Документ описывает целиком, как мультиблок этого репозитория получает свою 3D-модель: от шаблона
структуры, определяющего *где стоят блоки*, через OBJ/MTL/текстуры, до датагена, который превращает
их в blockstate и модели, загружаемые игрой. Всё привязано к файлам, которые реально лежат в моде;
для каждого помощника указан путь.

Английская версия: [`MULTIBLOCK_MODELS.md`](MULTIBLOCK_MODELS.md). Машины угольной ТЭЦ, добавленные
вместе с этим документом, - рабочие примеры; их устройство и раскладка патрубков описаны в
[`COAL_CHP_CHAIN.md`](COAL_CHP_CHAIN.md).

---

## 1. Конвейер целиком

```
шаблон структуры (.nbt)            OBJ + MTL                      текстура PNG
 data/immersivegeology/            models/block/multiblock/       textures/block/multiblock/
 structures/multiblocks/<mb>.nbt   obj/<mb>/<mb>.obj (+.mtl)      <mb>/<skin>.png
            |                               |                            |
            +---------------+---------------+----------------------------+
                            |
              датаген: IGBlockStateProvider
              (src/datagen/.../IGBlockStateProvider.java)
                            |
              загрузчик IE "basic_split" разрезает OBJ на части
              по не-воздушным блокам шаблона
                            |
              src/generated/resources/assets/immersivegeology/
                blockstates/<mb>.json
                models/block/.../<mb>_split.json (+ _mirrored / _active)
                models/item/<mb>.json            (IGComplexItemModelProvider)
```

Ключевые факты, из которых следует всё остальное:

* Сформированный мультиблок рисуется **одной OBJ-моделью на всю структуру**, а не отдельными
  блоками. Блоки, которые ставит игрок, живут только до формирования; дальше каждая ячейка становится
  частью мультиблока, а визуал даёт модель.
* Загрузчик моделей Immersive Engineering `immersiveengineering:basic_split` разрезает этот OBJ на
  части. Датаген сообщает ему, какие части существуют, передавая список не-воздушных позиций шаблона
  со смещением на мастер-блок (`IGBlockStateProvider.split(...)`,
  `src/datagen/java/com/igteam/immersivegeology/common/data/generators/IGBlockStateProvider.java`).
* Поэтому OBJ **обязан** быть сделан в той системе координат, которую использует список частей:
  **1 единица = 1 блок, начало координат в мастер-блоке** (см. §3).

---

## 2. Шаг 1 - шаблон структуры

Файл: `src/main/resources/data/immersivegeology/structures/multiblocks/<name>.nbt`
(gzip-ованный ванильный structure NBT: `size`, `palette`, `blocks`, `entities`, `DataVersion = 3465`).

### 2.1 Соглашения

* `size = (x, y, z)`, **y вверх**. Java-сторона повторяет размер в конструкторе подкласса
  `IGTemplateMultiblock`, и `IGTemplateMultiblock.getTemplate` *проверяет assert-ом*, что размер
  шаблона совпадает.
* **Перед** машины (`RelativeBlockFace.FRONT`) - это **-Z**; игрок формирует машину со стороны **+Z**,
  там же Immersive Geology ставит **триггерный** блок (`IEProperties.FACING_HORIZONTAL` выставляется
  в `clickDirection.getOpposite()` в `IGTemplateMultiblock.replaceStructureBlock`).
* `masterFromOrigin` - ячейка, становящаяся мастер-блок-сущностью; `triggerFromOrigin` - ячейка, по
  которой игрок бьёт молотком. Обе должны быть не-воздушными.
* Используйте только состояния блоков, однозначно переживающие поворот и зеркало. В поставляемых
  шаблонах это: кубы без свойств (`immersiveengineering:light_engineering`, `heavy_engineering`,
  `rs_engineering`, `sheetmetal_steel`, `storage_steel`, `immersivegeology:storage_block_*`,
  `engineering_block_*`, `sheetmetal_block_*`), `fluid_pipe`/`steel_scaffolding_standard`
  (`waterlogged=false`), нижние плиты (`type=bottom, waterlogged=false`) и `minecraft:hopper`
  (`facing=down, enabled=true`). Направленные блоки (ступени, заборы) встречаются лишь там, где
  математика поворота того стоит - в новых работах избегайте.

### 2.2 Как сделать

Два поддерживаемых пути:

1. **В игре**, ванильным structure block в режиме "save" (размером до вашего), затем скопировать файл
   по пути выше. Так сделаны исходные шаблоны.
2. **Из текстовой раскладки**, генератором, использованным для цепочки угольной ТЭЦ:
   `tools/tpp/structures.py`. Он собирает NBT из ASCII-слоёв (слои `y` из строк `z` из символов `x`,
   `.` = воздух) и проверяет мастер/триггер/размер. Запуск: `python3 tools/tpp/structures.py`.

После изменения шаблона перезапустите `tools/tpp/datagen_mirror.py` (или `runData`, §6): списки
частей разреза выводятся из шаблона.

---

## 3. Шаг 2 - OBJ-модель

Расположение: `src/main/resources/assets/immersivegeology/models/block/multiblock/obj/<name>/<name>.obj`
плюс соседний `<name>.mtl`.

### 3.1 Система координат (то, на чём прокалываются)

Координаты модели - **координаты структуры минус позиция мастера**:

```
model_pos = struct_pos - masterFromOrigin
```

то есть ячейка мастер-блока занимает `[0,1]^3`, ячейки в сторону +X/+Y/+Z положительны, а по другую
сторону мастера - отрицательны. Примеры из поставляемых моделей: `ballmill` (размер 5x4x4, мастер
(2,0,1)) занимает x [-2,3], y [0,4), z [-1,4); `bulk_blast_furnace` (3x8x3, мастер (1,0,1)) -
x [-1,2], y [0,8], z [-1,2]. Ошибётесь здесь - разрезанная модель раскидает части по чужим ячейкам,
и сформированная машина будет выглядеть как взорванный куб.

### 3.2 Материалы и MTL

Ровно **один** материал с именем мультиблока:

```
newmtl <name>
map_Kd #<name>
```

`map_Kd #<name>` - это *ключ текстуры*: датаген подставляет настоящую текстуру для каждого
скина/активной формы (`IGBlockStateProvider.obj(...)` передаёт
`ImmutableMap.of(name, rl("block/multiblock/<name>/<skin>"))`). Машины без скинов могут указывать
текстуру напрямую, как шаровая мельница:

```
newmtl m_179a1da9-...
map_Kd immersivegeology:block/multiblock/ballmill/base
```

Форму `#key` используйте всегда, когда у машины есть активная форма или скины; абсолютный путь -
только для простых машин `genericmultiblock(...)` (так сделаны четыре машины ТЭЦ, кроме котла).

### 3.3 Правила геометрии

* Квады, обход против часовой стрелки если смотреть снаружи; грани `f v/vt/vn`; `s off`; один объект
  `o <name>`.
* UV в `[0,1]` по текстуре машины. Датаген грузит OBJ с **`flipV(true)`**, то есть ось v файла
  переворачивается при загрузке - экспортируйте из Blender/Blockbench с v от низа (по умолчанию) и
  не переворачивайте заранее.
* Держите каждую грань **внутри своей ячейки** либо ровно на границе ячейки с соседом, который
  визуально её продолжает. Геометрия, торчащая на 0.02 в соседнюю сплошную ячейку, даёт z-fighting
  с её собственными гранями после разреза. (Процедурный генератор в `tools/tpp/models.py` добавляет
  выступающие детали только на гранях, соседняя ячейка которых воздушна.)
* Бюджет: поставляемые машины - ~200-2300 граней (`reverberation_furnace` 192, `steam_turbine` 299,
  `coredrill` 2250). Держитесь ниже ~2500; `scripts/obj_simplify.py` убирает дубликаты
  вершин/UV, если ваш редактор их дополняет.

### 3.4 Модели-заглушки

`tools/tpp/models.py` генерирует рабочие заглушки OBJ + MTL + текстуры для любой структуры из
`tools/tpp/structures.py` (блок на ячейку с деталировкой по коду блока: трубы, каркасы лесов,
бункера-воронки, колонны катушек, рёбра радиатора; плюс вариант текстуры `_active` со светящейся
топкой). Они существуют, чтобы цепочка была играбельна сразу; заменяются настоящим артом по §7 без
правки Java.

---

## 4. Шаг 3 - текстуры

Расположение: `src/main/resources/assets/immersivegeology/textures/block/multiblock/<name>/`.

| файл | когда используется |
|---|---|
| `default.png` | базовый скин / машины без скинов |
| `default_active.png` | машины со свойством `IEProperties.ACTIVE` (свечение топки и т.п.) |
| `<skin>.png` | по одной на запись enum-а скинов машины (`IGSteamTurbineSkins`, ...) |
| `<skin>_active.png` | машины одновременно со скинами и активной формой |

Соглашения:

* Степени двойки, обычно 128x128 или 256x256; острова UV в OBJ решают, что куда ляжет, поэтому
  раскладку держите в .blend/.bbmodel рядом с obj (см. §7).
* Имя материала в MTL обязано совпадать с ключом текстуры: `generateMultiskinMultiblock` привязывает
  `rl("block/multiblock/" + name + "/" + skinName + activeStr)` к ключу `<name>`.
* Скины регистрируются как предметы автоматически (`IGRegistrationHolder.registerMB(..., Skins.class)`
  создаёт предметы `<name>_multiblock_skin_<skin>`); enum скинов лежит в
  `common/block/multiblocks/skins/` и реализует `IIGMultiSkinHelper` (`multiblockName()`,
  `getSerializedName()`, авторство). Добавить скин = запись enum + `<skin>.png` (модель предмета
  сгенерируется).

---

## 5. Шаг 4 - регистрация и обвязка датагена

### 5.1 Java-регистрация (рантайм)

1. Логика: `common/block/multiblocks/logic/<Name>Logic.java`, реализующая `IMultiblockLogic<State>`
   плюс `IServerTickableComponent` / `IClientTickableComponent` / `MBOverlayText` по необходимости.
2. Форма: `common/block/multiblocks/shapes/<Name>Shape.java`, наследник `GenericShape`
   (AABB на ячейку в локальном пространстве `[0,1]`; минимальный пример - `AlternatorShape`).
3. Регистрация в `core/registration/IGMultiblockProvider.java`. Выбранный вариант **определяет, какой
   вариант blockstate должен выдать датаген**:

   | помощник | класс блока | свойства | вызов датагена |
   |---|---|---|---|
   | `metal_skinnable(...)` / `stone_skinnable(...)` | ваш подкласс `SkinableMultiblockPart` | `FACING_HORIZONTAL`, `MIRRORED`, enum скинов, опционально `ACTIVE` | `generateMultiskinMultiblock(block, name, hasActive, useActiveModels, hasMirror, useMirrorModels)` |
   | `stone(...)` | `NonMirrorableWithActiveBlock` | `FACING_HORIZONTAL`, `ACTIVE` | `generateActiveMultiblock(block, name)` |
   | `IGRegistrationHolder.registerMetalMultiblock(...)` / `mirroredStone(...)` | стандартный блок-часть IE | `FACING_HORIZONTAL`, `MIRRORED` | `genericmultiblock(name)` (или `genericmultiblockMirror(name)` для ручного зеркального obj) |

4. Класс шаблона: `common/block/multiblocks/IG<Name>Multiblock.java`, наследник `IGTemplateMultiblock`
   с `(loc, masterFromOrigin, triggerFromOrigin, size, IGMultiblockProvider.<REG>)`,
   `getManualScale()`, `initializeClient(...)` (смещение превью в мануале = половина footprint) и
   `getName()`.
5. Зарегистрируйте оба в `IGRegistrationHolder.initializeMultiblocks()`:
   `registerMB("<name>", IG<Name>Multiblock.INSTANCE, IGMultiblockProvider.<REG>[, Skins.class])`.

### 5.2 Датаген (ассеты)

В `IGBlockStateProvider.registerStatesAndModels()` добавьте вызов из таблицы выше. Что каждый делает:

* `generateMultiskinMultiblock` перебирает скины x active x mirrored x facing, строит
  `obj(<name>_<skin><_active><_mirrored>, rl("block/multiblock/obj/<name>/<model>.obj"), {name: skinTex})`
  и разрезает через `split(model, template, mirrored)`. `model` - это `<name>` плюс `_<skin>`, если
  `skin.alternativeModel()`, плюс `_active` / `_mirrored`, если так говорят флаги - то есть файл
  альтернативной модели скина называется, например, `bloomery_industrial_active.obj`.
* `generateActiveMultiblock` делает то же без скинов/зеркала, выдавая
  `models/block/<name>_split.json` и `<name>_active_split.json`.
* `genericmultiblock(name)` = `createMultiblock(innerObj("block/multiblock/obj/<name>/<name>.obj"), template)`:
  текстуру даёт абсолютный `map_Kd` из MTL, а зеркало обрабатывается обёрткой
  `immersiveengineering:mirror` вокруг разрезанной модели, если у блока есть `MIRRORED`.

JSON-ы разрезанных моделей ложатся в `src/generated/resources` и встраивают внутреннюю OBJ-модель
инлайном; blockstate затем поворачивает их по facing (`y = 0/90/180/270`, `uvlock: true`).

Предметная форма: `IGComplexItemModelProvider.registerModels()` - добавьте вызов, строящий
`TRSRModelBuilder` по тому же OBJ с display-трансформациями (`generateCoalChpModel(...)` - компактный
шаблон; угольный котёл дополнительно привязывает текстуру, потому что его MTL использует `#key`).

### 5.3 Анимированные части (опционально)

Машины с движущимися внутренностями (барабан мельницы, ротор турбины, колесо альтернатора) держат
подвижную геометрию во **втором OBJ** рядом со статичным (`.../obj/ballmill/drum.obj`), регистрируют
его в `IGDynamicModelProvider` (`customLoader(ObjModelBuilder::begin).modelLocation(...).flipV(true)`),
привязывают на клиенте в `client/ClientModBusEventHandlers.java`
(`BallmillRenderer.DRUM = new IGDynamicModel(BallmillRenderer.DRUM_NAME)`) и рисуют либо из
block entity renderer (`client/renderer/multiblocks/*Renderer.java`, регистрируется на мастер-BE),
либо в превью мануала через `IGClientMultiblockProperties.renderExtras(...)`
(см. `IGBallmillMultiblock.initializeClient`).

---

## 6. Получение `src/generated/resources`

Папка generated **закоммичена**, и CI собирает без запуска датагена, поэтому новый контент должен
существовать в обоих мирах:

* Предпочтительно: запустить run-конфиг датагена - `./gradlew runData`
  (вывод в `src/generated/resources/`, `--validate --existing src/main/resources`). Он перепишет всё,
  что выдают провайдеры, и обновит `src/generated/resources/.cache/*`; файлы, не перечисленные в кэше,
  не трогаются.
* Без dev-окружения Minecraft: `python3 tools/tpp/datagen_mirror.py` пишет ровно те файлы, которые
  провайдеры выдают для цепочки угольной ТЭЦ (blockstate, разрезанные модели, модели предметов,
  лут-таблицы, модели/теги материалов, рецепты). Расширяйте этот скрипт, а не правьте generated JSON
  руками.
* Никогда не кладите один и тот же относительный путь и в `src/main/resources`, и в
  `src/generated/resources`: `ProcessResources` использует `DuplicatesStrategy.FAIL`.

---

## 7. Замена модели-заглушки настоящим артом - чек-лист

1. Откройте/импортируйте шаблон структуры (§2) как блочный референс; моделируйте по нему.
2. Моделируйте в метрах с блоком-метром, затем сдвиньте начало координат в мастер-блок (§3.1).
   Держите перед машины в сторону -Z.
3. Один материал с именем `<name>`; `map_Kd #<name>`, если у машины скины/активные формы.
4. Разверните UV в `textures/block/multiblock/<name>/default.png` (и нарисуйте `default_active.png`,
   варианты `<skin>.png` по необходимости). Раскладку UV сохраните вместе с исходником модели.
5. Экспортируйте OBJ (+MTL): квады, `v/vt/vn`, `s off`; прогоните `scripts/obj_simplify.py`, если
   экспортёр паддит массивы; проверьте бюджет граней (§3.3).
6. Положите файлы в `models/block/multiblock/obj/<name>/` (перезаписав заглушку), сохранив имена,
   которые ждёт датаген (`<name>.obj`, плюс `<name>_active.obj`/`<name>_<skin>.obj` только если их
   ссылает вариант регистрации).
7. `./gradlew runData` (или расширьте `tools/tpp/datagen_mirror.py`), затем сформируйте машину в игре
   и проверьте: части попали в свои ячейки, нет z-fighting на общих гранях, активное состояние
   меняет текстуру, зеркало выглядит верно, превью мануала (`getManualScale`/смещения) кадрирует
   машину.

## 8. Рабочий пример

Цепочка угольной ТЭЦ (`coal_boiler`, `electrostatic_precipitator`, `steam_condenser`, `cooling_tower`,
`district_heater`) проходит все маршруты выше: активная незеркальная машина с MTL на `#key`
(угольный котёл), четыре зеркальные машины с абсолютными путями в MTL, процедурные заглушки геометрии
и текстур (`tools/tpp/models.py`), сгенерированные скриптом шаблоны (`tools/tpp/structures.py`) и
зеркалированный вывод датагена (`tools/tpp/datagen_mirror.py`). Раскладка их патрубков и реальные
числа станционных процессов описаны в [`COAL_CHP_CHAIN.md`](COAL_CHP_CHAIN.md).
