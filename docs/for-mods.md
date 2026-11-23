# RYSCore для авторов сборок и модов

Документ описывает ядро **1.17.0** (Minecraft 1.21.1, NeoForge). Всё ниже взято из кода этой версии.

RYSCore это общее ядро семьи модов RYS: темы, окна, уведомления, панель кнопок, колесо действий и контракты, через которые моды семьи находят друг друга. Само по себе ядро ничего не добавляет в игру. Оно вложено в jar каждого мода семьи, отдельно ставить его не нужно.

- [Авторам сборок](#авторам-сборок): своя тема, файлы настроек, переезд с FTB.
- [Авторам модов](#авторам-модов): как подключить ядро, политика совместимости.
- [Контракты](#контракты): что ядро даёт модам и что бывает без реализации.

---

## Авторам сборок

### Своя тема

Тема это один файл JSON в ресурспаке или в ресурсах мода:

```
assets/<namespace>/ryscore_themes/<имя>.json
```

- id темы складывается из пути: `<namespace>:<имя>`. Поле `id` внутри файла ядро не читает.
- Все найденные темы попадают в общий список, одна тема действует на все моды семьи сразу. Встроенные идут первыми (`ryscore:slate`, `ryscore:vanilla`, `ryscore:glass`), остальные по id.
- Темы перечитываются вместе с ресурсами: включение ресурспака или F3+T. Перезапуск не нужен.
- Файл с тем же путём в ресурспаке выше по списку заменяет прежний. Так можно поправить и встроенную тему, например `assets/ryscore/ryscore_themes/slate.json`.
- Битый файл пропускается, в лог пишется `Не удалось прочитать тему …`.

Тема меняет только цвета, размеры и эффекты. Раскладку окон тема не меняет: дерево виджетов у всех тем одно.

#### Поля файла

| Поле | Что значит |
|---|---|
| `name` | Имя темы для игрока. Без него показывается id. |
| `font` | `"ryscore:inter"`: текст окон рисуется шрифтом Inter из ядра. Любое другое значение (во встроенной Vanilla Refined `"minecraft:default"`) или отсутствие поля: шрифт игры. Других шрифтов ядро 1.17 не знает. |
| `colors` | Цвета токенов (ниже). |
| `metrics` | Радиусы, фаска, вид вкладок. |
| `effects` | Тень и анимации. |

#### Цвета

- Значение `"#rrggbb"` (непрозрачный) или `"#aarrggbb"` (с прозрачностью).
- Число в ключе `<токен>.alpha` (от 0 до 1) умножает прозрачность цвета `<токен>`. Если такого токена в файле нет, берётся `<токен>.bg`: `"panel.alpha": 0.9` относится к `panel.bg`. Сам цвет должен быть задан в этом же файле.
- Незаданный токен берётся по цепочке запасных из кода ядра (`Theme.FALLBACK`). Например `card.bg` → `panel.bg`, `text.dim` → `text.muted` → `text.body` → `text.primary`, `state.warn` → `accent`. Поэтому короткая тема из десятка цветов уже рабочая.
- Цепочки сходятся к корневым токенам. Их стоит задать всегда: `panel.bg`, `panel.border`, `slot.bg`, `text.primary`, `accent`, `state.order` (цвет ссылок), `overlay`. Без значения у корня токен рисуется ярко-розовым `#ff00ff`, так пропуск сразу виден. Исключения со встроенным значением: `overlay` (затемнение), рамки (`panel.border` и родственные становятся прозрачными), `plate.bg` и `plate.border` (светло-серая подложка под чужие рецепты).
- У полотна карты, колеса действий и плашек уведомлений запасные заданы готовыми тёмными цветами: эти места тёмные в любой теме. Переопределять их не обязательно.

Основные группы токенов (полный набор с значениями смотрите во встроенной `slate.json` в jar ядра):

| Группа | Токены |
|---|---|
| Окна | `panel.bg`, `panel.border`, `sidebar.bg`, `column.bg`, `card.bg`, `card.border`, `row.alt`, `row.selected.bg`, `overlay` |
| Текст | `text.primary`, `text.body`, `text.secondary`, `text.muted`, `text.dim`, `link` |
| Акцент и состояния | `accent`, `accent.text`, `state.ok`, `state.warn`, `state.bad`, `state.order`, `callout.bg`, `callout.border` |
| Слоты и ячейки | `slot.bg`, `slot.border`, `slot.hover`, `slot.have.border`, `slot.miss.border`, `cell.bg`, `cell.border`, `plate.bg`, `plate.border` |
| Кнопки, поля, вкладки | `button.bg`, `button.border`, `button.fg`, `input.bg`, `input.border`, `tab.active.bg`, `tab.active.fg`, `tab.inactive.bg`, `tab.inactive.fg`, `filter.active.bg`, `scroll.thumb` |
| Чипы и подсказки | `chip.mod.*`, `chip.fn.*`, `chip.tag.*` (`.bg`, `.fg`), `tooltip.bg`, `tooltip.border` |
| Поверх мира | `hud.bg`, `hud.border`, `hud.fg`, `hud.muted`, `wheel.*` (колесо действий) |
| Карта и награды | `map.bg`, `map.grid`, `map.draft`, `map.label.bg`, `map.label.fg`, `map.node.done`, `map.node.open`, `map.node.locked`, `medal.bronze`, `medal.silver`, `medal.gold`, `coin`, `cat.1` … `cat.12` |
| Фаска (тема с `bevel`) | `panel.bevel.light`, `panel.bevel.dark`, `slot.bevel.light`, `slot.bevel.dark` |

#### Размеры (`metrics`)

Радиусы задаются в пикселях макета (окно 1280×800, две единицы на пиксель интерфейса), не больше 40.

| Ключ | Что значит |
|---|---|
| `panel.radius`, `card.radius`, `slot.radius`, `button.radius`, `chip.radius`, `cell.radius`, `hud.radius` | Скругления окон, карточек, слотов, кнопок, чипов, ячеек списка, плашек поверх мира. |
| `tab.style` | `"pill"`: активная вкладка на пилюле, как в Glass. Любое другое значение (`"underline"`): линия акцента под активной, как в Slate. |
| `sidebar.flush` | `1`: боковая панель прижата к краю экрана на всю высоту. |
| `bevel` | Больше нуля: тема с фаской, как Vanilla Refined (без скруглений, выпуклые панели, закладки вместо вкладок). |
| `panel.bevel` | Толщина фаски окна в пикселях макета (по умолчанию 4). |
| `missing.alpha` | Прозрачность недостающего ингредиента. Читает RYSIndex. |

Ключи `slot.size` и `slot.gap` во встроенных темах ядро 1.17 не читает.

#### Эффекты (`effects`)

| Ключ | Что значит |
|---|---|
| `shadow` | `true`: тень под окнами и подсказками. |
| `animations` | `"normal"`: обычные движения. Любое другое значение (`"reduced"`): анимация внутри карточки уведомления сразу показана в конечном виде. |

Поле `blur` есть во встроенных темах, ядро 1.17 его не читает.

#### Пример: тёмная тема на основе Slate

Файл `assets/mypack/ryscore_themes/ember.json`, id темы `mypack:ember`. Цвета, которых здесь нет, возьмутся по цепочке запасных.

<!-- doc-example:theme (проверяет ThemeDocExampleTest) -->
```json
{
  "name": "Ember",
  "font": "ryscore:inter",
  "colors": {
    "panel.bg": "#1f1b18",
    "panel.border": "#3a312a",
    "sidebar.bg": "#181512",
    "card.bg": "#26211d",
    "card.border": "#40362e",
    "slot.bg": "#141110",
    "slot.border": "#2e2722",
    "slot.hover": "#352d27",
    "button.bg": "#2c2520",
    "button.border": "#40362e",
    "text.primary": "#faf6f2",
    "text.body": "#ebe4dc",
    "text.secondary": "#c2b8ad",
    "text.muted": "#968b80",
    "text.dim": "#6b6158",
    "accent": "#ff8a3d",
    "accent.text": "#1a1410",
    "state.ok": "#6fcf8f",
    "state.warn": "#e8894a",
    "state.bad": "#e36b6b",
    "state.order": "#6aa8ff",
    "tab.active.bg": "#3a312a",
    "tab.active.fg": "#faf6f2",
    "tooltip.bg": "#181512",
    "tooltip.border": "#40362e",
    "overlay": "#0e0c0a",
    "overlay.alpha": 0.55
  },
  "metrics": {
    "panel.radius": 12,
    "card.radius": 14,
    "slot.radius": 6,
    "button.radius": 9,
    "chip.radius": 6,
    "cell.radius": 7,
    "tab.style": "underline",
    "sidebar.flush": 1,
    "bevel": 0
  },
  "effects": {
    "shadow": true,
    "animations": "normal"
  }
}
```

Чтобы тема стояла у игроков с первого запуска, положите в сборку `config/ryscore/theme.json` (ниже).

### Файлы настроек

Ядро хранит настройки игрока в `config/ryscore/` папки игры. Они общие для всех миров и серверов. Сборка может положить свои файлы как начальные значения: в файле можно указать только нужные поля, остальные возьмутся по умолчанию. Игра перезаписывает файл, когда игрок меняет настройку, поэтому при обновлении сборки такие файлы лучше не заменять, иначе выбор игроков сбросится.

**`theme.json`**: выбранная тема.

```json
{ "theme": "mypack:ember" }
```

Тема с неизвестным id не ломает игру: берётся `ryscore:slate`.

**`ui.json`**: вид интерфейса.

<!-- doc-example:ui (проверяет UiDocExampleTest) -->
```json
{
  "scale": 0,
  "mapCorner": "TOP_RIGHT",
  "mapPercent": 25,
  "corners": { "PINNED_QUESTS": "TOP_LEFT", "INVITES": "BOTTOM_RIGHT" },
  "noticeSound": true,
  "noticeVolume": 100,
  "noticePlace": "RIGHT"
}
```

| Поле | Значения |
|---|---|
| `scale` | Размер окон семьи: `0` (авто), `75`, `100`, `125`, `150`. Не зависит от GUI Scale игры. |
| `mapCorner` | Угол, который занимает миникарта: `TOP_LEFT`, `TOP_RIGHT` (по умолчанию), `BOTTOM_LEFT`, `BOTTOM_RIGHT`. Постоянные элементы семьи этот угол обходят. Если стоит RYSMap, угол задаёт он по выбору игрока. |
| `mapPercent` | Высота зоны карты в процентах высоты экрана, по умолчанию 25. |
| `corners` | Углы постоянных элементов: `STICKERS`, `PINNED_QUESTS` (по умолчанию `TOP_LEFT`), `INVITES`, `TEAM_CARD` (по умолчанию `BOTTOM_RIGHT`). |
| `noticeSound`, `noticeVolume` | Звук уведомлений семьи и его громкость от 0 до 100. |
| `noticePlace` | Где появляются уведомления: `RIGHT` (под зоной карты, по умолчанию), `CENTER` (под боссбарами), `LEFT`. |
| `seen` | Разовые подсказки, которые игрок уже видел. Значение `["keys"]` выключает карточку «Клавиши семьи» при первом входе. |

**`keys.json`**: свои клавиши игрока и колесо быстрых действий (клавиша колеса по умолчанию Ё, она же `` ` ``).

<!-- doc-example:keys (проверяет KeysDocExampleTest) -->
```json
{
  "version": 1,
  "bindings": { "rysmap:home": "key.keyboard.h+alt" },
  "rings": { "ryscore:mine": ["rysmap:home", "", "", "", "", "", "", ""] },
  "wheelMode": "hold"
}
```

| Поле | Значения |
|---|---|
| `bindings` | id действия → сочетание: имя клавиши игры (`key.keyboard.h`, `key.mouse.4`) и модификаторы `+ctrl`, `+shift`, `+alt` в этом порядке. Свои клавиши работают в мире без открытого окна. |
| `rings` | id кольца → восемь id действий, пустая строка это пустой сектор. Кольца ядра: `ryscore:mine`, `ryscore:moderation`, `ryscore:operator`, моды добавляют свои. Кольцо без записи берёт умолчания модов. |
| `wheelMode` | `"hold"`: держать клавишу, вести, отпустить над сектором. `"click"`: колесо открывается нажатием и ждёт клика. |

id действий объявляют моды (например `rysmap:home`). Кнопки панели семьи сами становятся действиями `ryscore:dock/<id кнопки>`. Полный список виден игроку в окне «Клавиши».

Рядом лежит служебный `retired-keys.txt`: ядро помнит в нём клавиши по умолчанию, которые мод снял (см. `KeyDefaults` ниже). Править его не нужно.

**`migrate.json`**: флаг переезда с FTB (следующий раздел).

### Переезд с FTB

Моды семьи умеют забрать данные FTB Teams, FTB Quests и FTB Chunks. Порядок, копию, отметки и отчёт ведёт ядро, перенос делает каждый мод своим шагом: `teams` (10), `quests` (20), `claims` (30), `map` (40).

Сами при запуске сервера шаги идут, только если сборка положила флаг `config/ryscore/migrate.json`:

<!-- doc-example:migrate (проверяет ThemeDocExampleTest) -->
```json
{ "v": 1, "from": "ftb", "steps": ["teams", "quests", "claims", "map"] }
```

- Шаг идёт, если он назван во флаге, ещё не сделан и сам считает себя нужным (данные FTB есть, мод FTB не загружен).
- Перед первым шагом ядро копирует `world/ftbteams`, `world/ftbchunks`, `world/ftbquests` и данные модов семьи в `world/rys/backup/ftb-<время>/`. Не удалось скопировать: шаги не идут.
- Файлы FTB не меняются и не удаляются. Шаг переносит только то, чего у мода ещё нет.
- Отметки лежат в `world/rys/migrate.json`, отчёт идёт в лог блоком `[rys-migrate]` и дописывается в `world/rys/migrate-report.txt`.
- Шаг, прошедший не целиком, отметку не получает и повторяется при следующем запуске.
- Другие поля флага читают сами шаги, их описывает мод шага.

Команды сервера (уровень оператора 4):

```
/rys migrate status        что сделано, что ждёт
/rys migrate run <шаг>     запустить шаг вручную, в том числе без флага
/rys migrate undo <шаг>    откатить шаг из копии, если мод это умеет
```

Шаг `map` клиентский: его запускает RYSMap у игрока, в командах сервера он не виден.

### Права

Права модов семьи объявлены узлами NeoForge PermissionAPI с именами вида `<мод>.<право>`, например `rysmap.place.manage`. Если на сервере стоит LuckPerms или другой обработчик прав, решает он. Иначе действует умолчание узла: оператор, модератор или любой игрок.

---

## Авторам модов

### Подключение

Ядро это отдельный мод с modid `ryscore`. Мод семьи вкладывает его в свой jar через jarJar и объявляет обязательную зависимость.

> **Состояние на 1.17.0.** Публичного maven-адреса у ядра пока нет. `gradlew publish` в репозитории ядра кладёт его в локальный maven-каталог `repo/`, оттуда его берут моды семьи. Пока адреса нет, подключиться можно только сборкой ядра из исходников.

**`gradle.properties`**

```properties
ryscore_version=1.17.0
```

**`build.gradle`**: репозиторий, зависимость и вложение (так подключён RYSTeams).

```groovy
repositories {
    maven {
        name = 'RYS family'
        url = rootProject.file('../RYSCore/repo')
        content { includeGroup 'works.repka.ryscore' }
    }
}

dependencies {
    implementation "works.repka.ryscore:ryscore-neoforge:${ryscore_version}"
    jarJar("works.repka.ryscore:ryscore-neoforge:[${ryscore_version},2.0.0)") {
        version { prefer ryscore_version }
        transitive = false
    }
}

processResources {
    // к остальным свойствам mods.toml
    def props = [ryscore_version: ryscore_version]
    inputs.properties props
    filesMatching('META-INF/neoforge.mods.toml') { expand props }
}
```

**`neoforge.mods.toml`**

```toml
[[dependencies.${mod_id}]]
modId = "ryscore"
type = "required"
versionRange = "[${ryscore_version},2.0.0)"
ordering = "AFTER"
side = "BOTH"
```

Правила:

- **Ядро нельзя копировать к себе.** Ни классы, ни пакет `works.repka.ryscore`, ни затенение (shadow). Два мода с одним Java-пакетом не запускаются вместе: NeoForge падает с `ResolutionException`.
- **Несколько модов в сборке.** Каждый вкладывает свою версию ядра, загрузчик выбирает одну, самую новую из подходящих всем диапазонам. Поэтому нижняя граница диапазона это версия, на которой мод собран, а верхняя `2.0.0`.
- **Потоки и стороны.** Клиентские контракты зовутся из главного потока клиента, серверные из потока сервера. Регистрация в реестрах ядра (действия, кнопки, слои, права) делается при запуске, порядок загрузки модов не важен.

Простейшее использование темы:

```java
Theme theme = ThemeManager.get();
theme.panel(g, x, y, w, h);
Draw.label(g, "Заголовок", x, centerY, 20, Type.Weight.SEMIBOLD, theme.c("text.primary"));
```

Цвета берутся только токенами темы (`theme.c(...)`), скругления только примитивами `Theme` и `Draw`: тогда окно мода меняется вместе с темой игрока.

### Совместимость 1.x

- В 1.x ядро только добавляет: публичные классы, методы, токены тем и форматы файлов не убираются и не меняют смысл. Несовместимая правка возможна только в 2.0.
- Новый токен темы всегда получает запасной в цепочке: старые темы не ломаются.
- Данные контрактов это классы со строителями (`Marker.builder(...)`) или фабриками (`Status.of(...)`), а не записи: новое поле не меняет конструктор и не роняет старые моды.
- **Исключение: контракты с `@Preview`** (`ClaimsApi`, `MapApi`, `TravelApi`, `Labels`). До выпуска 1.0 мода, который их реализует (RYSClaims, RYSMap), они могут меняться с записью в `CHANGELOG.md` ядра. Правило «только добавляем» включается для такого контракта, когда у него три потребителя.

---

## Контракты

Контракт ядра это интерфейс или реестр, через который моды семьи говорят друг с другом, не зная друг о друге. Один мод реализует контракт (`provide`), остальные пользуются (`get`). Без реализации у каждого контракта есть безопасное поведение по умолчанию, поэтому код мода не ветвится на «стоит ли сосед». Пакеты ниже от `works.repka.ryscore`.

### `team.TeamsApi`: команды

Сервер. Реализует RYSTeams. Без него каждый игрок играет соло: его команда имеет тот же UUID, что и он сам, `can` всегда разрешает, ленты нет.

- `TeamsApi.get().teamOf(player)`, `members(team)`, `leader(team)`, `allies(team)`, `name(team)`, `color(team)`: состав и вид команды.
- `can(player, permission)`: проверка права. Своё право мод объявляет `registerPermission("mymod:claim", label, Set.of(TeamsApi.LEADER, TeamsApi.OFFICER))`, строку в таблице прав рисует RYSTeams. Роли команды: `LEADER`, `OFFICER`, `MEMBER`, `GUEST`.
- `TeamsApi.listen(listener)`: вступление, уход, роспуск, слияние и его отмена, смена роли, союза, имени. Здесь мод переносит свои данные.

### `claims.ClaimsApi`: земля и прогрузка (`@Preview`)

Сервер. Реализует RYSClaims. Без него земли нет: `ownerAt` отвечает `null`, `can` и `canMachine` разрешают всё, лимиты 0, `present()` ложь.

- `ownerAt(dimension, chunkX, chunkZ)`: чья земля (UUID команды).
- `can(player, dimension, pos, Action)` и `canMachine(dimension, from, profile, target, Action)`: можно ли игроку или машине строить, открывать, нажимать, трогать сущности.
- `addBonus(team, claims, forceLoaded)`, `addPlayerBonus(player, …)`: добавки к лимиту, например награда квеста. Подписка на занятие и освобождение чанков: `ClaimsApi.listen`.

### `map.MapApi`: карта (`@Preview`)

Клиент. Реализует RYSMap. Без него `present()` ложь, `open` и `track` ничего не делают, `drawTiles` ничего не рисует и отвечает ложью. Реестры ядро хранит само: мод может регистрировать слои и пункты меню до загрузки RYSMap.

- `MapApi.get().layer(layer)`: свой слой меток и областей (`Marker.builder(id)…build()`).
- `menu(item)`, `menuGroup(group)`, `status(id, order, line)`, `mode(...)`, `tab(...)`: пункты меню по правой кнопке, строки под миникартой, режимы полотна, вкладки окна карты.
- `open(target)`, `track(target)`: открыть карту на месте, вести игрока к цели. `drawTiles(...)`: фон тайлов карты в чужом окне.

### `travel.TravelApi`: перемещения (`@Preview`)

Сервер. Реализует RYSMap: пауза по причине, разогрев, безопасная посадка, история «назад», проверка привата. Без него ядро переносит игрока простым телепортом без пауз и истории, `present()` ложь.

- `TravelApi.get().teleport(player, spot, reason)`: короткая форма. Полная: `teleport(player, Trip.to(spot).reason(...).skipCooldown().build())`. Итог `Result`: `DONE`, `WARMUP`, `PAUSE` или `DENIED` с причиной.
- `cooldown(player, reason)`: сколько секунд паузы осталось.
- `rememberBack(player, from, reason)`: записать место в историю «назад» без перемещения.

Причины `Reason.HOME`, `PLACE`, `BACK`, `STAFF` и другие, свою мод заводит `Reason.of("…")`. Всё, что телепортирует игрока в семье, идёт через этот контракт.

### `links.ItemLinks`: сведения о предмете

Клиент. Поставщик: мод, который знает что-то о предметах (RYSQuests: награды, сделки магазина, задачи). Потребитель: просмотрщик рецептов (RYSIndex). Без поставщиков просмотрщик ведёт себя так, будто сведений нет.

- `ItemLinks.register(provider)`: регистрация при запуске клиента.
- `Provider.cards(stack, Role.GIVES | Role.TAKES)`: карточки «как получить» и «где тратится», строитель `ItemLinks.Card.builder(group, id, title)`.
- `Provider.mentions(stack)`: упоминания для чипа в шапке карточки предмета. `version()` растёт, когда ответы могли измениться.

Поставщик сам решает, что игроку видно, и отдаёт готовые тексты без цветов. Ответы идут сразу, из готового индекса.

### `perm.Perms` и `perm.Roles`: права сервера

Сервер. Без RYSHelm умолчание узла даёт его роль по умолчанию, оператор это игрок с уровнем 2 и выше, модераторов нет. RYSHelm ставит таблицу ролей (`Perms.policy`) и решает, кто модератор (`Roles.provide`).

- `Perms.node("mymod", "place.manage", Roles.MODERATOR, label)`: объявить узел в конструкторе мода, до запуска сервера. Он становится узлом PermissionAPI `mymod.place.manage`, поэтому LuckPerms видит его.
- `node.test(player)`: проверка. Уровень оператора напрямую моды не спрашивают.
- `Roles.of(player)`, `Roles.atLeast(player, Roles.MODERATOR)`: роль сервера (`OPERATOR`, `MODERATOR`, `PLAYER`). Это роли сервера, не роли команды.

### `world.Labels`: подписи в мире (`@Preview`)

Клиент. Рисует RYSMap: значок, имя, расстояние, сквозь стены, общий лимит и отсечка по расстоянию. Пока реализации нет (`Labels.present()` ложь), мод рисует свои метки сам.

- `Labels.source(source)`: свой источник меток (`id()`, `labels(dimension)`).
- `Labels.ping(label)`: пинг, ядро хранит его до срока жизни. Своего слоя пингов мод не заводит.
- `Labels.visible(dimension, x, y, z)`: готовый отбор для реализации (лимит 24, отсечка 512 блоков по умолчанию).

### `action.*`: действия, колесо, клавиши, ответы

Клиент, реализация в ядре. Колесо быстрых действий (клавиша Ё), окно «Клавиши» со своими сочетаниями игрока и конфликтами, ответы на запросы клавишами Y и N.

- `Actions.register(Action.builder(id).label(...).icon(...).group(Action.Group.PLACES).state(() -> Action.State.ready()).run(...).build())`: действие мода. Состояние (готово, пауза, недоступно с причиной) считает мод, колесо только показывает. Меняющийся список (каждое место, каждый дом): `Actions.provider(id, supplier)`. Короткое имя для тесного сектора: `Builder.shortLabel` или перевод `<ключ>.short`.
- `Actions.ring(Ring.builder(id)…)`, `Actions.suggest(ring, action)`: своё кольцо или подсказка в чужое. Перемещения собраны в набор `Actions.TRAVEL` («Перенести…»).
- Ответы: запрос строится `Notice.buttons(source, id).accept(label, run).decline(label, run)`, клавиши Y и N ядро отдаёт самому новому такому уведомлению (`action.Answers`). Своих клавиш ответа моды не заводят.
- `KeyDefaults.retire(id, key, "key.keyboard.g")`: снять клавишу по умолчанию у тех, кто уже играл. Без этого у них останется старая: игра хранит все клавиши в `options.txt`.

### `migrate.Migrations`: переезд с FTB

Сервер (и клиент для шага карты). Реализация в ядре, шаги дают моды. Как это видит сборка, описано [выше](#переезд-с-ftb).

- `Migrations.register(step)`: шаг мода с `id()`, `order()`, `side()`, `needed(context)`, `run(context)` и, если умеет, `undo(context)`.
- `run` читает и считает в памяти, пишет базу мода одним сохранением, возвращает `Report` (`skipped`, `partial`). Повтор шага обязан быть безопасным.
- Файлы FTB читаются только `migrate.Snbt`. Клиентский шаг мод запускает сам: `Migrations.runClient(gameDir, stateDir, values)`.

### `dock.Dock`: панель кнопок

Клиент, реализация в ядре. Цветные плитки модов семьи рядом с инвентарём. Без FTB Library панель стоит слева от инвентаря: её ставит мод-хозяин (`Dock.setHost`, сейчас RYSIndex) или само ядро. С FTB Library и без хозяина своей панели нет: мод кладёт кнопку в панель FTB своим файлом `sidebar_buttons`. Хозяин может заменить панель FTB своей, тогда кнопки FTB встают в неё после кнопок семьи.

```java
DockButton.builder("mymod_book").label(Component.translatable("mymod.dock"))
    .tile(0xFFE9A83A).icon(Icons.Shape.BOOK).key(MyKeys.OPEN).order(0)
    .action(BookScreen::open).register();
```

- `DockButton.builder(id)…register()`: кнопка с плиткой, значком (`icon` или белая текстура 42×42), клавишей в подсказке и действием.
- `replacesFtb("ftbquests_quests")`: спрятать кнопку FTB, которую заменяет мод.
- Каждая кнопка панели сама становится действием колеса `ryscore:dock/<id>`.

### `ui.Notices`: уведомления

Клиент, реализация в ядре. Каждое уведомление это настоящий тост игры, поверх мира и окон, в теме игрока, с паузой при наведении, кликом и звуком. Место выбирает игрок: справа под зоной карты, по центру или слева.

- `Notice.card(source, id)`, `Notice.big(...)`, `Notice.row(...)`, `Notice.buttons(...)`: вид уведомления; дальше `.title(...)`, `.lines(...)`, `.item(...)`, `.action(...)`, `.sound(...)`, `.summary(...)` и `.show()`.
- Источник и id вместе это ключ: новое уведомление с тем же ключом заменяет видимое. `Notices.dismiss(source, id)` убирает его.
- Своих тостов и строк-уведомлений в других местах экрана моды семьи не рисуют.

### `ui.Anchors`: углы экрана

Клиент, реализация в ядре. Где стоят постоянные элементы семьи на игровом экране и какой угол занят картой.

- `Anchors.place(element, w, h, marginX, marginY)`: место элемента (`STICKERS`, `PINNED_QUESTS`, `INVITES`, `TEAM_CARD`) в его углу с учётом зоны карты.
- `Anchors.mapCorner()`, `Anchors.topInset(corner)`: угол карты и нижний край её зоны. Карта сообщает свой угол `Anchors.setMapCorner`.
- Размер чужой миникарты не учитывается: зона карты это доля высоты экрана (`mapPercent` в `ui.json`).

---

Лицензия ядра: код под MPL-2.0, графика и имя RYS принадлежат автору (`NOTICE.md`). Историю версий смотрите в `CHANGELOG.md`.
