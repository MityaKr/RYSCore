# Как подключить RYSCore к моду семьи

Кор это отдельный мод. Его нельзя копировать к себе: два мода с одним Java-пакетом не запускаются вместе.

**gradle.properties**

```
ryscore_version=1.4.0
```

**Репозиторий** (рядом с остальными `repositories`). Папка появляется после `gradlew publish` в RYSCore.

```groovy
maven {
    name = 'RYS family'
    url = rootProject.file('../RYSCore/repo')
    content { includeGroup 'works.repka.ryscore' }
}
```

**Зависимости**

```groovy
implementation "works.repka.ryscore:ryscore-neoforge:${ryscore_version}"
jarJar("works.repka.ryscore:ryscore-neoforge:[${ryscore_version},2.0.0)") {
    version { prefer ryscore_version }
    transitive = false
}
```

**neoforge.mods.toml**

```toml
[[dependencies.${mod_id}]]
modId = "ryscore"
type = "required"
versionRange = "[${ryscore_version},2.0.0)"
ordering = "AFTER"
side = "BOTH"
```

Значение `ryscore_version` нужно добавить в свойства `processResources`.

**Использование**

```java
Theme theme = ThemeManager.get();
theme.panel(g, x, y, w, h);
Draw.text(g, "Текст", x, y, theme.c("text.primary"));
```

Перезагрузкой тем занимается сам кор. Выбранная тема одна на все моды.

Своя тема или набор: `assets/<ns>/ryscore_themes/<имя>.json`, поля `name, font, colors, metrics, effects`.

**Точность по макету**

Игра должна совпадать с HTML макета. Окно 1280×800 при GUI Scale 2: одна единица GUI это два пикселя макета, поэтому все размеры из макета делятся на два. У блоков без `box-sizing: border-box` внешний размер больше на рамку: слот 48 с рамкой 1 это 50.

```java
Draw.label(g, "Заголовок", x, centerY, 20, Type.Weight.SEMIBOLD, theme.c("text.primary")); // кегль как в макете, y это середина строки
float w = Draw.labelWidth("Заголовок", 20, Type.Weight.SEMIBOLD);                          // только так, font.width даёт другую ширину
```

Проверка: снимок игры того же размера накладывается на отрисованный макет в режиме разницы (`mix-blend-mode: difference`). Совпавшее становится чёрным, расхождения видны сразу.

Правила вида и контракт: [../CLAUDE.md](../CLAUDE.md). Нужно новое в коре: напиши автору блок «Запрос к RYSCore».
