# FunTime Live

**FunTime Live** — локальная Windows-панель для интерактивных Minecraft-стримов. Она получает один webhook от EasTok, сопоставляет название TikTok-подарка с эффектом и отправляет команду на Paper-сервер через RCON.

Приложению не нужны домен, хостинг, проброс портов или облачный аккаунт: EasTok и FunTime Live работают на одном компьютере через `127.0.0.1`.

[English guide](README.md)

## Возможности

- Локальный webhook: `http://127.0.0.1:4782/eastok/gift`.
- Получение ника зрителя и названия подарка из EasTok.
- Настраиваемая карта «подарок → эффект».
- Отправка команд на Paper через RCON.
- Анимированный тёмный интерфейс со статусом соединения и журналом событий.
- Ручной тест подарка без запуска TikTok LIVE.
- Локальное сохранение пути к серверу и карты подарков.

## Схема работы

```text
TikTok LIVE
    -> триггер EasTok «Любой подарок»
    -> HTTP webhook на 127.0.0.1
    -> карта подарков FunTime Live
    -> RCON-команда
    -> Paper + плагин FunTimeItems
```

## Что потребуется

- Windows 10 или Windows 11.
- Java 21+; для Minecraft 1.21.11 рекомендуется Java 25.
- Запущенный Paper-сервер с включённым RCON.
- Плагин `FunTimeItems.jar` в папке `plugins` сервера.
- EasTok, подключённый к TikTok LIVE.

## Быстрый запуск готовой версии

1. Положи `FunTimeLive.jar` и `FunTimeLive.bat` в одну папку.
2. Запусти Paper-сервер.
3. Открой `FunTimeLive.bat`.
4. В приложении проверь поле `SERVER.PROPERTIES`: там должен быть путь к `server.properties` твоего сервера.
5. Нажми `Reload server`, затем `Test RCON`. Статус должен стать `RCON: connected`.
6. Настрой EasTok по инструкции ниже.

Приложение читает `rcon.password` и `rcon.port` локально из `server.properties`. Пароль не выводится в интерфейс или журнал.

## Настройка Paper

Помести плагин сюда:

```text
<папка Paper-сервера>/plugins/FunTimeItems.jar
```

После этого **полностью перезапусти сервер**. `/reload` для плагинов не используй.

В `server.properties` должны быть строки:

```properties
enable-rcon=true
rcon.port=25575
rcon.password=длинный_секретный_пароль
```

## Настройка EasTok

Создай **один** триггер «Любой подарок». В нём добавь действие `HTTP Webhook` и заполни поля:

| Поле | Значение |
| --- | --- |
| URL адрес | `http://127.0.0.1:4782/eastok/gift` |
| Метод | `POST` |
| Тело запроса JSON | `{"user":"{nickname}","gift":"{gift_name}"}` |
| Заголовки JSON | `{"Content-Type":"application/json"}` |

Кнопка `Тест` в EasTok отправляет шаблонный текст `{gift_name}`, а не настоящее название подарка. FunTime Live ответит `200 OK`, но специально не запустит эффект. Это нормально. Настоящие эффекты проверяй через блок **Local gift test** в самом приложении.

## Чат TikTok внутри Minecraft

Создай второй триггер EasTok для сообщений чата TikTok. В нём добавь ещё одно действие `HTTP Webhook`:

| Поле | Значение |
| --- | --- |
| URL адрес | `http://127.0.0.1:4782/eastok/chat` |
| Метод | `POST` |
| Тело запроса JSON | `{"user":"{nickname}","message":"{message}"}` |
| Заголовки JSON | `{"Content-Type":"application/json"}` |

В Minecraft сообщение появится как ` [TikTok] ник » текст `. FunTime Live ограничивает длину до 240 символов и убирает цветовые/управляющие коды перед отправкой в Paper.

## Карта подарков

В левой карточке одна строка означает одно правило:

```text
Rose=rose
Doughnut=donut
Coffee=speed
Gold Microphone=raid
Ice Cream Cone=freeze
Sunglasses=blind
Galaxy=chaos
```

Слева пишется название подарка от TikTok/EasTok, справа — id эффекта. Сопоставление не зависит от регистра и ищет совпадение внутри названия: правило `ice cream` подойдёт для `Ice Cream Cone`.

Основные id эффектов:

```text
rose, donut, heal, diamond, speed, freeze, blind, raid, animal,
creeper, rocket, crusher, trap, dragon, meteor, chaos, tornado,
fireworks, wolves, loot, ghost, wither
```

После изменения нажми `Save map`. Рядом с приложением появится личный файл `FunTimeLive-gifts.properties`; он уже исключён из Git.

## Проверка

1. Запусти Paper и FunTime Live.
2. Нажми `Test RCON`.
3. В **Local gift test** напиши, например, `Doughnut`.
4. Введи любой ник зрителя и нажми `Run effect`.
5. Проверь в Minecraft эффект и ответ сервера в `Activity Feed`.

## Если что-то не работает

| Проблема | Что проверить |
| --- | --- |
| `Webhook failed: Address already in use` | Закрой другое окно FunTime Live и запусти приложение снова. |
| `RCON: failed` | Запусти Paper, проверь `enable-rcon=true`, путь к `server.properties`, затем нажми `Reload server`. |
| Тест EasTok ничего не делает | Так и должно быть: он передаёт шаблон, а не реальный подарок. |
| Реальный подарок даёт запасной лут | Добавь точное название подарка в карту, сохрани и повтори тест. |
| Команда плагина неизвестна | Положи актуальный `FunTimeItems.jar` в `plugins` и перезапусти Paper. |

## Сборка из исходников

```bat
build.bat
run.bat
```

Используются только Java Standard Library и модуль `jdk.httpserver`; внешние зависимости не нужны.

## Приватность и безопасность

- Webhook слушает только `127.0.0.1`, а не локальную сеть и не интернет.
- Данные RCON остаются в `server.properties` Paper-сервера.
- Не загружай на GitHub файлы `FunTimeLive.properties` и `FunTimeLive-gifts.properties`.
- Никогда не открывай RCON-порт в интернет.
