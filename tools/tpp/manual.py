import json, os
base='src/main/resources/assets/immersivegeology/manual'
os.makedirs(base+'/en_us', exist_ok=True)
os.makedirs(base+'/ru_ru', exist_ok=True)

entries = {}

entries['coal_boiler'] = (
"""Pulverised Coal Boiler
The Heart of the Station
<&multiblock>The Coal Boiler is a §ldrum boiler§r fired with §lpulverised coal§r, exactly like the boiler house of a real thermal power plant. Grind your coal in a Ball Mill first: coal dust burns in suspension, hot and complete, while lump coal smoulders and loses several percent of its heating value into the ash pit.
<np>
§lLighting up§r
- Feed §lpulverised coal§r into the bunker on the §lcentre of the roof§r.
- Feed §lwater§r (or preheated network water, see the District Heater) into the §ldrum port on the trigger side§r, middle height.
- Power the §lfan and pump motors§r on the trigger side corner. Without power there is no draft and the fire suffocates.
- The furnace has §lthermal inertia§r: heat climbs while coal burns and falls slowly when it does not. Steam only appears once the fire is properly up.
<np>
§lOutputs§r
- §lSuperheated steam§r leaves through the §lfar side§r of the superheater band - pipe it to a Steam Turbine.
- §lFlue gas§r leaves through the §lfar side§r of the roof duct - pipe it to an Electrostatic Precipitator. If the gas has nowhere to go the boiler chokes on its own exhaust.
- §lBottom ash§r (slag) drops out of the hopper at the §lback of the foundation§r.
- If the drum cannot give its steam away the §lpressure§r rises and the §lsafety valve§r lifts, wasting steam. The control panel on the roof takes a redstone signal.""",
"""Пылеугольный котёл
Сердце станции
<&multiblock>Пылеугольный котёл — это §lбарабанный котёл§r, сжигающий §lугольную пыль§r, в точности как котельный агрегат настоящей ТЭЦ. Сначала размолите уголь в шаровой мельнице: пыль горит в взвешенном состоянии, жарко и полно, а кусковой уголь лишь тлеет, теряя несколько процентов теплоты со шлаком.
<np>
§lРастопка§r
- Подавайте §lугольную пыль§r в бункер §lв центре крыши§r.
- Подавайте §lводу§r (или подогретую сетевую воду, см. сетевой подогреватель) в §lбарабанный патрубок на стороне формирования§r, на средней высоте.
- Запитайте §lприводы вентиляторов и насосов§r на углу стороны формирования. Без питания нет тяги, и топка задыхается.
- У котла §lтепловая инерция§r: нагрев растёт, пока горит уголь, и медленно падает без огня. Пар появляется только на прогретой топке.
<np>
§lВыходы§r
- §lПерегретый пар§r выходит с §lпротивоположной стороны§r на уровне пароперегревателя - ведите его к паровой турбине.
- §lДымовые газы§r выходят с §lпротивоположной стороны§r крышевого газохода - ведите их к электрофильтру. Если газу некуда идти, котёл задыхается собственными газами.
- §lШлак§r высыпается из бункера §lсзади фундамента§r.
- Если пару некуда уйти, §lдавление§r растёт и поднимается §lпредохранительный клапан§r, теряя пар. Панель управления на крыше принимает редстоун-сигнал.""")

entries['electrostatic_precipitator'] = (
"""Electrostatic Precipitator
Cleaning the Flue Gas
<&multiblock>The Electrostatic Precipitator pulls the boiler's §lflue gas§r through three §lcorona electrode fields§r. The discharge charges the suspended fly ash, the charged dust drifts onto the collection plates, and the rapping gear shakes it down into the hoppers - a real plant recovers 99% of its ash this way.
<np>
§lOperation§r
- Dirty gas enters the §linlet duct on the far side§r, low down.
- The §lrectifier sets§r need power on the §ltrigger side§r. Unpowered fields capture nothing and the gas backs up, choking the boiler's draft - exactly like a real plant with its induced draft fan stopped.
- §lFly ash§r is discharged from the hoppers at the §lback of the foundation§r; pipe it away or let it drop into a chest.
- Cleaned gas leaves through the §lstack§r on the roof.
<np>
§lTip:§r Fly ash is a §lpozzolan§r - mixed into reinforced concrete it partially replaces the cement, which is precisely how real stations sell their ash.""",
"""Электрофильтр
Очистка дымовых газов
<&multiblock>Электрофильтр протягивает §lдымовые газы§r котла через три §lкоронирующих электродных поля§r. Коронный разряд заряжает взвешенную золу, заряженная пыль оседает на коллекторных пластинах, а встряхивающий механизм сбрасывает её в бункера - так настоящая станция улавливает 99% золы.
<np>
§lРабота§r
- Грязный газ входит в §lгазоход на дальней стороне§r, внизу.
- §lВыпрямительным агрегатам§r нужно питание на §lстороне формирования§r. Обесточенные поля ничего не улавливают, газ запирается и душит тягу котла - как на реальной станции с остановленным дымососом.
- §lЗола-унос§r выгружается из бункеров §lсзади фундамента§r; уведите её трубами или сыпьте в сундук.
- Очищенный газ уходит в §lдымовую трубу§r на крыше.
<np>
§lСовет:§r зола-унос - §lпуццолан§r: в армированном бетоне она частично заменяет цемент, именно так реальные станции продают свою золу.""")

entries['steam_condenser'] = (
"""Steam Condenser
The Cold End of the Cycle
<&multiblock>The Surface Condenser is where the steam cycle closes. Exhaust steam from the turbine gives up its §llatent heat§r to the tube bundle and drops into the hotwell as §lcondensate§r - that condensate is the boiler's feed water, so recovering it is what keeps the water-steam circuit closed.
<np>
§lPorts§r
- §lExhaust steam or turbine condensate§r enters at the §ltop of the far side§r.
- §lCirculating (cooling) water§r enters one §lflank§r and leaves the other as §lheated water§r on its way to the Cooling Tower. Condensing one bucket of steam needs several buckets of cooling water, as the real heat balance demands.
- §lCondensate§r (feed water) is pumped out of the §ltrigger side§r bottom - return it to the boiler.
- The condensate and circulating pumps need §lpower§r on the trigger side; without them the condenser floods and the vacuum is lost.""",
"""Поверхностный конденсатор
Холодный конец цикла
<&multiblock>Поверхностный конденсатор - точка, где замыкается паровой цикл. Отработанный пар турбины отдаёт §lскрытую теплоту§r трубному пучку и стекает в сборник как §lконденсат§r - этот конденсат и есть питательная вода котла, и его возврат замыкает водопаровой контур.
<np>
§lПатрубки§r
- §lОтработанный пар или конденсат турбины§r входит §lсверху дальней стороны§r.
- §lЦиркуляционная (охлаждающая) вода§r входит с одного §lбока§r и выходит с другого §lподогретой§r, направляясь к градирне. На конденсацию одного ведра пара нужно несколько вёдер циркуляционной воды - как и в реальном тепловом балансе.
- §lКонденсат§r (питательная вода) выкачивается §lснизу стороны формирования§r - верните его в котёл.
- Конденсатным и циркуляционным насосам нужно §lпитание§r на стороне формирования; без них конденсатор переполняется и вакуум теряется.""")

entries['cooling_tower'] = (
"""Cooling Tower
The Station's Breath
<&multiblock>The Cooling Tower is a §lnatural draft hyperbolic tower§r: heated water from the condenser is sprayed over the §lfill packing§r near the top, air drawn in through the §llouvers§r at the base rises up the shell and evaporates a small fraction of the flow - and that evaporation is what carries the heat away. The cooled water collects in the §lbasin§r and returns to the condenser.
<np>
§lPorts§r
- §lHeated water§r from the condenser enters the §ltrigger side§r at mid height.
- §lCooled water§r is drawn from the §lbasin§r on the trigger side, at ground level.
- §lMake-up water§r enters the basin from a §lflank§r: about two percent of the flow leaves the tower as evaporation and drift, and a real basin must be topped up or the tower runs dry.
<np>
§lWeather matters:§r the tower needs no power at all - its performance is set by the atmosphere. Cold dry air and altitude improve evaporation, rain and humid heat choke it. Watch the plume: it is the station breathing.""",
"""Градирня
Дыхание станции
<&multiblock>Градирня - §lбашенная испарительная градирня с естественной тягой§r: подогретая вода из конденсатора разбрызгивается на §lороситель§r в верхней части, воздух, входящий через §lжалюзи§r у основания, поднимается по оболочке и испаряет малую долю потока - именно это испарение и уносит тепло. Охлаждённая вода собирается в §lводосборном бассейне§r и возвращается в конденсатор.
<np>
§lПатрубки§r
- §lПодогретая вода§r из конденсатора входит на §lстороне формирования§r на средней высоте.
- §lОхлаждённая вода§r забирается из §lбассейна§r на стороне формирования, у земли.
- §lПодпиточная вода§r входит в бассейн с §lбока§r: около двух процентов потока покидает башню с испарением и уносом, и реальный бассейн необходимо подпитывать, иначе градирня иссякнет.
<np>
§lПогода важна:§r башне вовсе не нужно питание - её производительность задаёт атмосфера. Холодный сухой воздух и высота улучшают испарение, дождь и влажная жара душат его. Следите за шлейфом: это станция дышит.""")

entries['district_heater'] = (
"""District Heater
What Makes It a CHP Plant
<&multiblock>The District Heater is a §lnetwork water heater§r - the cogeneration stage. Steam §lbled from the turbine§r condenses on the tube bundle and gives its latent heat to the §ldistrict heating network water§r, which leaves as hot supply water while the condensate returns to the feed water system.
<np>
Selling heat this way is what lifts a real cogeneration plant's fuel utilisation from about forty to seventy percent and more: the same coal that makes electricity also heats the town.
<np>
§lPorts§r
- §lExtraction steam§r enters at the §ltop of the far side§r.
- §lNetwork return water§r enters one §lflank§r; §lhot supply water§r leaves the other.
- §lCondensate§r is pumped out of the §ltrigger side§r bottom - it is clean, warm feed water.
- The §lnetwork circulation pumps§r need power on a flank; the control panel takes redstone.""",
"""Сетевой подогреватель
То, что делает станцию ТЭЦ
<&multiblock>Сетевой подогреватель - §lподогреватель сетевой воды§r, ступень когенерации. Пар, §lотобранный из турбины§r, конденсируется на трубном пучке и отдаёт скрытую теплоту §lсетевой воде§r, которая уходит горячей подачей, а конденсат возвращается в питательный тракт.
<np>
Продажа тепла таким способом поднимает использование топлива реальной когенерационной станции с примерно сорока до семидесяти и более процентов: тот же уголь, что даёт электричество, греет город.
<np>
§lПатрубки§r
- §lТеплофикационный отбор§r входит §lсверху дальней стороны§r.
- §lОбратная сетевая вода§r входит с одного §lбока§r; §lгорячая подача§r выходит с другого.
- §lКонденсат§r выкачивается §lснизу стороны формирования§r - это чистая тёплая питательная вода.
- §lСетевым циркуляционным насосам§r нужно питание с бока; панель управления принимает редстоун.""")

entries['coal_chp_chain'] = (
"""The Coal CHP Chain
From Seam to Socket and Radiator
This entry walks the whole chain, in the order a real coal fired combined heat and power plant runs it.
<np>
§l1. Fuel preparation.§r Mine §lcoal§r (lignite, bituminous or anthracite), crush it, then grind it to §lpulverised coal§r in a §lBall Mill§r. Dust burns in suspension; lump coal does not.
§l2. Combustion.§r The §lCoal Boiler§r burns the dust with feed water and makes §lsuperheated steam§r, §lflue gas§r and §lbottom ash§r. It needs power for its fans and pumps - a boiler without draft is a cold boiler.
§l3. Gas cleaning.§r The §lElectrostatic Precipitator§r pulls the flue gas through corona fields, recovers §lfly ash§r for the concrete industry and vents clean gas up its stack.
<np>
§l4. Generation.§r Steam drives the §lSteam Turbine§r, whose shaft spins the §lAlternator§r - that is your electricity.
§l5. The cold end.§r Exhaust steam condenses in the §lSteam Condenser§r; the §lCooling Tower§r cools the circulating water that carries the latent heat away; condensate returns to the boiler as feed water.
§l6. Cogeneration.§r Steam bled from the cycle heats network water in the §lDistrict Heater§r - hot water for the town, and the reason a CHP plant burns less coal per useful unit than a plain power station.
<np>
§lTip:§r return the District Heater's hot water to the boiler as §lpreheated feed water§r and the boiler rewards you with extra efficiency - regenerative feed water heating, the cheapest efficiency win a real station has.""",
"""Цепочка угольной ТЭЦ
От пласта до розетки и радиатора
Эта статья проводит по всей цепочке в том порядке, в котором её выполняет реальная угольная теплоэлектроцентраль.
<np>
§l1. Топливоподготовка.§r Добудьте §lуголь§r (бурый, каменный или антрацит), раздробите его и размолите в §lугольную пыль§r в §lшаровой мельнице§r. Пыль горит в взвешенном состоянии; кусковой уголь - нет.
§l2. Сжигание.§r §lПылеугольный котёл§r сжигает пыль с питательной водой и даёт §lперегретый пар§r, §lдымовые газы§r и §lшлак§r. Ему нужно питание для вентиляторов и насосов - котёл без тяги это холодный котёл.
§l3. Очистка газов.§r §lЭлектрофильтр§r протягивает дымовые газы через коронирующие поля, улавливает §lзолу-унос§r для цементной промышленности и выпускает чистый газ в трубу.
<np>
§l4. Генерация.§r Пар крутит §lпаровую турбину§r, чей вал вращает §lальтернатор§r - это ваше электричество.
§l5. Холодный конец.§r Отработанный пар конденсируется в §lповерхностном конденсаторе§r; §lградирня§r охлаждает циркуляционную воду, уносящую скрытую теплоту; конденсат возвращается в котёл питательной водой.
§l6. Когенерация.§r Пар, отобранный из цикла, греет сетевую воду в §lсетевом подогревателе§r - горячая вода для города и причина, по которой ТЭЦ жжёт меньше угля на полезную единицу, чем простая конденсационная станция.
<np>
§lСовет:§r верните горячую воду сетевого подогревателя в котёл как §lподогретую питательную воду§r, и котёл ответит прибавкой КПД - регенеративный подогрев питательной воды, самая дешёвая прибавка эффективности реальной станции.""")

for name,(en,ru) in entries.items():
    with open(f'{base}/{name}.json','w',encoding='utf-8') as fh:
        if name == 'coal_chp_chain':
            fh.write('{}')
        else:
            json.dump({"multiblock": {"type": "multiblock", "name": f"immersivegeology:multiblocks/{name}"}}, fh, indent=2)
    with open(f'{base}/en_us/{name}.txt','w',encoding='utf-8') as fh:
        fh.write(en)
    with open(f'{base}/ru_ru/{name}.txt','w',encoding='utf-8') as fh:
        fh.write(ru)
    print('wrote manual', name)
