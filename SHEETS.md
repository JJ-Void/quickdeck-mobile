# Приёмник для Google-таблицы

Вставить в Apps Script таблицы (Расширения → Apps Script), сохранить,
развернуть как веб-приложение с доступом «все, у кого есть ссылка».

Приложение шлёт весь реестр целиком, скрипт перезаписывает четыре листа.
Ручные правки в этих листах затираются при следующей отправке — таблица тут архив,
а не вторая точка ввода.

```javascript
function doPost(e) {
  var db = JSON.parse(e.postData.contents);
  var ss = SpreadsheetApp.getActiveSpreadsheet();

  var byId = function (list) {
    var m = {};
    (list || []).forEach(function (x) { m[x.id] = x.name; });
    return m;
  };
  var customers = byId(db.customers);
  var contractors = byId(db.contractors);
  var sites = byId(db.sites);

  var status = {
    DRAFT: 'Черновик', WORK: 'В работе', WAIT: 'Ждёт приёмки',
    DONE: 'Сдан', OVERDUE: 'Просрочен', ARCHIVE: 'Архив'
  };

  write(ss, 'Заказчики',
    ['Наименование', 'ИНН', 'Контакт', 'Телефон', 'Примечание'],
    (db.customers || []).map(function (p) {
      return [p.name, p.inn, p.contact, p.phone, p.note];
    }));

  write(ss, 'Исполнители',
    ['Наименование', 'ИНН', 'Контакт', 'Телефон', 'Примечание'],
    (db.contractors || []).map(function (p) {
      return [p.name, p.inn, p.contact, p.phone, p.note];
    }));

  write(ss, 'Объекты',
    ['Объект', 'Адрес', 'Заказчик', 'Статус', 'Срок', 'Готовность, %', 'Примечание'],
    (db.sites || []).map(function (s) {
      return [s.name, s.address, customers[s.customerId] || '',
              status[s.status] || s.status, s.deadline, s.progress, s.note];
    }));

  write(ss, 'Договоры',
    ['Номер', 'Объект', 'Заказчик', 'Исполнитель', 'Сумма', 'Статус', 'Начало', 'Срок', 'Примечание'],
    (db.contracts || []).map(function (c) {
      return [c.number, sites[c.siteId] || '', customers[c.customerId] || '',
              contractors[c.contractorId] || '', c.amount,
              status[c.status] || c.status, c.start, c.end, c.note];
    }));

  return ContentService
    .createTextOutput(JSON.stringify({ ok: true, at: new Date().toISOString() }))
    .setMimeType(ContentService.MimeType.JSON);
}

function write(ss, name, head, rows) {
  var sh = ss.getSheetByName(name) || ss.insertSheet(name);
  sh.clear();
  sh.getRange(1, 1, 1, head.length).setValues([head]).setFontWeight('bold');
  if (rows.length) sh.getRange(2, 1, rows.length, head.length).setValues(rows);
  sh.setFrozenRows(1);
  sh.autoResizeColumns(1, head.length);
}
```

## Проверка

Ссылка отвечает только на POST. Если в приложении «Не вышло: Таблица ответила 401» —
развёртывание сделано с доступом «только я», надо пересоздать с доступом по ссылке.
