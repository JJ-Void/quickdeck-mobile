/**
 * Проверка обмена на подставной книге.
 *
 * Google Apps Script здесь не запустить, поэтому SpreadsheetApp и Utilities
 * заменены самыми простыми заглушками на массивах. Проверяется не разметка
 * листа, а то, что раз за разом ломалось: опознание строк, удаление в обе
 * стороны и то, что удалённое не возвращается.
 */

const crypto = require('crypto');

/**
 * Часы под управлением теста.
 *
 * Обмен весь построен на «кто правил позже», поэтому проверять его с
 * настоящими часами бессмысленно: все события происходят в одну и ту же
 * миллисекунду. Здесь время двигает сам тест — и порядок событий виден
 * в коде, а не угадывается.
 */
const RealDate = Date;
let CLOCK = RealDate.now();
global.Date = class extends RealDate {
  constructor(...a) { if (a.length) super(...a); else super(CLOCK); }
  static now() { return CLOCK; }
};
function tick(ms = 60000) { CLOCK += ms; return CLOCK; }

// --- заглушки Apps Script ---------------------------------------------------

class Range {
  constructor(sheet, row, col, h, w) {
    Object.assign(this, { sheet, row, col, h, w });
  }
  getValues() {
    const out = [];
    for (let r = 0; r < this.h; r++) {
      const line = [];
      for (let c = 0; c < this.w; c++) line.push(this.sheet.cell(this.row + r, this.col + c));
      out.push(line);
    }
    return out;
  }
  getValue() { return this.sheet.cell(this.row, this.col); }
  setValue(v) { this.sheet.set(this.row, this.col, v); return this; }
  setValues(vals) {
    vals.forEach((line, r) => line.forEach((v, c) => this.sheet.set(this.row + r, this.col + c, v)));
    return this;
  }
  setBackground(v) { this.sheet.paint(this.row, 'bg', v); return this; }
  setFontLine(v) { this.sheet.paint(this.row, 'line', v); return this; }
  setFontColor(v) { this.sheet.paint(this.row, 'color', v); return this; }
  clear() { this.sheet.data = {}; return this; }
}

class Sheet {
  constructor(name, rows = 40, cols = 70) {
    this.name = name;
    this.data = {};
    this.style = {};
    this.rows = rows;
    this.cols = cols;
    this.hidden = [];
  }
  key(r, c) { return r + ':' + c; }
  cell(r, c) { const v = this.data[this.key(r, c)]; return v === undefined ? '' : v; }
  set(r, c, v) { this.data[this.key(r, c)] = v; }
  paint(r, what, v) { (this.style[r] = this.style[r] || {})[what] = v; }
  getRange(row, col, h = 1, w = 1) { return new Range(this, row, col, h, w); }
  getMaxRows() { return this.rows; }
  getMaxColumns() { return this.cols; }
  getLastRow() {
    let last = 0;
    for (const k of Object.keys(this.data)) {
      const r = Number(k.split(':')[0]);
      if (String(this.data[k]) !== '') last = Math.max(last, r);
    }
    return last;
  }
  insertColumnsAfter(after, n) { this.cols += n; }
  insertRowsAfter(after, n) { this.rows += n; }
  hideColumns(col, n) { this.hidden.push([col, n]); }
  hideSheet() { this.isHidden = true; }
  clear() { this.data = {}; }
}

class Book {
  constructor() { this.sheets = {}; }
  add(name, rows, cols) { return (this.sheets[name] = new Sheet(name, rows, cols)); }
  getSheetByName(n) { return this.sheets[n] || null; }
  insertSheet(n) { return this.add(n); }
  toast() {}
}

let BOOK = new Book();

global.SpreadsheetApp = { getActive: () => BOOK, getActiveSpreadsheet: () => BOOK };
global.Logger = { log: () => {} };
global.Utilities = {
  getUuid: () => crypto.randomUUID(),
  computeDigest: (_alg, s) => Array.from(crypto.createHash('md5').update(s, 'utf8').digest()),
  DigestAlgorithm: { MD5: 'MD5' },
  Charset: { UTF_8: 'UTF-8' }
};
global.LockService = { getScriptLock: () => ({ waitLock() {}, releaseLock() {} }) };
global.ContentService = {
  createTextOutput: (t) => ({ setMimeType: () => t }),
  MimeType: { JSON: 'json', TEXT: 'text' }
};

// --- загрузка скрипта -------------------------------------------------------

const fs = require('fs');
const src = fs.readFileSync(__dirname + '/../../ПОДРЯД-ОБМЕН.gs', 'utf8');
(0, eval)(src + '\nglobal.__sync = sync; global.__setup = НАСТРОИТЬ; global.__readSheet = readSheet;'
  + ' global.__readCustomers = readCustomers; global.__readMap = readMap;');

// --- подставная книга -------------------------------------------------------

function freshBook() {
  BOOK = new Book();

  const sites = BOOK.add('Объекты');
  sites.getRange(5, 1).setValue('О-001');
  sites.getRange(5, 2).setValue('Цимлянская 17');
  sites.getRange(5, 4).setValue('ООО СК «ЭнергоКомплекс»');
  sites.getRange(6, 1).setValue('О-002');
  sites.getRange(6, 2).setValue('Пролетариата Донбасса 5');

  const cons = BOOK.add('Договора');
  cons.getRange(5, 1).setValue('Д-001');
  cons.getRange(5, 2).setValue('Цимлянская 17');
  cons.getRange(5, 3).setValue('Обследование');
  cons.getRange(5, 5).setValue('В работе');
  cons.getRange(5, 6).setValue(980000);

  const staff = BOOK.add('Сотрудники');
  staff.getRange(5, 1).setValue('МЕНЕДЖМЕНТ');
  staff.getRange(5, 2).setValue('Патрушев Даниил Сергеевич');
  staff.getRange(5, 3).setValue('Зам. директора');
  staff.getRange(5, 8).setValue('+79068564704, @Dln_Srgvch');

  BOOK.add('Сдача');

  // Карточка заказчика: B — имя блока, C — подпись, D — значение.
  const cust = BOOK.add('Заказчики');
  const block = [
    ['Полное наименование', 'ООО Строительная Компания «ЭнергоКомплекс»'],
    ['Юридический адрес', 'г. Одинцово, ул. Южная, 8А'],
    ['ИНН', '5032307315'],
    ['КПП', '503201001'],
    ['ОГРН', '1195081030198'],
    ['Банк', 'ПАО Сбербанк'],
    ['Расчётный счёт', '40702810740000069793'],
    ['БИК', '044525225'],
    ['Руководитель', 'Иванов И. И.'],
    ['Телефон', '+74951234567'],
    ['E-mail', 'info@ek.ru']
  ];
  block.forEach(([label, value], i) => {
    cust.getRange(4 + i, 2).setValue('ООО СК «ЭнергоКомплекс»');
    cust.getRange(4 + i, 3).setValue(label);
    cust.getRange(4 + i, 4).setValue(value);
  });
}

// --- проверки ---------------------------------------------------------------

let failures = 0;
function ok(name, cond, extra) {
  if (cond) { console.log('  ok   ' + name); return; }
  failures++;
  console.log('  ПЛОХО ' + name + (extra ? '  → ' + extra : ''));
}

function findById(list, id) { return (list || []).find((x) => x.id === id); }

console.log('\nОПОЗНАНИЕ И ПЕРВЫЙ ОБМЕН');
freshBook();
let db = __sync({});
ok('объекты приехали', db.sites.length === 2, 'пришло ' + db.sites.length);
ok('у всех есть идентификатор', db.sites.every((s) => !!s.id));
ok('договор приехал', db.contracts.length === 1);
ok('договор привязан к объекту', !!db.contracts[0].siteId);
ok('сотрудник приехал', db.employees.length === 1);
ok('телефон разобран', db.employees[0].phones.length === 1, JSON.stringify(db.employees[0].phones));
ok('телеграм разобран', db.employees[0].chats.length === 1, JSON.stringify(db.employees[0].chats));

console.log('\nРЕКВИЗИТЫ ЗАКАЗЧИКА');
ok('заказчик один', db.customers.length === 1, 'пришло ' + db.customers.length);
const p = db.customers[0];
ok('ИНН приехал', p.inn === '5032307315', p.inn);
ok('банк приехал', p.bank === 'ПАО Сбербанк', p.bank);
ok('расчётный счёт приехал', p.account === '40702810740000069793', p.account);
ok('БИК приехал', p.bik === '044525225', p.bik);
ok('директор приехал', p.director === 'Иванов И. И.', p.director);
ok('почта приехала', p.email === 'info@ek.ru', p.email);
ok('объект привязан к заказчику', db.sites[0].customerId === p.id);

console.log('\nПОВТОРНЫЙ ОБМЕН НИЧЕГО НЕ ПЛОДИТ');
const before = { s: db.sites.length, c: db.contracts.length, p: db.customers.length, h: db.handovers.length };
const ids = db.sites.map((s) => s.id).sort().join();
db = __sync(db);
ok('объектов столько же', db.sites.length === before.s, 'стало ' + db.sites.length);
ok('заказчиков столько же', db.customers.length === before.p, 'стало ' + db.customers.length);
ok('идентификаторы те же', db.sites.map((s) => s.id).sort().join() === ids);

console.log('\nУДАЛЕНИЕ В ПРИЛОЖЕНИИ');
const victim = db.sites.find((s) => s.name === 'Пролетариата Донбасса 5');
let phone = JSON.parse(JSON.stringify(db));
findById(phone.sites, victim.id).deleted = true;
findById(phone.sites, victim.id).updatedAt = tick();
db = __sync(phone);
let back = findById(db.sites, victim.id);
ok('запись вернулась надгробием', !!back && back.deleted === true, JSON.stringify(back && back.deleted));
ok('строка в книге посерела', BOOK.getSheetByName('Объекты').style[victim.row].bg === '#F1F3F4');
ok('строка перечёркнута', BOOK.getSheetByName('Объекты').style[victim.row].line === 'line-through');

console.log('\nУДАЛЁННОЕ НЕ ВОЗВРАЩАЕТСЯ');
phone = JSON.parse(JSON.stringify(db));
for (let i = 0; i < 3; i++) phone = __sync(phone);
back = findById(phone.sites, victim.id);
ok('после трёх обменов всё ещё удалена', !!back && back.deleted === true);
ok('живых объектов остался один', phone.sites.filter((s) => !s.deleted).length === 1,
  'живых ' + phone.sites.filter((s) => !s.deleted).length);

console.log('\nНОВАЯ ЗАПИСЬ ИЗ ПРИЛОЖЕНИЯ');
phone = JSON.parse(JSON.stringify(phone));
const newId = crypto.randomUUID();
phone.sites.push({
  id: newId, code: '', name: 'Складской корпус', fullName: '', customerId: p.id,
  address: 'Луганск', buildingType: 'Склад', area: 0, unit: '', note: '',
  progress: 0, updatedAt: tick(), deleted: false, row: 0
});
db = __sync(phone);
const added = findById(db.sites, newId);
ok('новая запись попала в книгу', !!added && !added.deleted);
ok('она встала в блок данных, а не в конец листа', !!added && added.row >= 5 && added.row <= 12,
  'строка ' + (added && added.row));
ok('адрес доехал', BOOK.getSheetByName('Объекты').cell(added.row, 5) === 'Луганск');
ok('заказчик записан именем', BOOK.getSheetByName('Объекты').cell(added.row, 4) === p.name);

console.log('\nСТРОКУ ВЫРЕЗАЛИ В КНИГЕ');
const sheet = BOOK.getSheetByName('Объекты');
const killRow = added.row;
for (let c = 1; c <= 61; c++) sheet.set(killRow, c, '');
db = __sync(db);
const killed = findById(db.sites, newId);
ok('запись вернулась надгробием', !!killed && killed.deleted === true,
  killed ? 'deleted=' + killed.deleted : 'не пришла вовсе');

console.log('\nПРАВКА ИЗ ПРИЛОЖЕНИЯ ДОЕЗЖАЕТ');
phone = JSON.parse(JSON.stringify(db));
const live = phone.sites.find((s) => !s.deleted);
live.address = 'Луганск, Цимлянская 17';
live.updatedAt = tick();
db = __sync(phone);
ok('адрес записан в книгу', sheet.cell(live.row, 5) === 'Луганск, Цимлянская 17',
  sheet.cell(live.row, 5));
ok('и приехал обратно', findById(db.sites, live.id).address === 'Луганск, Цимлянская 17');

console.log('\nПРАВКА В КНИГЕ ПОБЕЖДАЕТ СТАРУЮ ВЕРСИЮ');
sheet.set(live.row, 5, 'Другой адрес');
db = __sync(db);
ok('адрес из книги победил', findById(db.sites, live.id).address === 'Другой адрес',
  findById(db.sites, live.id).address);

console.log('\nЗАДАЧИ ПО ДОГОВОРУ ПЕРЕЖИВАЮТ ОБМЕН');
phone = JSON.parse(JSON.stringify(db));
phone.contracts[0].tasks = [{ id: 'T1', text: 'Забрать исходники', done: false, createdAt: 1 }];
phone.contracts[0].updatedAt = tick();
db = __sync(phone);
ok('задача на месте', (db.contracts[0].tasks || []).length === 1,
  JSON.stringify(db.contracts[0].tasks));

console.log('\nРЕКВИЗИТЫ ЗАКАЗЧИКА ПИШУТСЯ ОБРАТНО');
phone = JSON.parse(JSON.stringify(db));
phone.customers[0].director = 'Петров П. П.';
phone.customers[0].updatedAt = tick();
db = __sync(phone);
const cSheet = BOOK.getSheetByName('Заказчики');
let wrote = false;
for (let r = 4; r < 20; r++) if (cSheet.cell(r, 3) === 'Руководитель') wrote = cSheet.cell(r, 4) === 'Петров П. П.';
ok('директор записан в карточку', wrote);
ok('и приехал обратно', db.customers[0].director === 'Петров П. П.', db.customers[0].director);

console.log('\nСОРТИРОВКА ЛИСТА НЕ РВЁТ СВЯЗИ');
// Меняем две строки объектов местами вместе со скрытым столбцом.
const rowA = 5, rowB = 6;
for (let c = 1; c <= 61; c++) {
  const a = sheet.cell(rowA, c), b = sheet.cell(rowB, c);
  sheet.set(rowA, c, b); sheet.set(rowB, c, a);
}
const idAtTopBefore = sheet.cell(rowA, C_ID);
db = __sync(db);
const moved = findById(db.sites, idAtTopBefore);
ok('запись нашлась по идентификатору', !!moved);
ok('и знает свою новую строку', !!moved && moved.row === rowA, moved && String(moved.row));

console.log('\nПЕРЕХОД СО СТАРОЙ ВЕРСИИ: РЕЕСТР НЕ ЗАДВАИВАЕТСЯ');
// В телефоне уже есть записи со своими идентификаторами, а книга про них
// ещё ничего не знает — ровно то, что случится при первом обмене после
// обновления скрипта. Книга обязана узнать их по имени, а не завести вторые.
freshBook();
const oldPhone = {
  customers: [{ id: 'C-СТАРЫЙ', name: 'ООО СК «ЭнергоКомплекс»', inn: '', updatedAt: CLOCK - 5000, deleted: false }],
  sites: [
    { id: 'S-СТАРЫЙ-1', name: 'Цимлянская 17', updatedAt: CLOCK - 5000, deleted: false },
    { id: 'S-СТАРЫЙ-2', name: 'Пролетариата Донбасса 5', updatedAt: CLOCK - 5000, deleted: false }
  ],
  contracts: [{ id: 'K-СТАРЫЙ', siteId: 'S-СТАРЫЙ-1', workKind: 'Обследование', updatedAt: CLOCK - 5000, deleted: false }],
  employees: [{ id: 'E-СТАРЫЙ', name: 'Патрушев Даниил Сергеевич', updatedAt: CLOCK - 5000, deleted: false }],
  handovers: []
};
const after = __sync(oldPhone);
ok('объектов по-прежнему двое', after.sites.filter((s) => !s.deleted).length === 2,
  'стало ' + after.sites.filter((s) => !s.deleted).length);
ok('объект узнал себя', !!findById(after.sites, 'S-СТАРЫЙ-1'));
ok('заказчик узнал себя', !!findById(after.customers, 'C-СТАРЫЙ'));
ok('сотрудник узнал себя', !!findById(after.employees, 'E-СТАРЫЙ'));
ok('договор узнал себя', !!findById(after.contracts, 'K-СТАРЫЙ'));
ok('договоров не прибавилось', after.contracts.filter((c) => !c.deleted).length === 1,
  'стало ' + after.contracts.filter((c) => !c.deleted).length);
ok('связь объект → заказчик жива', after.sites[0].customerId === 'C-СТАРЫЙ', after.sites[0].customerId);

console.log('\n' + (failures ? 'ПРОВАЛЕНО ПРОВЕРОК: ' + failures : 'ВСЕ ПРОВЕРКИ ПРОШЛИ'));
process.exit(failures ? 1 : 0);
