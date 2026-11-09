# История RYSCore

## 1.3.0

`team.TeamCards`: карточки сводки на экране команды. Запасные токены `hud.fg` и `tooltip.fg` (запрос RYSQuests).

## 1.2.0

`team.TeamsApi`: команды для модов семьи. Без RYSTeams каждый игрок соло, его команда это его UUID. Подписка на вступление, уход, распад и слияние с предпросмотром и откатом.

## 1.1.0

Вид приведён к макетам. `Theme`: `sidebar`, `slots` (много ячеек одним вызовом через новый `ui.Tiles`), `slot` с рамкой состояния, `tabLine`, `tabLineFg`, поля `lineTabs`, `flushSidebar`. `Draw`: `magnifier`, `cross`, `star`, `dot`, мягкая тень без ступенек. Токены `slot.have.border`, `slot.miss.border`, метрики `tab.style: underline`, `sidebar.flush`. В Slate активные вкладки больше не янтарные: акцент оставлен главным действиям.

## 1.0.0

Первая версия, перенесено из RYScope: `Theme`, `ThemeManager`, `Draw`, `Loading`, темы Slate, Vanilla, Glass. Папка тем теперь `ryscore_themes`, выбор темы общий для всех модов: `config/ryscore/theme.json`.
