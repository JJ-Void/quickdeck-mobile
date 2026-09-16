/**
 * ПОДРЯД — обмен между книгой и телефоном.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ТРИ ПРАВИЛА. Больше запоминать нечего.
 * ═══════════════════════════════════════════════════════════════════════
 *
 * 1. У КАЖДОЙ ЗАПИСИ ЕСТЬ ID.
 *    Он лежит в скрытом столбце BH того же листа и едет вместе со строкой.
 *    Сортируй листы, вставляй строки в середину, двигай блоки — связь не
 *    порвётся. Номер строки больше ничего не значит.
 *
 *    Раньше запись узнавали по номеру строки, и любая вставка перемешивала
 *    данные: телефон писал правки по объекту в соседний объект.
 *
 * 2. ПОЛЕ ПРАВИТ ТОТ, КТО ПРАВИЛ ПОЗЖЕ.
 *    У каждой записи одна метка времени. Книга ставит её, когда видит, что
 *    строку меняли руками (сравнивает отпечаток). Телефон — когда человек
 *    сохранил карточку. Свежая версия побеждает целиком.
 *
 * 3. УДАЛЕНИЕ — ЭТО КОМАНДА, А НЕ ПОЛЕ.
 *    Удалил в приложении → при обмене строка в книге сереет, перечёркивается
 *    и получает дату в скрытом столбце BI. В приложении запись пропадает и
 *    больше не возвращается.
 *    Удалил строку в книге → запись пропадает из приложения.
 *    Вернуть можно только руками: очистить BI или завести заново.
 *
 *    Почему не «в книге есть — значит верни в телефон»: тогда удалить
 *    что-либо с телефона стало бы невозможно в принципе, а команда «удалить»
 *    молча отменялась бы при каждом обмене. Именно это и происходило раньше:
 *    записи возвращались снова и снова.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ЧТО СКРИПТ НИКОГДА НЕ ТРОГАЕТ
 * ═══════════════════════════════════════════════════════════════════════
 * Формульные столбцы: номер (А), отдел, суммы, остатки, ранги, подписи
 * карточек. Они перечислены в *_WRITE — пишется только то, что там есть.
 * Ничего не очищается целиком: clear() в этом файле нет и быть не должно.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * УСТАНОВКА
 * ═══════════════════════════════════════════════════════════════════════
 * 1. Расширения → Apps Script, вставить этот файл целиком.
 * 2. Выполнить функцию НАСТРОИТЬ() один раз. Она заведёт скрытые столбцы
 *    и раздаст идентификаторы всем существующим строкам, подхватив старые
 *    из служебного листа, чтобы ничего не потеряло себя.
 * 3. Развернуть → Веб-приложение → запуск от моего имени → доступ «у всех
 *    в интернете» → Развернуть. Ссылку /exec вставить в приложении:
 *    Настройки → Google-таблица.
 *
 * После любой правки скрипта развёртывание нужно обновить, иначе телефон
 * продолжит говорить со старой версией.
 */

// --- листы и границы данных -----------------------------------------------

var SH_SITES = 'Объекты';
var SH_CONTRACTS = 'Договора';
var SH_STAFF = 'Сотрудники';
var SH_HANDOVER = 'Сдача';
var SH_CUSTOMERS = 'Заказчики';
var SH_REFS = 'Справочники';
var SH_MAP = '_ПОДРЯД';

/** Первая строка данных. Выше — шапка и подсказка. */
var FIRST_ROW = {
  'Объекты': 5, 'Договора': 5, 'Сдача': 5, 'Сотрудники': 5, 'Заказчики': 4
};

/** Сколько строк ниже первой имеет смысл просматривать. */
var SCAN = 600;

/**
 * Скрытые служебные столбцы.
 *
 * Стоят далеко за краем данных (самый широкий лист — «Договора», 46
 * столбцов) и специально не вставляются в начало: вставка столбца A
 * сдвинула бы все формулы и именованные диапазоны книги.
 */
var C_ID = 60;    // BH — идентификатор записи
var C_DEL = 61;   // BI — когда запись удалили из приложения
var WIDTH = 61;

/** Ширина видимой части листа: до этого столбца красим удалённую строку. */
var VIEW = {
  'Объекты': 12, 'Договора': 46, 'Сотрудники': 9, 'Сдача': 18, 'Заказчики': 6
};

/** Номера столбцов (1 = A). Чего тут нет — скрипт не читает и не пишет. */
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
var C_HAND_WRITE = [5, 6, 7, 8, 9, 10, 11];

/**
 * Лист «Заказчики» — не таблица, а карточка: столбец B держит имя блока,
 * C — подпись реквизита, D — его значение. Поэтому заказчик читается не
 * строкой, а блоком строк, и каждый реквизит ищется по своей подписи.
 *
 * Раньше отсюда бралось только наименование — из-за этого в телефоне у
 * заказчика не было ни ИНН, ни банка, ни директора.
 */
var C_CUST = { num: 1, name: 2, label: 3, value: 4, sites: 5, note: 6 };

/** Подпись в столбце C → поле записи в приложении. */
var CUST_FIELD = {
  'Полное наименование': 'fullName',
  'Юридический адрес': 'legalAddress',
  'ИНН': 'inn',
  'КПП': 'kpp',
  'ОГРН': 'ogrn',
  'Банк': 'bank',
  'Расчётный счёт': 'account',
  'БИК': 'bik',
  'Руководитель': 'director',
  'Телефон': 'phone',
  'E-mail': 'email'
};

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
  try {
    lock.waitLock(30000);
    var body = JSON.parse(e.postData.contents);
    if (body.action !== 'sync') return json({ ok: false, error: 'Неизвестное действие' });
    return json({ ok: true, db: sync(body.db || {}) });
  } catch (err) {
    return json({ ok: false, error: String(err && err.message ? err.message : err) });
  } finally {
    try { lock.releaseLock(); } catch (ignored) {}
  }
}

function doGet() {
  try {
    var n = {
      sites: readSheet(SH_SITES).length,
      contracts: readSheet(SH_CONTRACTS).length,
      staff: readSheet(SH_STAFF).length,
      customers: readCustomers().length
    };
    return ContentService
      .createTextOutput('ПОДРЯД на связи. Объектов: ' + n.sites + ', договоров: ' + n.contracts +
        ', сотрудников: ' + n.staff + ', заказчиков: ' + n.customers)
      .setMimeType(ContentService.MimeType.TEXT);
  } catch (err) {
    return ContentService
      .createTextOutput('Скрипт на связи, но книга не прочиталась: ' + err)
      .setMimeType(ContentService.MimeType.TEXT);
  }
}

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

// --- мелкие помощники -----------------------------------------------------

function text(v) {
  if (v === null || v === undefined) return '';
  if (v instanceof Date) return isoDate(v);
  return String(v).trim();
}

function isoDate(v) {
  if (!v) return '';
  if (v instanceof Date) {
    return v.getFullYear() + '-' + pad(v.getMonth() + 1) + '-' + pad(v.getDate());
  }
  var s = String(v).trim();
  var m = s.match(/^(\d{1,2})[.\/](\d{1,2})[.\/](\d{4})$/);
  if (m) return m[3] + '-' + pad(m[2]) + '-' + pad(m[1]);
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
  return '';
}

function pad(n) { return (String(n).length < 2 ? '0' : '') + n; }

function ruDate(iso) {
  if (!iso) return '';
  var m = String(iso).match(/^(\d{4})-(\d{2})-(\d{2})$/);
  return m ? m[3] + '.' + m[2] + '.' + m[1] : '';
}

function num(v) {
  if (typeof v === 'number') return v;
  var s = String(v || '').replace(/[^\d,.\-]/g, '').replace(',', '.');
  var n = parseFloat(s);
  return isNaN(n) ? 0 : n;
}

function share(v) {
  if (typeof v === 'number') return v > 1 ? v / 100 : v;
  var s = String(v || '').trim();
  if (!s) return 0;
  var n = num(s);
  return s.indexOf('%') >= 0 || n > 1 ? n / 100 : n;
}

function uuid() { return Utilities.getUuid(); }

/** Отпечаток значимых ячеек: по нему видно, правили ли строку руками. */
function hashOf(values) {
  var line = values.join('|~|');
  var raw = Utilities.computeDigest(Utilities.DigestAlgorithm.MD5, line, Utilities.Charset.UTF_8);
  var out = '';
  for (var i = 0; i < raw.length; i++) {
    var b = (raw[i] + 256) % 256;
    out += (b < 16 ? '0' : '') + b.toString(16);
  }
  return out;
}

function pick(row, cols) {
  var out = [];
  for (var key in cols) out.push(text(row[cols[key] - 1]));
  return out;
}

function sheetOf(name) {
  var sh = SpreadsheetApp.getActive().getSheetByName(name);
  if (!sh) throw new Error('Не нашёл лист «' + name + '»');
  return sh;
}

/** Лист должен быть шире служебных столбцов, иначе их негде держать. */
function ensureWidth(sh) {
  var have = sh.getMaxColumns();
  if (have < WIDTH) sh.insertColumnsAfter(have, WIDTH - have);
}

/** Блок данных листа целиком, вместе со скрытыми служебными столбцами. */
function gridOf(name) {
  var sh = sheetOf(name);
  ensureWidth(sh);
  var first = FIRST_ROW[name];
  var height = Math.min(SCAN, Math.max(sh.getMaxRows() - first + 1, 1));
  return { sheet: sh, name: name, first: first, rows: sh.getRange(first, 1, height, WIDTH).getValues() };
}

// --- служебный лист: метки времени и отпечатки ----------------------------

/**
 * Служебный лист держит по одной строке на запись: когда её последний раз
 * меняли и каким был отпечаток. Ключ — идентификатор, а не номер строки:
 * строка переезжает, идентификатор остаётся.
 */
function mapSheet() {
  var ss = SpreadsheetApp.getActive();
  var sh = ss.getSheetByName(SH_MAP);
  if (!sh) {
    sh = ss.insertSheet(SH_MAP);
    sh.hideSheet();
  }
  if (text(sh.getRange(1, 1).getValue()) !== 'id') {
    sh.getRange(1, 1, 1, 4).setValues([['id', 'Лист', 'Изменено', 'Отпечаток']]);
  }
  return sh;
}

function readMap() {
  var sh = mapSheet();
  var last = sh.getLastRow();
  var out = { sheet: sh, byId: {}, rowOf: {}, next: Math.max(last + 1, 2) };
  if (last < 2) return out;
  var values = sh.getRange(2, 1, last - 1, 4).getValues();
  for (var i = 0; i < values.length; i++) {
    var id = text(values[i][0]);
    if (!id) continue;
    out.byId[id] = {
      sheetName: text(values[i][1]),
      updatedAt: Number(values[i][2]) || 0,
      hash: text(values[i][3])
    };
    out.rowOf[id] = i + 2;
  }
  return out;
}

function writeMap(map, id, sheetName, updatedAt, hash) {
  var row = map.rowOf[id];
  if (!row) {
    row = map.next++;
    map.rowOf[id] = row;
  }
  map.sheet.getRange(row, 1, 1, 4).setValues([[id, sheetName, updatedAt, hash]]);
  map.byId[id] = { sheetName: sheetName, updatedAt: updatedAt, hash: hash };
}

// --- чтение листов --------------------------------------------------------

/**
 * Общий разбор листа-таблицы: одна строка — одна запись.
 *
 * Возвращает записи вместе с идентификатором, отметкой удаления и
 * отпечатком значимых ячеек. Строки без ключевого поля пропускаются: это
 * пустые заготовки, а не записи.
 */
function readSheet(name) {
  var grid = gridOf(name);
  var out = [];
  for (var i = 0; i < grid.rows.length; i++) {
    var r = grid.rows[i];
    var row = grid.first + i;
    var item = null;

    if (name === SH_SITES) item = siteOf(r, row);
    else if (name === SH_CONTRACTS) item = contractOf(r, row);
    else if (name === SH_STAFF) item = staffOf(r, row);
    else if (name === SH_HANDOVER) item = handoverOf(r, row);
    if (!item) continue;

    item.sheetName = name;
    item.row = row;
    item.id = text(r[C_ID - 1]);
    item.deletedAt = text(r[C_DEL - 1]);
    out.push(item);
  }
  return out;
}

function siteOf(r, row) {
  var name = text(r[C_SITE.name - 1]);
  if (!name) return null;
  return {
    hash: hashOf(pick(r, C_SITE)),
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
      row: row
    }
  };
}

function contractOf(r, row) {
  var siteName = text(r[C_CON.site - 1]);
  var kind = text(r[C_CON.kind - 1]);
  if (!siteName && !kind) return null;
  return {
    hash: hashOf(pick(r, C_CON)),
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
      row: row
    }
  };
}

function staffOf(r, row) {
  var name = text(r[C_STAFF.name - 1]);
  if (!name) return null;
  var contact = text(r[C_STAFF.contact - 1]);
  return {
    hash: hashOf(pick(r, C_STAFF)),
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
      row: row
    }
  };
}

function handoverOf(r, row) {
  var what = text(r[C_HAND.what - 1]);
  if (!what) return null;
  return {
    hash: hashOf(pick(r, C_HAND)),
    contractLabel: text(r[C_HAND.label - 1]),
    value: {
      code: text(r[0]),
      what: what,
      kind: text(r[C_HAND.kind - 1]),
      plan: isoDate(r[C_HAND.plan - 1]),
      fact: isoDate(r[C_HAND.fact - 1]),
      status: HANDOVER_STATUS[text(r[C_HAND.status - 1])] || 'NOT_STARTED',
      responsible: text(r[C_HAND.resp - 1]),
      note: text(r[C_HAND.note - 1]),
      row: row
    }
  };
}

/** В столбце «Телефон / почта» всё вперемешку — разбираем по виду. */
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

/**
 * Заказчики читаются блоками, а не строками.
 *
 * Блок начинается там, где в столбце B появляется новое наименование, и
 * тянется до следующего. Внутри блока столбец C — подпись реквизита, D —
 * значение; какие подписи во что превращаются, написано в CUST_FIELD.
 * Идентификатор блока лежит в скрытом столбце первой его строки.
 */
function readCustomers() {
  var grid = gridOf(SH_CUSTOMERS);
  var out = [];
  var current = null;

  for (var i = 0; i < grid.rows.length; i++) {
    var r = grid.rows[i];
    var row = grid.first + i;
    var name = text(r[C_CUST.name - 1]);
    var label = text(r[C_CUST.label - 1]);
    var value = text(r[C_CUST.value - 1]);

    if (name && (!current || current.value.name !== name)) {
      current = {
        sheetName: SH_CUSTOMERS,
        row: row,
        lastRow: row,
        id: text(r[C_ID - 1]),
        deletedAt: text(r[C_DEL - 1]),
        labelRows: {},
        value: {
          name: name, fullName: '', legalAddress: '', inn: '', kpp: '', ogrn: '',
          bank: '', account: '', bik: '', director: '', phone: '', email: '',
          note: text(r[C_CUST.note - 1]), row: row
        }
      };
      out.push(current);
    }
    if (!current || !name) continue;

    current.lastRow = row;
    var field = CUST_FIELD[label];
    if (field) {
      current.value[field] = value;
      current.labelRows[field] = row;
    }
  }

  for (var k = 0; k < out.length; k++) {
    out[k].hash = hashOf([
      out[k].value.name, out[k].value.fullName, out[k].value.legalAddress,
      out[k].value.inn, out[k].value.kpp, out[k].value.ogrn,
      out[k].value.bank, out[k].value.account, out[k].value.bik,
      out[k].value.director, out[k].value.phone, out[k].value.email
    ]);
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

// --- запись в книгу -------------------------------------------------------

/**
 * Пишет только изменившиеся ячейки и только из разрешённого списка.
 * Остальное в строке остаётся ровно таким, каким было, включая формулы.
 */
function writeCells(sheet, row, allowed, values) {
  var changed = 0;
  for (var i = 0; i < allowed.length; i++) {
    var col = allowed[i];
    if (!(col in values)) continue;
    var cell = sheet.getRange(row, col);
    if (text(cell.getValue()) === text(values[col])) continue;
    cell.setValue(values[col]);
    changed++;
  }
  return changed;
}

function siteCells(s) {
  var v = {};
  v[C_SITE.name] = s.name || '';
  v[C_SITE.full] = s.fullName || '';
  v[C_SITE.customer] = s.customerName || '';
  v[C_SITE.address] = s.address || '';
  v[C_SITE.btype] = s.buildingType || '';
  v[C_SITE.area] = s.area || '';
  v[C_SITE.unit] = s.unit || '';
  v[C_SITE.note] = s.note || '';
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
  var slots = [[C_CON.p1, C_CON.p1s], [C_CON.p2, C_CON.p2s], [C_CON.p3, C_CON.p3s]];
  for (var i = 0; i < 3; i++) {
    var pay = p[i] || {};
    v[slots[i][0]] = pay.condition || '';
    v[slots[i][1]] = pay.share ? Math.round(pay.share * 100) + '%' : '';
    // Столбец «Оплачено» не трогаем вовсе: в книге там дата поступления
    // денег, а в телефоне только галочка — затирать дату ею нельзя.
  }
  v[C_CON.note] = c.note || '';
  return v;
}

function staffCells(e) {
  var contact = []
    .concat(e.phones || [])
    .concat((e.chats || []).map(function (c) { return c.handle; }))
    .filter(function (v) { return v; })
    .join(', ');
  var v = {};
  v[C_STAFF.dept] = e.department || '';
  v[C_STAFF.name] = e.name || '';
  v[C_STAFF.pos] = e.position || '';
  v[C_STAFF.tab] = e.tabNumber || '';
  v[C_STAFF.employment] = e.employment || '';
  v[C_STAFF.location] = e.location || '';
  v[C_STAFF.contact] = contact;
  v[C_STAFF.note] = e.note || '';
  return v;
}

function handoverCells(h, statusLabel) {
  var v = {};
  v[C_HAND.what] = h.what || '';
  v[C_HAND.plan] = ruDate(h.plan);
  v[C_HAND.fact] = ruDate(h.fact);
  v[C_HAND.status] = statusLabel;
  v[C_HAND.resp] = h.responsible || '';
  v[C_HAND.note] = h.note || '';
  return v;
}

function labelOf(status) {
  for (var label in STATUS) if (STATUS[label] === status) return label;
  return 'Потенциальный';
}

function handoverLabelOf(status) {
  for (var label in HANDOVER_STATUS) if (HANDOVER_STATUS[label] === status) return label;
  return 'Не начато';
}

/**
 * Записать реквизиты заказчика обратно в карточку.
 *
 * Пишем только в те строки блока, где такая подпись реально есть: сочинять
 * новые строки в чужой вёрстке нельзя — карточка собрана на объединённых
 * ячейках и формулах.
 */
function writeCustomer(sheet, block, party) {
  var changed = 0;
  if (text(sheet.getRange(block.row, C_CUST.name).getValue()) !== text(party.name)) {
    sheet.getRange(block.row, C_CUST.name).setValue(party.name || '');
    changed++;
  }
  for (var field in block.labelRows) {
    var row = block.labelRows[field];
    var next = text(party[field] || '');
    var cell = sheet.getRange(row, C_CUST.value);
    if (text(cell.getValue()) === next) continue;
    cell.setValue(next);
    changed++;
  }
  return changed;
}

// --- удаление -------------------------------------------------------------

/**
 * Пометить строку удалённой: дата в скрытый столбец, серая заливка и
 * зачёркивание на видимую часть.
 *
 * Строку не вырезаем намеренно. Во-первых, под ней поедут формулы и
 * нумерация. Во-вторых, удаление с телефона — операция на один палец, и
 * данные не должны исчезать из книги безвозвратно от одного случайного
 * нажатия. Хочешь убрать совсем — убери строку руками.
 */
function markDeleted(sheet, name, row) {
  var width = VIEW[name] || 12;
  sheet.getRange(row, C_DEL).setValue(new Date());
  sheet.getRange(row, 1, 1, width)
    .setBackground('#F1F3F4')
    .setFontLine('line-through')
    .setFontColor('#9AA0A6');
}

/** Снять пометку — если строку вернули руками, очистив BI. */
function unmarkDeleted(sheet, name, row) {
  var width = VIEW[name] || 12;
  sheet.getRange(row, 1, 1, width)
    .setBackground(null)
    .setFontLine('none')
    .setFontColor(null);
}

// --- новые записи из телефона ---------------------------------------------

/** Первая свободная строка ВНУТРИ блока данных, а не в конце листа. */
function freeRow(sheet, name, keyCols) {
  var first = FIRST_ROW[name];
  var height = Math.min(SCAN, Math.max(sheet.getMaxRows() - first + 1, 1));
  var width = Math.max.apply(null, keyCols);
  var values = sheet.getRange(first, 1, height, width).getValues();
  for (var i = 0; i < values.length; i++) {
    var empty = true;
    for (var k = 0; k < keyCols.length; k++) {
      if (text(values[i][keyCols[k] - 1])) { empty = false; break; }
    }
    if (empty) return first + i;
  }
  var row = first + values.length;
  if (row > sheet.getMaxRows()) sheet.insertRowsAfter(sheet.getMaxRows(), 10);
  return row;
}

// --- само слияние ---------------------------------------------------------

/**
 * Один обмен целиком.
 *
 * Порядок важен: сначала книга узнаёт свои строки в лицо, потом принимает
 * команды удаления, потом правки, потом новые записи. Если поменять местами
 * удаление и правку, удалённая запись успеет записаться обратно.
 */
function sync(incoming) {
  var map = readMap();
  var now = new Date().getTime();

  var sets = [
    { name: SH_SITES, list: readSheet(SH_SITES), mine: indexBy(incoming.sites),
      theirs: incoming.sites, key: keySite },
    { name: SH_CONTRACTS, list: readSheet(SH_CONTRACTS), mine: indexBy(incoming.contracts),
      theirs: incoming.contracts, key: keyContract, allSites: incoming.sites || [] },
    { name: SH_STAFF, list: readSheet(SH_STAFF), mine: indexBy(incoming.employees),
      theirs: incoming.employees, key: keyStaff },
    { name: SH_HANDOVER, list: readSheet(SH_HANDOVER), mine: indexBy(incoming.handovers),
      theirs: incoming.handovers, key: keyHandover },
    { name: SH_CUSTOMERS, list: readCustomers(), mine: indexBy(incoming.customers),
      theirs: incoming.customers, key: keyCustomer }
  ];

  // 1. Опознание. Строка без идентификатора получает свой и запоминает его
  //    прямо в листе — со следующего обмена её уже не перепутать.
  for (var s = 0; s < sets.length; s++) identify(sets[s], map, now);

  // 2. Команды удаления с телефона. Раньше остальных: иначе правка успеет
  //    записать в книгу то, что человек уже удалил.
  for (var s2 = 0; s2 < sets.length; s2++) applyDeletes(sets[s2], map);

  // 3. Правки. Пишем только те записи, что в телефоне свежее табличных.
  var siteNameById = {};
  var sitesSet = sets[0];
  for (var i = 0; i < sitesSet.list.length; i++) {
    siteNameById[sitesSet.list[i].id] = sitesSet.list[i].value.name;
  }
  applyEdits(sets[0], map, incoming, siteNameById, now);
  applyEdits(sets[1], map, incoming, siteNameById, now);
  applyEdits(sets[2], map, incoming, siteNameById, now);
  applyEdits(sets[3], map, incoming, siteNameById, now);
  applyEdits(sets[4], map, incoming, siteNameById, now);

  // 4. Новые записи из телефона — дописываем строки внутрь блока данных.
  appendNew(sets[0], map, incoming, now, siteNameById);
  appendNew(sets[1], map, incoming, now, siteNameById);
  appendNew(sets[2], map, incoming, now, siteNameById);

  return buildDb(incoming, sets, map, now);
}

/**
 * Естественные признаки записей — по ним телефон и книга узнают друг друга
 * в первый раз, пока идентификаторов ещё нет.
 *
 * Дальше эти признаки не используются нигде: имя можно переименовать, а
 * идентификатор остаётся. Признак нужен ровно один раз — при знакомстве.
 */
function keySite(x) { return text((x.value ? x.value.name : x.name)).toLowerCase(); }

function keyStaff(x) { return text((x.value ? x.value.name : x.name)).toLowerCase(); }

function keyCustomer(x) { return text((x.value ? x.value.name : x.name)).toLowerCase(); }

function keyContract(x) {
  if (x.value) return (text(x.value.siteName) + '|' + text(x.value.workKind)).toLowerCase();
  return '';   // у телефона нет имени объекта, только ссылка — см. ниже
}

function keyHandover(x) {
  if (x.value) return (text(x.contractLabel) + '|' + text(x.value.what)).toLowerCase();
  return (text(x.what)).toLowerCase();
}

/**
 * Признак → идентификатор из телефона. Сюда попадают только те записи
 * телефона, которых книга ещё не опознала.
 */
function adoptionIndex(set) {
  var taken = {};
  for (var i = 0; i < set.list.length; i++) if (set.list[i].id) taken[set.list[i].id] = true;

  var out = {};
  var arr = set.theirs || [];
  for (var k = 0; k < arr.length; k++) {
    var rec = arr[k];
    if (!rec.id || taken[rec.id] || rec.deleted) continue;
    var key = set.key === keyContract ? contractKeyOfPhone(rec, set)
      : set.key === keyHandover ? text(rec.what).toLowerCase()
        : text(rec.name).toLowerCase();
    if (!key || out[key]) continue;
    out[key] = rec.id;
  }
  return out;
}

/**
 * У договора в телефоне нет имени объекта — только ссылка. Разворачиваем
 * её через объекты того же обмена, иначе договор себя не узнает.
 */
function contractKeyOfPhone(rec, set) {
  var sites = set.allSites || [];
  var name = '';
  for (var i = 0; i < sites.length; i++) if (sites[i].id === rec.siteId) name = sites[i].name;
  return (text(name) + '|' + text(rec.workKind)).toLowerCase();
}

function indexBy(list) {
  var out = {};
  var arr = list || [];
  for (var i = 0; i < arr.length; i++) out[arr[i].id] = arr[i];
  return out;
}

/**
 * Раздать идентификаторы и понять, кого правили руками.
 *
 * Отпечаток значимых ячеек сравнивается с запомненным: совпал — строку не
 * трогали, метка времени остаётся прежней; разошёлся — в книге правили,
 * метка становится текущей.
 */
function identify(set, map, now) {
  var sheet = sheetOf(set.name);
  var adopt = adoptionIndex(set);
  for (var i = 0; i < set.list.length; i++) {
    var item = set.list[i];
    if (!item.id) {
      // Строка ещё без идентификатора. Прежде чем заводить новый, ищем
      // такую же запись в телефоне по естественному признаку — имени,
      // паре «объект + вид работ». Иначе при первом обмене после
      // обновления книга и телефон завели бы каждый свой экземпляр, и
      // весь реестр задвоился бы.
      var mine = adopt[set.key(item)];
      item.id = mine || uuid();
      sheet.getRange(item.row, C_ID).setValue(item.id);
      writeMap(map, item.id, set.name, now, item.hash);
      item.updatedAt = now;
      set.mine[item.id] = set.mine[item.id] || null;
      continue;
    }
    var known = map.byId[item.id];
    if (!known) {
      writeMap(map, item.id, set.name, now, item.hash);
      item.updatedAt = now;
      continue;
    }
    var edited = known.hash !== item.hash;
    item.updatedAt = edited ? now : known.updatedAt;
    if (edited) writeMap(map, item.id, set.name, now, item.hash);
  }
}

/**
 * Удаления, пришедшие с телефона.
 *
 * Строка сереет и получает дату. Обратной команды нет: чтобы вернуть
 * запись, надо очистить столбец BI руками — так удаление остаётся
 * осознанным действием, а не тем, что молча откатывается при обмене.
 */
function applyDeletes(set, map) {
  var sheet = sheetOf(set.name);
  for (var i = 0; i < set.list.length; i++) {
    var item = set.list[i];
    var theirs = set.mine[item.id];
    if (!theirs) continue;

    if (theirs.deleted && !item.deletedAt) {
      markDeleted(sheet, set.name, item.row);
      item.deletedAt = 'now';
      item.updatedAt = Math.max(Number(theirs.updatedAt || 0), item.updatedAt || 0);
      writeMap(map, item.id, set.name, item.updatedAt, item.hash);
    }
  }
}

/**
 * Метка времени телефона, приведённая к здравому смыслу.
 *
 * Часы на телефоне могут убежать вперёд — тогда его версия «свежее» любой
 * будущей правки в книге, и правки руками начинают молча пропадать.
 * Поэтому метка из будущего считается сегодняшней: при равенстве
 * побеждает книга, потому что её правку скрипт видит своими глазами.
 */
function phoneStamp(theirs, now) {
  return Math.min(Number(theirs.updatedAt || 0), now);
}

function applyEdits(set, map, incoming, siteNameById, now) {
  var sheet = sheetOf(set.name);
  for (var i = 0; i < set.list.length; i++) {
    var item = set.list[i];
    if (item.deletedAt) continue;
    var theirs = set.mine[item.id];
    if (!theirs || theirs.deleted) continue;
    if (phoneStamp(theirs, now) <= (item.updatedAt || 0)) continue;

    var changed = 0;
    if (set.name === SH_SITES) {
      changed = writeCells(sheet, item.row, C_SITE_WRITE, siteCells({
        name: theirs.name, fullName: theirs.fullName,
        customerName: nameOfCustomer(incoming, theirs.customerId),
        address: theirs.address, buildingType: theirs.buildingType,
        area: theirs.area, unit: theirs.unit, note: theirs.note
      }));
    } else if (set.name === SH_CONTRACTS) {
      changed = writeCells(sheet, item.row, C_CON_WRITE, contractCells({
        siteName: siteNameById[theirs.siteId] || item.value.siteName,
        workKind: theirs.workKind, legalEntity: theirs.legalEntity,
        amount: theirs.amount, start: theirs.start, end: theirs.end,
        responsible: theirs.responsible, coExecutors: theirs.coExecutors,
        payments: theirs.payments, note: theirs.note
      }, labelOf(theirs.status)));
    } else if (set.name === SH_STAFF) {
      changed = writeCells(sheet, item.row, C_STAFF_WRITE, staffCells(theirs));
    } else if (set.name === SH_HANDOVER) {
      changed = writeCells(sheet, item.row, C_HAND_WRITE,
        handoverCells(theirs, handoverLabelOf(theirs.status)));
    } else if (set.name === SH_CUSTOMERS) {
      changed = writeCustomer(sheet, item, theirs);
    }

    if (!changed) continue;

    // Перечитываем строку: отпечаток должен совпасть с тем, что теперь
    // в книге, иначе следующий обмен решит, что её правили руками.
    var fresh = freshItem(set.name, item.row);
    item.value = fresh ? fresh.value : item.value;
    item.hash = fresh ? fresh.hash : item.hash;
    item.updatedAt = phoneStamp(theirs, now);
    writeMap(map, item.id, set.name, item.updatedAt, item.hash);
  }
}

/** Перечитать одну строку после записи. */
function freshItem(name, row) {
  if (name === SH_CUSTOMERS) {
    var all = readCustomers();
    for (var i = 0; i < all.length; i++) if (all[i].row === row) return all[i];
    return null;
  }
  var sheet = sheetOf(name);
  var r = sheet.getRange(row, 1, 1, WIDTH).getValues()[0];
  if (name === SH_SITES) return siteOf(r, row);
  if (name === SH_CONTRACTS) return contractOf(r, row);
  if (name === SH_STAFF) return staffOf(r, row);
  if (name === SH_HANDOVER) return handoverOf(r, row);
  return null;
}

function nameOfCustomer(incoming, customerId) {
  var list = incoming.customers || [];
  for (var i = 0; i < list.length; i++) if (list[i].id === customerId) return list[i].name;
  return '';
}

/**
 * Записи, заведённые в телефоне и ещё не попавшие в книгу.
 *
 * Удалённые не дописываем никогда: иначе удаление, дошедшее раньше самой
 * записи, вернуло бы её обратно новой строкой.
 */
function appendNew(set, map, incoming, now, siteNameById) {
  var sheet = sheetOf(set.name);
  var have = {};
  for (var i = 0; i < set.list.length; i++) have[set.list[i].id] = true;

  var source = set.name === SH_SITES ? (incoming.sites || [])
    : set.name === SH_CONTRACTS ? (incoming.contracts || [])
      : (incoming.employees || []);

  for (var k = 0; k < source.length; k++) {
    var rec = source[k];
    if (!rec.id || have[rec.id] || rec.deleted) continue;
    // Запись, которую книга уже знала и которую удалили строкой, обратно
    // не воскрешаем: её идентификатор остался в служебном листе.
    if (map.byId[rec.id]) continue;

    var row, values;
    if (set.name === SH_SITES) {
      if (!text(rec.name)) continue;
      row = freeRow(sheet, set.name, [C_SITE.name]);
      values = siteCells({
        name: rec.name, fullName: rec.fullName,
        customerName: nameOfCustomer(incoming, rec.customerId),
        address: rec.address, buildingType: rec.buildingType,
        area: rec.area, unit: rec.unit, note: rec.note
      });
      writeCells(sheet, row, C_SITE_WRITE, values);
    } else if (set.name === SH_CONTRACTS) {
      row = freeRow(sheet, set.name, [C_CON.site, C_CON.kind]);
      writeCells(sheet, row, C_CON_WRITE, contractCells({
        siteName: siteNameById[rec.siteId] || '',
        workKind: rec.workKind, legalEntity: rec.legalEntity,
        amount: rec.amount, start: rec.start, end: rec.end,
        responsible: rec.responsible, coExecutors: rec.coExecutors,
        payments: rec.payments, note: rec.note
      }, labelOf(rec.status)));
    } else {
      if (!text(rec.name)) continue;
      row = freeRow(sheet, set.name, [C_STAFF.name]);
      writeCells(sheet, row, C_STAFF_WRITE, staffCells(rec));
    }

    sheet.getRange(row, C_ID).setValue(rec.id);
    var fresh = freshItem(set.name, row);
    var hash = fresh ? fresh.hash : '';
    writeMap(map, rec.id, set.name, Math.min(Number(rec.updatedAt || now), now), hash);
    if (fresh) {
      fresh.id = rec.id;
      fresh.row = row;
      fresh.sheetName = set.name;
      fresh.deletedAt = '';
      fresh.updatedAt = Math.min(Number(rec.updatedAt || now), now);
      set.list.push(fresh);
      have[rec.id] = true;
      if (set.name === SH_SITES) siteNameById[rec.id] = fresh.value.name;
    }
  }
}

// --- ответ телефону -------------------------------------------------------

/**
 * Что уезжает в телефон.
 *
 * Живые записи — как они выглядят в книге прямо сейчас. Плюс надгробия:
 * записи, помеченные удалёнными, и те, чьи строки из книги вырезали руками.
 * Без надгробий телефон считал бы, что записи просто «нет в ответе», и
 * оставлял бы свою — именно поэтому удалённое возвращалось снова и снова.
 */
function buildDb(incoming, sets, map, now) {
  var bySheet = {};
  for (var i = 0; i < sets.length; i++) bySheet[sets[i].name] = sets[i];

  var customers = bySheet[SH_CUSTOMERS].list;
  var sites = bySheet[SH_SITES].list;
  var contracts = bySheet[SH_CONTRACTS].list;
  var staff = bySheet[SH_STAFF].list;
  var handovers = bySheet[SH_HANDOVER].list;

  var customerIdByName = {};
  for (var c = 0; c < customers.length; c++) {
    if (!customers[c].deletedAt) customerIdByName[customers[c].value.name] = customers[c].id;
  }
  var siteIdByName = {};
  for (var s = 0; s < sites.length; s++) {
    if (!sites[s].deletedAt) siteIdByName[sites[s].value.name] = sites[s].id;
  }
  var contractIdByLabel = {};
  for (var k = 0; k < contracts.length; k++) {
    contractIdByLabel[contracts[k].value.siteName + ' · ' + contracts[k].value.workKind] = contracts[k].id;
  }

  function base(item, extra) {
    var out = { id: item.id, updatedAt: item.updatedAt || 0, deleted: !!item.deletedAt, row: item.row };
    for (var key in extra) out[key] = extra[key];
    return out;
  }

  var outCustomers = customers.map(function (p) {
    return base(p, {
      name: p.value.name, fullName: p.value.fullName, inn: p.value.inn, kpp: p.value.kpp,
      ogrn: p.value.ogrn, legalAddress: p.value.legalAddress, director: p.value.director,
      phone: p.value.phone, email: p.value.email, bank: p.value.bank,
      account: p.value.account, bik: p.value.bik, note: p.value.note
    });
  });

  var outSites = sites.map(function (s) {
    return base(s, {
      code: s.value.code, name: s.value.name, fullName: s.value.fullName,
      customerId: customerIdByName[s.value.customerName] || null,
      address: s.value.address, buildingType: s.value.buildingType,
      area: s.value.area, unit: s.value.unit, note: s.value.note,
      progress: keepNumber(incoming.sites, s.id, 'progress')
    });
  });

  var outContracts = contracts.map(function (c) {
    return base(c, {
      code: c.value.code, siteId: siteIdByName[c.value.siteName] || null,
      workKind: c.value.workKind, legalEntity: c.value.legalEntity,
      status: c.value.status, amount: c.value.amount,
      start: c.value.start, end: c.value.end,
      responsible: c.value.responsible, coExecutors: c.value.coExecutors,
      payments: c.value.payments, note: c.value.note,
      // Задач в книге нет — возвращаем телефону его же, иначе обмен их сотрёт.
      tasks: keepList(incoming.contracts, c.id, 'tasks')
    });
  });

  var outStaff = staff.map(function (e) {
    return base(e, {
      name: e.value.name, department: e.value.department, position: e.value.position,
      tabNumber: e.value.tabNumber, employment: e.value.employment,
      location: e.value.location, phones: e.value.phones, chats: e.value.chats,
      note: e.value.note
    });
  });

  var outHandovers = handovers.map(function (h) {
    return base(h, {
      code: h.value.code, contractId: contractIdByLabel[h.contractLabel] || null,
      what: h.value.what, kind: h.value.kind, plan: h.value.plan, fact: h.value.fact,
      status: h.value.status, responsible: h.value.responsible, note: h.value.note
    });
  });

  // Надгробия: идентификатор в служебном листе есть, а строки в книге нет.
  var alive = {};
  for (var a = 0; a < sets.length; a++) {
    for (var b = 0; b < sets[a].list.length; b++) alive[sets[a].list[b].id] = sets[a].name;
  }
  var gone = { 'Объекты': outSites, 'Договора': outContracts, 'Сотрудники': outStaff,
    'Сдача': outHandovers, 'Заказчики': outCustomers };
  for (var id in map.byId) {
    if (alive[id]) continue;
    var bucket = gone[map.byId[id].sheetName];
    if (!bucket) continue;
    bucket.push({ id: id, updatedAt: now, deleted: true, row: 0 });
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

/** Поля, которых в книге нет, возвращаем телефону его же — числом. */
function keepNumber(list, id, field) {
  var arr = list || [];
  for (var i = 0; i < arr.length; i++) if (arr[i].id === id) return arr[i][field] || 0;
  return 0;
}

/** То же для списков: задачи по договору живут только в телефоне. */
function keepList(list, id, field) {
  var arr = list || [];
  for (var i = 0; i < arr.length; i++) if (arr[i].id === id) return arr[i][field] || [];
  return [];
}

// --- установка ------------------------------------------------------------

/**
 * Выполнить один раз после вставки скрипта.
 *
 * Заводит скрытые столбцы, раздаёт идентификаторы всем существующим
 * строкам и подхватывает старые идентификаторы из прежнего служебного
 * листа — чтобы записи, уже живущие в телефоне, не начали жизнь заново
 * и не задвоились.
 */
function НАСТРОИТЬ() {
  var ss = SpreadsheetApp.getActive();
  var old = oldMapByRow(ss);
  var map = readMap();
  var now = new Date().getTime();
  var report = [];

  var sheets = [SH_SITES, SH_CONTRACTS, SH_STAFF, SH_HANDOVER, SH_CUSTOMERS];
  for (var i = 0; i < sheets.length; i++) {
    var name = sheets[i];
    var sh = sheetOf(name);
    ensureWidth(sh);
    var head = FIRST_ROW[name] - 1;
    sh.getRange(head, C_ID).setValue('ID');
    sh.getRange(head, C_DEL).setValue('Удалено');
    sh.hideColumns(C_ID, 2);

    var list = name === SH_CUSTOMERS ? readCustomers() : readSheet(name);
    var given = 0;
    for (var k = 0; k < list.length; k++) {
      var item = list[k];
      if (item.id) continue;
      var key = name + ':' + item.row;
      var id = old[key] ? old[key].id : uuid();
      sh.getRange(item.row, C_ID).setValue(id);
      writeMap(map, id, name, old[key] ? old[key].updatedAt : now, item.hash);
      given++;
    }
    report.push(name + ': записей ' + list.length + ', выдано идентификаторов ' + given);
  }

  SpreadsheetApp.getActive().toast(report.join('\n'), 'ПОДРЯД настроен', 30);
  Logger.log(report.join('\n'));
}

/**
 * Прежний служебный лист хранил связку «лист + номер строки → id».
 * Читаем его до перезаписи, чтобы перенести идентификаторы.
 */
function oldMapByRow(ss) {
  var sh = ss.getSheetByName(SH_MAP);
  var out = {};
  if (!sh) return out;
  var last = sh.getLastRow();
  if (last < 2) return out;
  var head = sh.getRange(1, 1, 1, 5).getValues()[0].map(text);
  if (head[0] !== 'Лист') return out;          // лист уже нового образца
  var values = sh.getRange(2, 1, last - 1, 5).getValues();
  for (var i = 0; i < values.length; i++) {
    var sheetName = text(values[i][0]);
    var row = Number(values[i][1]);
    var id = text(values[i][2]);
    if (!sheetName || !row || !id) continue;
    out[sheetName + ':' + row] = { id: id, updatedAt: Number(values[i][3]) || 0 };
  }
  sh.clear();
  sh.getRange(1, 1, 1, 4).setValues([['id', 'Лист', 'Изменено', 'Отпечаток']]);
  return out;
}

// --- ручная проверка ------------------------------------------------------

/** Что скрипт видит в книге. Запустить и посмотреть журнал выполнения. */
function ПРОВЕРКА() {
  var lines = [];
  var sets = [SH_SITES, SH_CONTRACTS, SH_STAFF, SH_HANDOVER];
  for (var i = 0; i < sets.length; i++) {
    var list = readSheet(sets[i]);
    var withId = list.filter(function (x) { return !!x.id; }).length;
    var dead = list.filter(function (x) { return !!x.deletedAt; }).length;
    lines.push(sets[i] + ': строк ' + list.length + ', с идентификатором ' + withId + ', помечено удалёнными ' + dead);
  }
  var cust = readCustomers();
  lines.push('Заказчики: карточек ' + cust.length +
    ', с идентификатором ' + cust.filter(function (x) { return !!x.id; }).length);
  if (cust.length) {
    var p = cust[0].value;
    lines.push('Первый заказчик: ' + p.name + ' | ИНН ' + p.inn + ' | ' + p.bank + ' | ' + p.director);
  }
  Logger.log(lines.join('\n'));
  SpreadsheetApp.getActive().toast(lines.join('\n'), 'ПОДРЯД — проверка', 30);
}
