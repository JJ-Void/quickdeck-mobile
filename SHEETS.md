# Связь с Google-таблицей — в обе стороны

Таблица здесь не архив, а вторая точка ввода. Что поменял в телефоне —
видно в таблице. Что поменял в таблице — приезжает в телефон.

## Как договариваются

У каждой записи есть скрытый идентификатор (столбец `ID`) и метка времени
(столбец `Изменено`). При обмене побеждает та версия, которую правили позже.
Никакого сервера посередине нет — вся логика в этих двух столбцах.

Когда ты правишь ячейку руками, скрипт сам ставит в `Изменено` текущее
время. Поэтому ручная правка всегда выигрывает у старой версии в телефоне.

Удаление — это не вырезанная строка, а галочка `да` в столбце `Удалено`.
Если строку просто стереть в таблице, она вернётся при следующем обмене:
телефон-то про удаление не знает. Хочешь убрать запись — ставь `да`
(или удаляй в приложении, тогда `да` проставится само).

Столбец `ID` скрыт — он машинный, трогать его не нужно. Новую строку можно
завести прямо в таблице, оставив `ID` пустым: скрипт выдаст идентификатор сам.

В `Объектах` и `Договорах` стороны указываются **именем**. Если написать имя,
которого ещё нет, скрипт заведёт такого заказчика или исполнителя сам.

## Установка

1. В таблице: Расширения → Apps Script.
2. Стереть всё, что там есть, вставить код ниже, сохранить.
3. Развернуть → Новое развёртывание → тип **Веб-приложение**.
4. «Запуск от имени» — от моего имени. «Доступ» — **Все, у кого есть ссылка**.
5. Скопировать ссылку, которая кончается на `/exec`, и вставить в приложении.

Проверить можно просто открыв эту ссылку в браузере — должно ответить
строкой с количеством записей.

```javascript
var STAMP = 'Изменено';
var DEL = 'Удалено';

var LAYOUT = {
  customers: {
    sheet: 'Заказчики',
    head: ['ID', 'Наименование', 'ИНН', 'Контакт', 'Телефон', 'Примечание', DEL, STAMP]
  },
  contractors: {
    sheet: 'Исполнители',
    head: ['ID', 'Наименование', 'ИНН', 'Контакт', 'Телефон', 'Примечание', DEL, STAMP]
  },
  sites: {
    sheet: 'Объекты',
    head: ['ID', 'Объект', 'Адрес', 'Заказчик', 'Статус', 'Срок', 'Готовность, %', 'Примечание', DEL, STAMP]
  },
  contracts: {
    sheet: 'Договоры',
    head: ['ID', 'Номер', 'Объект', 'Заказчик', 'Исполнитель', 'Сумма', 'Статус', 'Начало', 'Срок', 'Примечание', DEL, STAMP]
  }
};

var STATUS_LABEL = {
  DRAFT: 'Черновик', WORK: 'В работе', WAIT: 'Ждёт приёмки',
  DONE: 'Сдан', OVERDUE: 'Просрочен', ARCHIVE: 'Архив'
};

// --- вход ----------------------------------------------------------------

function doGet() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var n = function (key) { return readRows(ss, LAYOUT[key]).length; };
  return ContentService
    .createTextOutput('QuickDeck на связи. Объектов: ' + n('sites') +
      ', договоров: ' + n('contracts') +
      ', заказчиков: ' + n('customers') +
      ', исполнителей: ' + n('contractors'))
    .setMimeType(ContentService.MimeType.TEXT);
}

function doPost(e) {
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(30000);
  } catch (err) {
    return reply({ ok: false, error: 'Таблица занята другим обменом, попробуй ещё раз' });
  }
  try {
    var body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    var phone = body.db || {};
    var ss = SpreadsheetApp.getActiveSpreadsheet();

    // Стороны — первыми: на них ссылаются объекты и договоры.
    var customers = mergeList(readParties(ss, LAYOUT.customers), phone.customers);
    var contractors = mergeList(readParties(ss, LAYOUT.contractors), phone.contractors);

    var sheetSites = readRows(ss, LAYOUT.sites).map(function (r) {
      return {
        id: r.ID,
        name: text(r['Объект']),
        address: text(r['Адрес']),
        customerId: idByName(customers, r['Заказчик'], r[STAMP]),
        status: statusName(r['Статус']),
        deadline: toIso(r['Срок']),
        progress: intOf(r['Готовность, %']),
        note: text(r['Примечание']),
        deleted: flag(r[DEL]),
        updatedAt: stampOf(r[STAMP])
      };
    });
    var sites = mergeList(sheetSites, phone.sites);

    var sheetContracts = readRows(ss, LAYOUT.contracts).map(function (r) {
      return {
        id: r.ID,
        number: text(r['Номер']),
        siteId: siteIdByName(sites, r['Объект']),
        customerId: idByName(customers, r['Заказчик'], r[STAMP]),
        contractorId: idByName(contractors, r['Исполнитель'], r[STAMP]),
        amount: intOf(r['Сумма']),
        status: statusName(r['Статус']),
        start: toIso(r['Начало']),
        end: toIso(r['Срок']),
        note: text(r['Примечание']),
        deleted: flag(r[DEL]),
        updatedAt: stampOf(r[STAMP])
      };
    });
    var contracts = mergeList(sheetContracts, phone.contracts);

    stampAll(customers);
    stampAll(contractors);
    stampAll(sites);
    stampAll(contracts);

    write(ss, LAYOUT.customers, customers.map(function (p) {
      return [p.id, p.name, p.inn, p.contact, p.phone, p.note, p.deleted ? 'да' : '', new Date(p.updatedAt)];
    }));
    write(ss, LAYOUT.contractors, contractors.map(function (p) {
      return [p.id, p.name, p.inn, p.contact, p.phone, p.note, p.deleted ? 'да' : '', new Date(p.updatedAt)];
    }));
    write(ss, LAYOUT.sites, sites.map(function (s) {
      return [s.id, s.name, s.address, nameById(customers, s.customerId),
        STATUS_LABEL[s.status] || s.status, fromIso(s.deadline), s.progress,
        s.note, s.deleted ? 'да' : '', new Date(s.updatedAt)];
    }), ['Срок']);
    write(ss, LAYOUT.contracts, contracts.map(function (c) {
      return [c.id, c.number, nameById(sites, c.siteId), nameById(customers, c.customerId),
        nameById(contractors, c.contractorId), c.amount,
        STATUS_LABEL[c.status] || c.status, fromIso(c.start), fromIso(c.end),
        c.note, c.deleted ? 'да' : '', new Date(c.updatedAt)];
    }), ['Начало', 'Срок']);

    return reply({
      ok: true,
      at: new Date().toISOString(),
      db: {
        customers: customers,
        contractors: contractors,
        sites: sites,
        contracts: contracts
      }
    });
  } catch (err) {
    return reply({ ok: false, error: String(err && err.message ? err.message : err) });
  } finally {
    lock.releaseLock();
  }
}

/** Ручная правка ячейки — ставим метку времени, чтобы она выиграла обмен. */
function onEdit(e) {
  if (!e || !e.range) return;
  var sh = e.range.getSheet();
  var layout = layoutOf(sh.getName());
  if (!layout) return;

  var stampCol = layout.head.indexOf(STAMP) + 1;
  if (e.range.getColumn() === stampCol && e.range.getNumColumns() === 1) return;

  var from = Math.max(2, e.range.getRow());
  var to = e.range.getRow() + e.range.getNumRows() - 1;
  if (to < 2) return;

  var now = new Date();
  for (var r = from; r <= to; r++) {
    sh.getRange(r, stampCol).setValue(now);
    var idCell = sh.getRange(r, 1);
    if (!String(idCell.getValue()).trim()) idCell.setValue(Utilities.getUuid());
  }
}

// --- чтение --------------------------------------------------------------

function readRows(ss, layout) {
  var sh = ss.getSheetByName(layout.sheet);
  if (!sh) return [];
  var last = sh.getLastRow();
  if (last < 2) return [];

  var width = layout.head.length;
  var values = sh.getRange(2, 1, last - 1, width).getValues();
  var out = [];

  for (var i = 0; i < values.length; i++) {
    var row = values[i];
    var filled = false;
    for (var c = 1; c < width; c++) {
      if (String(row[c]).trim() !== '') { filled = true; break; }
    }
    if (!filled) continue;

    var obj = {};
    for (var k = 0; k < width; k++) obj[layout.head[k]] = row[k];

    if (!String(obj.ID).trim()) {
      obj.ID = Utilities.getUuid();      // строку завели руками
      if (!obj[STAMP]) obj[STAMP] = new Date();
    }
    obj.ID = String(obj.ID).trim();
    out.push(obj);
  }
  return out;
}

function readParties(ss, layout) {
  return readRows(ss, layout).map(function (r) {
    return {
      id: r.ID,
      name: text(r['Наименование']),
      inn: text(r['ИНН']),
      contact: text(r['Контакт']),
      phone: text(r['Телефон']),
      note: text(r['Примечание']),
      deleted: flag(r[DEL]),
      updatedAt: stampOf(r[STAMP])
    };
  });
}

// --- слияние -------------------------------------------------------------

function mergeList(fromSheet, fromPhone) {
  var byId = {};
  var order = [];
  var add = function (r) {
    if (!r || !r.id) return;
    if (!byId[r.id]) { byId[r.id] = r; order.push(r.id); return; }
    if (numOf(r.updatedAt) > numOf(byId[r.id].updatedAt)) byId[r.id] = r;
  };
  (fromSheet || []).forEach(add);
  (fromPhone || []).forEach(add);
  return order.map(function (id) { return byId[id]; });
}

function stampAll(list) {
  var now = Date.now();
  list.forEach(function (r) { if (!numOf(r.updatedAt)) r.updatedAt = now; });
}

// --- связи по имени ------------------------------------------------------

function idByName(list, name, when) {
  var n = String(name == null ? '' : name).trim();
  if (!n) return null;
  var low = n.toLowerCase();
  for (var i = 0; i < list.length; i++) {
    if (!list[i].deleted && String(list[i].name).trim().toLowerCase() === low) return list[i].id;
  }
  var made = {
    id: Utilities.getUuid(), name: n, inn: '', contact: '', phone: '', note: '',
    deleted: false, updatedAt: stampOf(when) || Date.now()
  };
  list.push(made);
  return made.id;
}

function siteIdByName(list, name) {
  var n = String(name == null ? '' : name).trim();
  if (!n) return null;
  var low = n.toLowerCase();
  for (var i = 0; i < list.length; i++) {
    if (!list[i].deleted && String(list[i].name).trim().toLowerCase() === low) return list[i].id;
  }
  return null;
}

function nameById(list, id) {
  if (!id) return '';
  for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i].name || '';
  return '';
}

// --- запись --------------------------------------------------------------

function write(ss, layout, rows, dateCols) {
  var sh = ss.getSheetByName(layout.sheet) || ss.insertSheet(layout.sheet);
  var width = layout.head.length;

  sh.clear();
  sh.getRange(1, 1, 1, width).setValues([layout.head]).setFontWeight('bold');
  if (rows.length) sh.getRange(2, 1, rows.length, width).setValues(rows);
  sh.setFrozenRows(1);

  if (rows.length) {
    var stampCol = layout.head.indexOf(STAMP) + 1;
    sh.getRange(2, stampCol, rows.length, 1).setNumberFormat('dd.MM.yyyy HH:mm');
    (dateCols || []).forEach(function (title) {
      var col = layout.head.indexOf(title) + 1;
      if (col > 0) sh.getRange(2, col, rows.length, 1).setNumberFormat('dd.MM.yyyy');
    });
  }

  sh.autoResizeColumns(1, width);
  sh.hideColumns(1);   // ID — машинный, человеку не нужен
}

// --- мелочи --------------------------------------------------------------

function layoutOf(sheetName) {
  for (var key in LAYOUT) if (LAYOUT[key].sheet === sheetName) return LAYOUT[key];
  return null;
}

function reply(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function text(v) { return v == null ? '' : String(v).trim(); }

function flag(v) {
  var s = String(v == null ? '' : v).trim().toLowerCase();
  return s === 'да' || s === 'true' || s === '1' || s === 'x' || s === '+';
}

function numOf(v) { var n = Number(v); return isNaN(n) ? 0 : n; }

function intOf(v) {
  if (v === '' || v == null) return 0;
  var n = parseInt(String(v).replace(/[^\d-]/g, ''), 10);
  return isNaN(n) ? 0 : n;
}

function stampOf(v) {
  if (v instanceof Date) return v.getTime();
  if (typeof v === 'number') return v;
  var s = String(v == null ? '' : v).trim();
  if (!s) return 0;
  var t = Date.parse(s);
  return isNaN(t) ? 0 : t;
}

function statusName(v) {
  var s = String(v == null ? '' : v).trim();
  if (!s) return 'DRAFT';
  if (STATUS_LABEL[s]) return s;
  for (var key in STATUS_LABEL) {
    if (STATUS_LABEL[key].toLowerCase() === s.toLowerCase()) return key;
  }
  return 'DRAFT';
}

function tz() { return SpreadsheetApp.getActiveSpreadsheet().getSpreadsheetTimeZone(); }

function pad(v, n) {
  var s = String(v);
  while (s.length < n) s = '0' + s;
  return s;
}

function toIso(v) {
  if (v instanceof Date) return Utilities.formatDate(v, tz(), 'yyyy-MM-dd');
  var s = String(v == null ? '' : v).trim();
  if (!s) return '';
  var m = s.match(/^(\d{1,2})[.\/-](\d{1,2})[.\/-](\d{2,4})$/);
  if (m) {
    var y = parseInt(m[3], 10);
    if (y < 100) y += 2000;
    return pad(y, 4) + '-' + pad(m[2], 2) + '-' + pad(m[1], 2);
  }
  m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (m) return m[1] + '-' + pad(m[2], 2) + '-' + pad(m[3], 2);
  return '';
}

function fromIso(iso) {
  var s = String(iso == null ? '' : iso).trim();
  var m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (!m) return '';
  return new Date(parseInt(m[1], 10), parseInt(m[2], 10) - 1, parseInt(m[3], 10));
}
```

## Если что-то не так

**«Доступ закрыт (401/403)»** — развёртывание сделано с доступом «только я».
Пересоздать: Развернуть → Управление развёртываниями → карандаш → Доступ:
«Все, у кого есть ссылка».

**«Ссылка не найдена (404)»** — взят адрес `/dev` вместо `/exec`.

**Правка в таблице не доехала** — посмотри столбец `Изменено` в этой строке.
Если он пустой, значит `onEdit` не отработал: открой Apps Script → Триггеры и
убедись, что скрипт сохранён под тем же проектом, что и таблица.

**Строка вернулась после удаления** — её удалили из таблицы вместо того, чтобы
поставить `да` в столбце `Удалено`.
