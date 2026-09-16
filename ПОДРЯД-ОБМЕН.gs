/**
 * ПОДРЯД — обмен таблицы с мобильным приложением.
 *
 * Главное правило: скрипт НИКОГДА не вызывает clear() и не пишет в
 * формульные столбцы. Он трогает ровно те ячейки, значение которых
 * изменилось, и только в ручных столбцах:
 *
 *   Объекты    B–I         (A, K, L — формулы, не трогаем)
 *   Договора   B–H, J–W    (A, I, Y, AB–AT — формулы, не трогаем)
 *   Сотрудники A–F, H, I   (правее — служебная зона, не трогаем)
 *   Сдача      только чтение
 *   Заказчики  только чтение (карточка со склейками, писать в неё нельзя)
 *
 * Идентификаторы записей таблица не хранит, поэтому скрипт держит их в
 * служебном скрытом листе «_ПОДРЯД»: лист, строка, id, время правки и
 * отпечаток строки. По отпечатку видно, правили ли строку руками после
 * прошлого обмена — тогда версия таблицы считается свежей.
 *
 * Разворачивается как веб-приложение: Развернуть → Новое развёртывание →
 * Веб-приложение → запуск от моего имени → доступ «у всех» → Развернуть.
 * Полученную ссылку /exec вставить в приложении: Настройки → адрес таблицы.
 */

var SH_SITES = 'Объекты';
var SH_CONTRACTS = 'Договора';
var SH_STAFF = 'Сотрудники';
var SH_HANDOVER = 'Сдача';
var SH_CUSTOMERS = 'Заказчики';
var SH_REFS = 'Справочники';
var SH_MAP = '_ПОДРЯД';

/** Первая строка данных на каждом листе. Выше — шапка и подсказка. */
var FIRST_ROW = { 'Объекты': 5, 'Договора': 5, 'Сдача': 5, 'Сотрудники': 5, 'Заказчики': 4 };

/** Номера столбцов (1 = A). Всё, чего тут нет, скрипт не читает и не пишет. */
var C_SITE = { name: 2, full: 3, customer: 4, address: 5, btype: 6, area: 7, unit: 8, note: 9 };
var C_SITE_WRITE = [2, 3, 4, 5, 6, 7, 8, 9];

var C_CON = {
  site: 2, kind: 3, legal: 4, status: 5, price: 6, start: 7, end: 8,
  resp: 10, co1: 11, co2: 12, co3: 13,
  p1: 14, p1s: 15, p1d: 16, p2: 17, p2s: 18, p2d: 19, p3: 20, p3s: 21, p3d: 22,
  note: 23, label: 39
};
var C_CON_WRITE = [2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 17, 18, 20, 21, 23];

var C_STAFF = { dept: 1, name: 2, pos: 3, tab: 4, employment: 5, location: 6, rate: 7, contact: 8, note: 9 };
var C_STAFF_WRITE = [1, 2, 3, 4, 5, 6, 8, 9];

var C_HAND = { label: 2, num: 3, kind: 4, what: 5, type: 6, plan: 7, fact: 8, status: 9, resp: 10, note: 11 };

/** Русская подпись статуса → имя константы, как его ждёт приложение. */
var STATUS = {
  'Потенциальный': 'POTENTIAL',
  'Запрошены исходные данные': 'INPUT_REQUESTED',
  'Выезд / обследование назначено': 'SURVEY_SCHEDULED',
  'Расчёт КП': 'QUOTE_CALC',
  'КП направлено': 'QUOTE_SENT',
  'КП на рассмотрении у заказчика': 'QUOTE_REVIEW',
  'Торг / пересчёт цены': 'PRICE_TALKS',
  'КП согласовано': 'QUOTE_APPROVED',
  'Подготовка договора': 'DRAFTING',
  'Договор на согласовании с юристом': 'LEGAL_REVIEW',
  'Договор направлен': 'CONTRACT_SENT',
  'Разногласия по договору': 'DISPUTES',
  'Корректировка договора': 'AMENDING',
  'Договор подписан': 'SIGNED',
  'Счёт на аванс выставлен': 'ADVANCE_INVOICED',
  'Аванс получен': 'ADVANCE_PAID',
  'Ожидание исходных данных': 'AWAITING_INPUT',
  'В работе': 'IN_WORK',
  'Внутренняя проверка (нормоконтроль)': 'INTERNAL_CHECK',
  'Готово к передаче': 'READY_TO_HAND',
  'Документация передана': 'HANDED',
  'На проверке у заказчика': 'CLIENT_REVIEW',
  'Получены замечания': 'REMARKS',
  'Устранение замечаний': 'FIXING',
  'Повторно передана': 'REHANDED',
  'Передана на экспертизу (ГГЭ)': 'EXPERTISE',
  'Замечания экспертизы': 'EXPERTISE_REMARKS',
  'Положительное заключение получено': 'EXPERTISE_OK',
  'Акт направлен': 'ACT_SENT',
  'Акт подписан': 'ACT_SIGNED',
  'Сдан': 'DELIVERED',
  'Счёт на окончательный расчёт выставлен': 'FINAL_INVOICED',
  'Оплачен частично': 'PAID_PART',
  'Оплачен полностью': 'PAID_FULL',
  'Гарантийный период': 'WARRANTY',
  'Приостановлен': 'SUSPENDED',
  'Ожидание решения заказчика': 'AWAITING_CLIENT',
  'Просрочен': 'OVERDUE',
  'Претензия / спор': 'CLAIM',
  'Отказ': 'REJECTED',
  'Расторжение договора': 'TERMINATED'
};

var HANDOVER_STATUS = {
  'Не начато': 'NOT_STARTED',
  'В работе': 'IN_WORK',
  'Готово к сдаче': 'READY',
  'Передано заказчику': 'HANDED',
  'На проверке у заказчика': 'CLIENT_REVIEW',
  'Получены замечания': 'REMARKS',
  'Устранение замечаний': 'FIXING',
  'Принято': 'ACCEPTED'
};

// --- точка входа ----------------------------------------------------------

function doPost(e) {
  var lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    var request = JSON.parse(e.postData.contents);
    var incoming = request.db || {};
    var db = sync(incoming);
    return json({ ok: true, db: db });
  } catch (err) {
    return json({ ok: false, error: String(err && err.message ? err.message : err) });
  } finally {
    lock.releaseLock();
  }
}

function doGet() {
  return json({ ok: true, db: sync({}) });
}

function json(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

// --- служебный лист с идентификаторами ------------------------------------

function mapSheet() {
  var ss = SpreadsheetApp.getActive();
  var sh = ss.getSheetByName(SH_MAP);
  if (!sh) {
    sh = ss.insertSheet(SH_MAP);
    sh.appendRow(['Лист', 'Строка', 'id', 'Изменено', 'Отпечаток']);
    sh.hideSheet();
  }
  return sh;
}

/** Карта вида map['Объекты:7'] = {id, updatedAt, hash, sheetRow}. */
function readMap() {
  var sh = mapSheet();
  var last = sh.getLastRow();
  var out = {};
  if (last < 2) return out;
  var rows = sh.getRange(2, 1, last - 1, 5).getValues();
  for (var i = 0; i < rows.length; i++) {
    var key = rows[i][0] + ':' + rows[i][1];
    out[key] = {
      id: String(rows[i][2]),
      updatedAt: Number(rows[i][3]) || 0,
      hash: String(rows[i][4] || ''),
      mapRow: i + 2
    };
  }
  return out;
}

function writeMapEntry(map, sheetName, row, id, updatedAt, hash) {
  var sh = mapSheet();
  var key = sheetName + ':' + row;
  var known = map[key];
  var values = [[sheetName, row, id, updatedAt, hash]];
  if (known && known.mapRow) {
    sh.getRange(known.mapRow, 1, 1, 5).setValues(values);
  } else {
    sh.appendRow(values[0]);
    known = { mapRow: sh.getLastRow() };
  }
  map[key] = { id: id, updatedAt: updatedAt, hash: hash, mapRow: known.mapRow };
}

// --- мелкие помощники -----------------------------------------------------

function text(v) {
  if (v === null || v === undefined) return '';
  if (v instanceof Date) return isoDate(v);
  return String(v).trim();
}

function isoDate(v) {
  if (!v) return '';
  if (v instanceof Date) return Utilities.formatDate(v, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  var s = String(v).trim();
  var m = s.match(/^(\d{1,2})[.\/](\d{1,2})[.\/](\d{4})$/);
  if (m) return m[3] + '-' + pad(m[2]) + '-' + pad(m[1]);
  return s;
}

function pad(n) { return (String(n).length < 2 ? '0' : '') + n; }

/** Обратно в вид, привычный таблице: 2026-09-01 → 01.09.2026. */
function ruDate(iso) {
  var m = String(iso || '').match(/^(\d{4})-(\d{2})-(\d{2})$/);
  return m ? m[3] + '.' + m[2] + '.' + m[1] : String(iso || '');
}

function num(v) {
  if (typeof v === 'number') return v;
  var s = String(v || '').replace(/[^\d,.-]/g, '').replace(',', '.');
  var n = parseFloat(s);
  return isNaN(n) ? 0 : n;
}

/** «60%» и 0.6 — одно и то же. */
function share(v) {
  if (typeof v === 'number') return v > 1 ? v / 100 : v;
  var s = String(v || '').trim();
  if (!s) return 0;
  var n = num(s);
  return s.indexOf('%') >= 0 || n > 1 ? n / 100 : n;
}

function hashOf(values) {
  var line = values.join('');
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.MD5, line, Utilities.Charset.UTF_8);
  var out = '';
  for (var i = 0; i < bytes.length; i++) {
    var b = bytes[i] < 0 ? bytes[i] + 256 : bytes[i];
    out += (b < 16 ? '0' : '') + b.toString(16);
  }
  return out;
}

function uuid() { return Utilities.getUuid(); }

function sheetOf(name) {
  var sh = SpreadsheetApp.getActive().getSheetByName(name);
  if (!sh) throw new Error('В таблице нет листа «' + name + '»');
  return sh;
}

/** Строки листа с данными: от первой строки данных до последней заполненной. */
function dataRows(name, width) {
  var sh = sheetOf(name);
  var first = FIRST_ROW[name];
  var last = sh.getLastRow();
  if (last < first) return { sheet: sh, first: first, rows: [] };
  var values = sh.getRange(first, 1, last - first + 1, width).getValues();
  return { sheet: sh, first: first, rows: values };
}

function pick(row, cols) {
  var out = [];
  for (var k in cols) out.push(text(row[cols[k] - 1]));
  return out;
}

// --- чтение таблицы -------------------------------------------------------

function readSites(map) {
  var data = dataRows(SH_SITES, 12);
  var out = [];
  for (var i = 0; i < data.rows.length; i++) {
    var r = data.rows[i];
    var rowNo = data.first + i;
    var name = text(r[C_SITE.name - 1]);
    if (!name) continue;
    var stamp = pick(r, C_SITE);
    out.push({
      row: rowNo,
      hash: hashOf(stamp),
      value: {
        code: text(r[0]),
        name: name,
        fullName: text(r[C_SITE.full - 1]),
        customerName: text(r[C_SITE.customer - 1]),
        address: text(r[C_SITE.address - 1]),
        buildingType: text(r[C_SITE.btype - 1]),
        area: num(r[C_SITE.area - 1]),
        unit: text(r[C_SITE.unit - 1]),
        note: text(r[C_SITE.note - 1]),
        row: rowNo
      }
    });
  }
  return out;
}

function readContracts(map) {
  var data = dataRows(SH_CONTRACTS, 40);
  var out = [];
  for (var i = 0; i < data.rows.length; i++) {
    var r = data.rows[i];
    var rowNo = data.first + i;
    var siteName = text(r[C_CON.site - 1]);
    var kind = text(r[C_CON.kind - 1]);
    if (!siteName && !kind) continue;
    var stamp = pick(r, C_CON);
    out.push({
      row: rowNo,
      hash: hashOf(stamp),
      siteName: siteName,
      value: {
        code: text(r[0]),
        siteName: siteName,
        workKind: kind,
        legalEntity: text(r[C_CON.legal - 1]),
        status: STATUS[text(r[C_CON.status - 1])] || 'POTENTIAL',
        amount: Math.round(num(r[C_CON.price - 1])),
        start: isoDate(r[C_CON.start - 1]),
        end: isoDate(r[C_CON.end - 1]),
        responsible: text(r[C_CON.resp - 1]),
        coExecutors: [text(r[C_CON.co1 - 1]), text(r[C_CON.co2 - 1]), text(r[C_CON.co3 - 1])]
          .filter(function (v) { return v; }),
        payments: [
          { condition: text(r[C_CON.p1 - 1]), share: share(r[C_CON.p1s - 1]), paid: !!text(r[C_CON.p1d - 1]) },
          { condition: text(r[C_CON.p2 - 1]), share: share(r[C_CON.p2s - 1]), paid: !!text(r[C_CON.p2d - 1]) },
          { condition: text(r[C_CON.p3 - 1]), share: share(r[C_CON.p3s - 1]), paid: !!text(r[C_CON.p3d - 1]) }
        ],
        note: text(r[C_CON.note - 1]),
        row: rowNo
      }
    });
  }
  return out;
}

function readStaff(map) {
  var data = dataRows(SH_STAFF, 9);
  var out = [];
  for (var i = 0; i < data.rows.length; i++) {
    var r = data.rows[i];
    var rowNo = data.first + i;
    var name = text(r[C_STAFF.name - 1]);
    if (!name) continue;
    var stamp = pick(r, C_STAFF);
    var contact = text(r[C_STAFF.contact - 1]);
    out.push({
      row: rowNo,
      hash: hashOf(stamp),
      value: {
        name: name,
        department: text(r[C_STAFF.dept - 1]),
        position: text(r[C_STAFF.pos - 1]),
        tabNumber: text(r[C_STAFF.tab - 1]),
        employment: text(r[C_STAFF.employment - 1]),
        location: text(r[C_STAFF.location - 1]),
        phones: phonesOf(contact),
        chats: chatsOf(contact),
        note: text(r[C_STAFF.note - 1]),
        row: rowNo
      }
    });
  }
  return out;
}

/** В столбце «Телефон / почта» бывает всё вперемешку — разбираем по виду. */
function phonesOf(s) {
  var parts = String(s || '').split(/[,;\n]+/);
  var out = [];
  for (var i = 0; i < parts.length; i++) {
    var p = parts[i].trim();
    if (!p || p.indexOf('@') >= 0) continue;
    if (p.replace(/\D/g, '').length >= 6) out.push(p);
  }
  return out;
}

function chatsOf(s) {
  var parts = String(s || '').split(/[,;\n]+/);
  var out = [];
  for (var i = 0; i < parts.length; i++) {
    var p = parts[i].trim();
    if (!p) continue;
    if (p.indexOf('@') > 0 && p.indexOf('.') > 0) out.push({ kind: 'email', handle: p });
    else if (p.charAt(0) === '@') out.push({ kind: 'telegram', handle: p });
  }
  return out;
}

function readHandovers() {
  var data = dataRows(SH_HANDOVER, 11);
  var out = [];
  for (var i = 0; i < data.rows.length; i++) {
    var r = data.rows[i];
    var what = text(r[C_HAND.what - 1]);
    if (!what) continue;
    out.push({
      row: data.first + i,
      contractLabel: text(r[C_HAND.label - 1]),
      value: {
        code: text(r[0]),
        what: what,
        kind: text(r[C_HAND.kind - 1]),
        type: text(r[C_HAND.type - 1]),
        plan: isoDate(r[C_HAND.plan - 1]),
        fact: isoDate(r[C_HAND.fact - 1]),
        status: HANDOVER_STATUS[text(r[C_HAND.status - 1])] || 'NOT_STARTED',
        responsible: text(r[C_HAND.resp - 1]),
        note: text(r[C_HAND.note - 1]),
        row: data.first + i
      }
    });
  }
  return out;
}

/** Заказчики читаются списком имён: карточка со склейками разбору не поддаётся. */
function readCustomers() {
  var sh = sheetOf(SH_CUSTOMERS);
  var first = FIRST_ROW[SH_CUSTOMERS];
  var last = Math.min(sh.getLastRow(), 628);
  if (last < first) return [];
  var values = sh.getRange(first, 2, last - first + 1, 1).getValues();
  var seen = {};
  var out = [];
  for (var i = 0; i < values.length; i++) {
    var name = text(values[i][0]);
    if (!name || seen[name]) continue;
    seen[name] = true;
    out.push({ name: name, row: first + i });
  }
  return out;
}

function readRefs() {
  var sh = SpreadsheetApp.getActive().getSheetByName(SH_REFS);
  if (!sh) return null;
  function column(a1) {
    return sh.getRange(a1).getValues()
      .map(function (r) { return text(r[0]); })
      .filter(function (v) { return v; });
  }
  var kinds = sh.getRange('A60:B81').getValues()
    .filter(function (r) { return text(r[0]); })
    .map(function (r) { return { name: text(r[0]), department: text(r[1]) }; });
  return {
    workKinds: kinds,
    buildingTypes: column('D94:D108'),
    units: column('B94:B100'),
    legalEntities: column('C94:C96'),
    departments: column('H94:H101'),
    payStages: column('A94:A103')
  };
}

// --- запись в таблицу -----------------------------------------------------

/**
 * Пишет только изменившиеся ячейки и только из разрешённого списка столбцов.
 * Остальное в строке остаётся ровно таким, каким было, включая формулы.
 */
function writeCells(sheet, row, allowed, values) {
  var changed = 0;
  for (var i = 0; i < allowed.length; i++) {
    var col = allowed[i];
    if (!(col in values)) continue;
    var cell = sheet.getRange(row, col);
    var now = cell.getValue();
    var next = values[col];
    if (text(now) === text(next)) continue;
    cell.setValue(next);
    changed++;
  }
  return changed;
}

function siteCells(site) {
  var v = {};
  v[C_SITE.name] = site.name || '';
  v[C_SITE.full] = site.fullName || '';
  v[C_SITE.customer] = site.customerName || '';
  v[C_SITE.address] = site.address || '';
  v[C_SITE.btype] = site.buildingType || '';
  v[C_SITE.area] = site.area || '';
  v[C_SITE.unit] = site.unit || '';
  v[C_SITE.note] = site.note || '';
  return v;
}

function contractCells(c, statusLabel) {
  var v = {};
  v[C_CON.site] = c.siteName || '';
  v[C_CON.kind] = c.workKind || '';
  v[C_CON.legal] = c.legalEntity || '';
  v[C_CON.status] = statusLabel;
  v[C_CON.price] = c.amount || '';
  v[C_CON.start] = ruDate(c.start);
  v[C_CON.end] = ruDate(c.end);
  v[C_CON.resp] = c.responsible || '';
  var co = c.coExecutors || [];
  v[C_CON.co1] = co[0] || '';
  v[C_CON.co2] = co[1] || '';
  v[C_CON.co3] = co[2] || '';
  var p = c.payments || [];
  var slots = [[C_CON.p1, C_CON.p1s, C_CON.p1d], [C_CON.p2, C_CON.p2s, C_CON.p2d], [C_CON.p3, C_CON.p3s, C_CON.p3d]];
  for (var i = 0; i < 3; i++) {
    var pay = p[i] || {};
    v[slots[i][0]] = pay.condition || '';
    v[slots[i][1]] = pay.share ? Math.round(pay.share * 100) + '%' : '';
    // Столбец «Оплачено» не трогаем вовсе: в таблице там дата поступления
    // денег, а в телефоне только галочка — затирать дату ею нельзя.
  }
  v[C_CON.note] = c.note || '';
  return v;
}

function staffCells(e) {
  var v = {};
  v[C_STAFF.dept] = e.department || '';
  v[C_STAFF.name] = e.name || '';
  v[C_STAFF.pos] = e.position || '';
  v[C_STAFF.tab] = e.tabNumber || '';
  v[C_STAFF.employment] = e.employment || '';
  v[C_STAFF.location] = e.location || '';
  var contacts = (e.phones || []).concat((e.chats || []).map(function (c) { return c.handle; }));
  if (contacts.length) v[C_STAFF.contact] = contacts.join(', ');
  v[C_STAFF.note] = e.note || '';
  return v;
}

function labelOf(status) {
  for (var label in STATUS) if (STATUS[label] === status) return label;
  return 'Потенциальный';
}

// --- само слияние ---------------------------------------------------------

function sync(incoming) {
  var map = readMap();
  var now = new Date().getTime();

  var customers = readCustomers();
  var sites = readSites(map);
  var contracts = readContracts(map);
  var staff = readStaff(map);
  var handovers = readHandovers();

  // Идентификаторы: берём из карты или заводим новые.
  function identify(sheetName, list) {
    for (var i = 0; i < list.length; i++) {
      var key = sheetName + ':' + list[i].row;
      var known = map[key];
      if (!known) {
        var id = uuid();
        writeMapEntry(map, sheetName, list[i].row, id, now, list[i].hash);
        list[i].id = id;
        list[i].updatedAt = now;
        list[i].fresh = true;             // строки в таблице ещё не видели
      } else {
        list[i].id = known.id;
        // Отпечаток другой — значит строку правили руками после обмена.
        var edited = known.hash !== list[i].hash;
        list[i].updatedAt = edited ? now : known.updatedAt;
        list[i].fresh = edited;
        if (edited) writeMapEntry(map, sheetName, list[i].row, known.id, now, list[i].hash);
      }
    }
  }

  identify(SH_SITES, sites);
  identify(SH_CONTRACTS, contracts);
  identify(SH_STAFF, staff);

  // Приложение прислало свои версии — пишем те, что новее табличных.
  var phoneSites = indexBy(incoming.sites || []);
  var phoneContracts = indexBy(incoming.contracts || []);
  var phoneStaff = indexBy(incoming.employees || []);

  var siteSheet = sheetOf(SH_SITES);
  var conSheet = sheetOf(SH_CONTRACTS);
  var staffSheet = sheetOf(SH_STAFF);

  var siteNameById = {};
  for (var i = 0; i < sites.length; i++) siteNameById[sites[i].id] = sites[i].value.name;

  for (var i = 0; i < sites.length; i++) {
    var mine = sites[i];
    var theirs = phoneSites[mine.id];
    if (!theirs || theirs.deleted) continue;
    if (Number(theirs.updatedAt || 0) <= mine.updatedAt) continue;
    var changed = writeCells(siteSheet, mine.row, C_SITE_WRITE, siteCells({
      name: theirs.name,
      fullName: theirs.fullName,
      customerName: nameOfCustomer(incoming, theirs.customerId),
      address: theirs.address,
      buildingType: theirs.buildingType,
      area: theirs.area,
      unit: theirs.unit,
      note: theirs.note
    }));
    if (changed) {
      applyPhoneSite(mine, theirs, incoming);
      writeMapEntry(map, SH_SITES, mine.row, mine.id, Number(theirs.updatedAt), hashOf(pick(rowValues(siteSheet, mine.row, 12), C_SITE)));
      mine.updatedAt = Number(theirs.updatedAt);
    }
  }

  for (var i = 0; i < contracts.length; i++) {
    var mineC = contracts[i];
    var theirsC = phoneContracts[mineC.id];
    if (!theirsC || theirsC.deleted) continue;
    if (Number(theirsC.updatedAt || 0) <= mineC.updatedAt) continue;
    var cells = contractCells({
      siteName: siteNameById[theirsC.siteId] || mineC.value.siteName,
      workKind: theirsC.workKind,
      legalEntity: theirsC.legalEntity,
      amount: theirsC.amount,
      start: theirsC.start,
      end: theirsC.end,
      responsible: theirsC.responsible,
      coExecutors: theirsC.coExecutors,
      payments: theirsC.payments,
      note: theirsC.note
    }, labelOf(theirsC.status));
    var changedC = writeCells(conSheet, mineC.row, C_CON_WRITE, cells);
    if (changedC) {
      applyPhoneContract(mineC, theirsC, siteNameById);
      writeMapEntry(map, SH_CONTRACTS, mineC.row, mineC.id, Number(theirsC.updatedAt), hashOf(pick(rowValues(conSheet, mineC.row, 40), C_CON)));
      mineC.updatedAt = Number(theirsC.updatedAt);
    }
  }

  for (var i = 0; i < staff.length; i++) {
    var mineS = staff[i];
    var theirsS = phoneStaff[mineS.id];
    if (!theirsS || theirsS.deleted) continue;
    if (Number(theirsS.updatedAt || 0) <= mineS.updatedAt) continue;
    var changedS = writeCells(staffSheet, mineS.row, C_STAFF_WRITE, staffCells(theirsS));
    if (changedS) {
      applyPhoneStaff(mineS, theirsS);
      writeMapEntry(map, SH_STAFF, mineS.row, mineS.id, Number(theirsS.updatedAt), hashOf(pick(rowValues(staffSheet, mineS.row, 9), C_STAFF)));
      mineS.updatedAt = Number(theirsS.updatedAt);
    }
  }

  // Новые записи из телефона — дописываем строки в конец блока данных.
  appendNewSites(incoming, sites, map, siteSheet, now, siteNameById);
  appendNewContracts(incoming, contracts, map, conSheet, now, siteNameById);
  appendNewStaff(incoming, staff, map, staffSheet, now);

  return buildDb(incoming, customers, sites, contracts, staff, handovers);
}

/**
 * Первая свободная строка ВНУТРИ блока данных.
 *
 * Раньше место бралось как getLastRow() + 1 — но это последняя заполненная
 * строка всего листа, включая служебные зоны справа. Новая запись уезжала
 * на три сотни строк вниз: в служебном листе она появлялась, а в списке её
 * не было видно, и в именованные диапазоны («ИСПОЛНИТЕЛИ», «ОБЪЕКТЫ_СПИСОК»)
 * она не попадала. Поэтому свободное место ищется по ключевому столбцу.
 */
function freeRow(sheet, sheetName, keyCols) {
  var first = FIRST_ROW[sheetName];
  var limit = first + 500;
  var height = Math.max(sheet.getMaxRows() - first + 1, 1);
  var width = Math.max.apply(null, keyCols);
  var values = sheet.getRange(first, 1, Math.min(height, 501), width).getValues();
  for (var i = 0; i < values.length; i++) {
    var busy = false;
    for (var k = 0; k < keyCols.length; k++) {
      if (text(values[i][keyCols[k] - 1])) { busy = true; break; }
    }
    if (!busy) return first + i;
  }
  return Math.min(first + values.length, limit);
}

function rowValues(sheet, row, width) {
  return sheet.getRange(row, 1, 1, width).getValues()[0];
}

function indexBy(list) {
  var out = {};
  for (var i = 0; i < list.length; i++) out[list[i].id] = list[i];
  return out;
}

function nameOfCustomer(incoming, customerId) {
  var list = incoming.customers || [];
  for (var i = 0; i < list.length; i++) if (list[i].id === customerId) return list[i].name;
  return '';
}

function applyPhoneSite(mine, theirs, incoming) {
  mine.value.name = theirs.name;
  mine.value.fullName = theirs.fullName;
  mine.value.customerName = nameOfCustomer(incoming, theirs.customerId) || mine.value.customerName;
  mine.value.address = theirs.address;
  mine.value.buildingType = theirs.buildingType;
  mine.value.area = theirs.area;
  mine.value.unit = theirs.unit;
  mine.value.note = theirs.note;
}

function applyPhoneContract(mine, theirs, siteNameById) {
  mine.value.siteName = siteNameById[theirs.siteId] || mine.value.siteName;
  mine.value.workKind = theirs.workKind;
  mine.value.legalEntity = theirs.legalEntity;
  mine.value.status = theirs.status;
  mine.value.amount = theirs.amount;
  mine.value.start = theirs.start;
  mine.value.end = theirs.end;
  mine.value.responsible = theirs.responsible;
  mine.value.coExecutors = theirs.coExecutors || [];
  mine.value.payments = theirs.payments || [];
  mine.value.note = theirs.note;
}

function applyPhoneStaff(mine, theirs) {
  mine.value.name = theirs.name;
  mine.value.department = theirs.department;
  mine.value.position = theirs.position;
  mine.value.tabNumber = theirs.tabNumber;
  mine.value.employment = theirs.employment;
  mine.value.location = theirs.location;
  mine.value.phones = theirs.phones || [];
  mine.value.chats = theirs.chats || [];
  mine.value.note = theirs.note;
}

function knownIds(list) {
  var out = {};
  for (var i = 0; i < list.length; i++) out[list[i].id] = true;
  return out;
}

function appendNewSites(incoming, sites, map, sheet, now, siteNameById) {
  var known = knownIds(sites);
  var list = incoming.sites || [];
  for (var i = 0; i < list.length; i++) {
    var s = list[i];
    if (known[s.id] || s.deleted || !s.name) continue;
    // Тёзка в таблице — это та же запись, просто заведённая с другой стороны.
    var twin = null;
    for (var j = 0; j < sites.length; j++) if (sites[j].value.name === s.name) twin = sites[j];
    var row = twin ? twin.row : freeRow(sheet, SH_SITES, [C_SITE.name]);
    writeCells(sheet, row, C_SITE_WRITE, siteCells({
      name: s.name, fullName: s.fullName, customerName: nameOfCustomer(incoming, s.customerId),
      address: s.address, buildingType: s.buildingType, area: s.area, unit: s.unit, note: s.note
    }));
    writeMapEntry(map, SH_SITES, row, s.id, Number(s.updatedAt) || now, hashOf(pick(rowValues(sheet, row, 12), C_SITE)));
    if (!twin) {
      sites.push({ row: row, id: s.id, updatedAt: Number(s.updatedAt) || now, value: {
        code: '', name: s.name, fullName: s.fullName, customerName: nameOfCustomer(incoming, s.customerId),
        address: s.address, buildingType: s.buildingType, area: s.area, unit: s.unit, note: s.note, row: row
      } });
    }
    siteNameById[s.id] = s.name;
  }
}

function appendNewContracts(incoming, contracts, map, sheet, now, siteNameById) {
  var known = knownIds(contracts);
  var list = incoming.contracts || [];
  for (var i = 0; i < list.length; i++) {
    var c = list[i];
    if (known[c.id] || c.deleted) continue;
    var siteName = siteNameById[c.siteId] || '';
    if (!siteName && !c.workKind) continue;
    var twin = null;
    for (var j = 0; j < contracts.length; j++) {
      if (contracts[j].value.siteName === siteName && contracts[j].value.workKind === c.workKind) twin = contracts[j];
    }
    var row = twin ? twin.row : freeRow(sheet, SH_CONTRACTS, [C_CON.site, C_CON.kind]);
    writeCells(sheet, row, C_CON_WRITE, contractCells({
      siteName: siteName, workKind: c.workKind, legalEntity: c.legalEntity, amount: c.amount,
      start: c.start, end: c.end, responsible: c.responsible, coExecutors: c.coExecutors,
      payments: c.payments, note: c.note
    }, labelOf(c.status)));
    writeMapEntry(map, SH_CONTRACTS, row, c.id, Number(c.updatedAt) || now, hashOf(pick(rowValues(sheet, row, 40), C_CON)));
    if (!twin) {
      contracts.push({ row: row, id: c.id, updatedAt: Number(c.updatedAt) || now, value: {
        code: '', siteName: siteName, workKind: c.workKind, legalEntity: c.legalEntity,
        status: c.status, amount: c.amount, start: c.start, end: c.end, responsible: c.responsible,
        coExecutors: c.coExecutors || [], payments: c.payments || [], note: c.note, row: row
      } });
    }
  }
}

function appendNewStaff(incoming, staff, map, sheet, now) {
  var known = knownIds(staff);
  var list = incoming.employees || [];
  for (var i = 0; i < list.length; i++) {
    var e = list[i];
    if (known[e.id] || e.deleted || !e.name) continue;
    var twin = null;
    for (var j = 0; j < staff.length; j++) if (staff[j].value.name === e.name) twin = staff[j];
    var row = twin ? twin.row : freeRow(sheet, SH_STAFF, [C_STAFF.name]);
    writeCells(sheet, row, C_STAFF_WRITE, staffCells(e));
    writeMapEntry(map, SH_STAFF, row, e.id, Number(e.updatedAt) || now, hashOf(pick(rowValues(sheet, row, 9), C_STAFF)));
    if (!twin) {
      staff.push({ row: row, id: e.id, updatedAt: Number(e.updatedAt) || now, value: {
        name: e.name, department: e.department, position: e.position, tabNumber: e.tabNumber,
        employment: e.employment, location: e.location, phones: e.phones || [], chats: e.chats || [],
        note: e.note, row: row
      } });
    }
  }
}

/** Сборка ответа в том виде, в каком его ждёт приложение. */
function buildDb(incoming, customers, sites, contracts, staff, handovers) {
  var customerIdByName = {};
  var incomingCustomers = incoming.customers || [];
  for (var i = 0; i < incomingCustomers.length; i++) {
    customerIdByName[incomingCustomers[i].name] = incomingCustomers[i];
  }

  var outCustomers = [];
  for (var i = 0; i < customers.length; i++) {
    var known = customerIdByName[customers[i].name];
    outCustomers.push({
      id: known ? known.id : uuid(),
      name: customers[i].name,
      fullName: known ? known.fullName : '',
      inn: known ? known.inn : '',
      kpp: known ? known.kpp : '',
      ogrn: known ? known.ogrn : '',
      legalAddress: known ? known.legalAddress : '',
      director: known ? known.director : '',
      phone: known ? known.phone : '',
      email: known ? known.email : '',
      bank: known ? known.bank : '',
      account: known ? known.account : '',
      bik: known ? known.bik : '',
      note: known ? known.note : '',
      updatedAt: known ? known.updatedAt : 0,
      deleted: false,
      row: customers[i].row
    });
  }
  var customerIdBySheetName = {};
  for (var i = 0; i < outCustomers.length; i++) customerIdBySheetName[outCustomers[i].name] = outCustomers[i].id;

  var outSites = [];
  var siteIdByName = {};
  for (var i = 0; i < sites.length; i++) {
    var s = sites[i];
    siteIdByName[s.value.name] = s.id;
    outSites.push({
      id: s.id,
      code: s.value.code,
      name: s.value.name,
      fullName: s.value.fullName,
      customerId: customerIdBySheetName[s.value.customerName] || null,
      address: s.value.address,
      buildingType: s.value.buildingType,
      area: s.value.area,
      unit: s.value.unit,
      note: s.value.note,
      progress: progressOf(incoming, s.id),
      updatedAt: s.updatedAt,
      deleted: false,
      row: s.row
    });
  }

  var outContracts = [];
  var contractIdByLabel = {};
  for (var i = 0; i < contracts.length; i++) {
    var c = contracts[i];
    contractIdByLabel[c.value.siteName + ' · ' + c.value.workKind] = c.id;
    outContracts.push({
      id: c.id,
      code: c.value.code,
      siteId: siteIdByName[c.value.siteName] || null,
      workKind: c.value.workKind,
      legalEntity: c.value.legalEntity,
      status: c.value.status,
      amount: c.value.amount,
      start: c.value.start,
      end: c.value.end,
      responsible: c.value.responsible,
      coExecutors: c.value.coExecutors,
      payments: c.value.payments,
      note: c.value.note,
      updatedAt: c.updatedAt,
      deleted: false,
      row: c.row
    });
  }

  var outStaff = [];
  for (var i = 0; i < staff.length; i++) {
    var e = staff[i];
    outStaff.push({
      id: e.id,
      name: e.value.name,
      department: e.value.department,
      position: e.value.position,
      tabNumber: e.value.tabNumber,
      employment: e.value.employment,
      location: e.value.location,
      phones: e.value.phones,
      chats: e.value.chats,
      note: e.value.note,
      updatedAt: e.updatedAt,
      deleted: false,
      row: e.row
    });
  }

  var outHandovers = [];
  for (var i = 0; i < handovers.length; i++) {
    var h = handovers[i];
    outHandovers.push({
      id: uuid(),
      code: h.value.code,
      contractId: contractIdByLabel[h.contractLabel] || null,
      what: h.value.what,
      kind: h.value.kind,
      plan: h.value.plan,
      fact: h.value.fact,
      status: h.value.status,
      responsible: h.value.responsible,
      note: h.value.note,
      updatedAt: 0,
      deleted: false,
      row: h.row
    });
  }

  var db = {
    customers: outCustomers,
    employees: outStaff,
    sites: outSites,
    contracts: outContracts,
    handovers: outHandovers,
    templates: incoming.templates || []
  };
  var refs = readRefs();
  if (refs && refs.workKinds && refs.workKinds.length) db.refs = refs;
  else if (incoming.refs) db.refs = incoming.refs;
  return db;
}

/** Готовность — поле телефона, в таблице такого столбца нет. Возвращаем как было. */
function progressOf(incoming, siteId) {
  var list = incoming.sites || [];
  for (var i = 0; i < list.length; i++) if (list[i].id === siteId) return list[i].progress || 0;
  return 0;
}

// --- разовая починка ------------------------------------------------------

/**
 * Переносит записи, уехавшие вниз листа старой версией скрипта.
 *
 * Строки не удаляются и не вставляются — содержимое копируется в первую
 * свободную строку блока данных, старое место очищается, а в служебном листе
 * правится номер строки. Сдвигать строки нельзя: вся карта считает записи по
 * номерам, и сдвиг увёл бы за собой все остальные.
 */
function починитьУехавшие() {
  var map = readMap();
  var plan = [
    { name: SH_SITES, keys: [C_SITE.name], width: 12, write: C_SITE_WRITE, cols: C_SITE },
    { name: SH_CONTRACTS, keys: [C_CON.site, C_CON.kind], width: 40, write: C_CON_WRITE, cols: C_CON },
    { name: SH_STAFF, keys: [C_STAFF.name], width: 9, write: C_STAFF_WRITE, cols: C_STAFF }
  ];
  var moved = 0;

  for (var p = 0; p < plan.length; p++) {
    var job = plan[p];
    var sheet = sheetOf(job.name);
    var first = FIRST_ROW[job.name];

    // Все строки карты этого листа, сверху вниз.
    var rows = [];
    for (var key in map) {
      var parts = key.split(':');
      if (parts[0] !== job.name) continue;
      rows.push({ row: Number(parts[1]), entry: map[key] });
    }
    rows.sort(function (a, b) { return a.row - b.row; });

    for (var i = 0; i < rows.length; i++) {
      var from = rows[i].row;
      var target = freeRow(sheet, job.name, job.keys);
      if (target >= from) continue;              // стоит там, где надо

      var values = sheet.getRange(from, 1, 1, job.width).getValues()[0];
      var busy = false;
      for (var k = 0; k < job.keys.length; k++) {
        if (text(values[job.keys[k] - 1])) busy = true;
      }
      if (!busy) continue;                        // строка пустая, переносить нечего

      var payload = {};
      for (var c = 0; c < job.write.length; c++) {
        payload[job.write[c]] = values[job.write[c] - 1];
      }
      writeCells(sheet, target, job.write, payload);
      for (var c = 0; c < job.write.length; c++) {
        sheet.getRange(from, job.write[c]).clearContent();
      }

      var hash = hashOf(pick(rowValues(sheet, target, job.width), job.cols));
      writeMapEntry(map, job.name, target, rows[i].entry.id, rows[i].entry.updatedAt, hash);
      clearMapRow(map, job.name, from);
      moved++;
      Logger.log(job.name + ': строка ' + from + ' → ' + target);
    }
  }
  Logger.log('Перенесено записей: ' + moved);
}

/** Убирает из служебного листа ссылку на опустевшую строку. */
function clearMapRow(map, sheetName, row) {
  var key = sheetName + ':' + row;
  var entry = map[key];
  if (!entry || !entry.mapRow) return;
  mapSheet().getRange(entry.mapRow, 1, 1, 5).clearContent();
  delete map[key];
}

// --- ручная проверка ------------------------------------------------------

/**
 * Запусти один раз из редактора, чтобы убедиться: листы на месте, колонки
 * совпали, ничего не переписано. Пишет краткий отчёт в журнал.
 */
function проверка() {
  var db = sync({});
  Logger.log('Объектов: ' + db.sites.length);
  Logger.log('Договоров: ' + db.contracts.length);
  Logger.log('Сотрудников: ' + db.employees.length);
  Logger.log('Заказчиков: ' + db.customers.length);
  Logger.log('Вех сдачи: ' + db.handovers.length);
  Logger.log('Новый объект встанет в строку: ' + freeRow(sheetOf(SH_SITES), SH_SITES, [C_SITE.name]));
  Logger.log('Новый договор встанет в строку: ' + freeRow(sheetOf(SH_CONTRACTS), SH_CONTRACTS, [C_CON.site, C_CON.kind]));
  Logger.log('Новый сотрудник встанет в строку: ' + freeRow(sheetOf(SH_STAFF), SH_STAFF, [C_STAFF.name]));
  if (db.sites.length) Logger.log('Первый объект: ' + JSON.stringify(db.sites[0]));
  if (db.contracts.length) Logger.log('Первый договор: ' + JSON.stringify(db.contracts[0]));
}
