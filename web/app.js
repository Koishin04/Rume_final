const $ = id => document.getElementById(id);
const filters = $('filters');
const editor = $('editor');
let demo = sessionStorage.getItem('mode') !== 'real';
let active = Number(sessionStorage.getItem('rume-user')) || (demo ? 2 : 0);
let state = {profiles: [], real: [], candidates: [], matches: [], inbox: []};
let creating = false;
let voting = false;
let revision = 0;

function element(tag, text, className) {
  const node = document.createElement(tag);
  node.textContent = text;
  if (className) node.className = className;
  return node;
}
function button(text, fn, style) {
  const node = element('button', text, style);
  node.type = 'button';
  node.onclick = fn;
  return node;
}
function account() { return state.profiles.find(p => p.id === active); }
function remember() {
  sessionStorage.setItem('mode', demo ? 'demo' : 'real');
  sessionStorage.setItem('rume-user', active);
}
function view(name) {
  for (const tab of document.querySelectorAll('nav button')) {
    const selected = tab.dataset.view === name;
    $(tab.dataset.view).hidden = !selected;
    if (selected) tab.setAttribute('aria-current','page');
    else tab.removeAttribute('aria-current');
  }
}
function openEditor(isNew) {
  creating = isNew || !account();
  const p = creating ? {} : account();
  for (const field of ['name','age','district','budget']) editor.elements[field].value = p[field] ?? '';
  $('picture').value = '';
  $('form-title').textContent = creating ? 'Новая анкета' : 'Моя анкета';
  $('save').textContent = creating ? 'Создать анкету' : 'Сохранить изменения';
  view('profile');
}
function empty(title, text, action) {
  const box = element('div','','empty');
  box.append(element('h3',title),element('p',text));
  if (action) box.append(action);
  return box;
}
function portrait(p, compact) {
  const holder = element('div','',compact ? 'avatar' : 'monogram');
  holder.textContent = p.name.slice(0,1);
  const img = new Image();
  img.className = 'portrait';
  img.alt = 'Фото: ' + p.name;
  img.draggable = false;
  img.onload = () => holder.replaceWith(img);
  img.src = '/photo?' + new URLSearchParams({id:p.id,demo:Number(demo)});
  return holder;
}
function reaction(label, mark, kind, id) {
  const node = button('', () => vote(kind,id), 'reaction ' + (kind === 'like' ? 'like' : 'pass'));
  const icon = element('b',mark);
  icon.setAttribute('aria-hidden','true');
  node.append(icon,element('span',label));
  return node;
}
function card(p) {
  const node = element('article','','card');
  const visual = element('div','','visual');
  const bio = element('div','','bio');
  bio.append(element('h2',p.name + ', ' + p.age),element('p',p.district + '   ·   ' + budget(p),'details'));
  visual.append(portrait(p,false),element('span',demo ? 'Демо-анкета' : 'Ищу соседа','chip'),bio);
  let start;
  visual.onpointerdown = event => {
    if (event.button !== 0) return;
    visual.setPointerCapture(event.pointerId);
    start = [event.clientX,event.clientY];
  };
  visual.onpointercancel = () => { start = null; };
  visual.onpointerup = event => {
    if (!start) return;
    const dx = event.clientX - start[0], dy = event.clientY - start[1];
    start = null;
    if (Math.abs(dx) > 65 && Math.abs(dx) > 1.5 * Math.abs(dy)) vote(dx > 0 ? 'like' : 'skip',p.id);
  };
  const actions = element('div','','actions');
  actions.append(reaction('Пропустить','×','skip',p.id),reaction('Лайк','♥','like',p.id));
  node.append(visual,actions);
  return node;
}
function budget(p) { return p.budget.toLocaleString('ru-RU') + ' ₸/мес'; }
function match(p) {
  const node = element('article','','match');
  const info = element('div','');
  info.append(element('h3',p.name + ', ' + p.age),element('p',p.district + ' · ' + budget(p)));
  node.append(portrait(p,true),info);
  return node;
}
function render() {
  const users = $('user');
  users.replaceChildren(new Option('Выберите пользователя','none'));
  for (const p of state.real) users.add(new Option(p.name + ' · #' + p.id,'real:'+p.id));
  if (demo) {
    for (const p of state.profiles) users.add(new Option('Демо · '+p.name,'demo:'+p.id));
  } else users.add(new Option('Открыть демо','demo:2'));
  users.add(new Option('+ Создать свою анкету','new'));
  users.value = active ? (demo ? 'demo:' : 'real:')+active : 'none';
  const current = state.candidates[0];
  $('count').textContent = 'В подборке: ' + state.candidates.length;
  $('cards').replaceChildren(current ? card(current) : empty('Карточки закончились',
    'Измените фильтры или добавьте новую анкету.',button('Создать анкету',()=>openEditor(true),'primary')));
  $('pairs').replaceChildren(...state.matches.map(match));
  if (!state.matches.length) $('pairs').append(empty('Совпадений пока нет','Здесь появятся люди, с которыми вы поставили друг другу лайк.'));
  $('messages').replaceChildren(...state.inbox.map(m => element('p',m,'message')));
  if (!state.inbox.length) $('messages').append(empty('Новых сообщений нет','При взаимном лайке здесь появится уведомление.'));
}
async function request(url,options) {
  const response = await fetch(url,options);
  const data = await response.json();
  if (!response.ok) throw Error(data.error || 'Не удалось выполнить действие.');
  return data;
}
async function refresh() {
  const ticket = ++revision;
  const query = new URLSearchParams(new FormData(filters));
  query.set('id',active); query.set('demo',Number(demo));
  const result = await request('/state?'+query);
  if (ticket !== revision) return;
  state = result;
  if (!account() && state.profiles.length) {
    active = state.profiles[0].id; remember(); return refresh();
  }
  render();
}
function error(e) { $('notice').textContent = e.message; }
async function change(fields) {
  return request('/action',{method:'POST',body:new URLSearchParams(fields)});
}
async function vote(action,target) {
  if (voting || state.candidates[0]?.id !== target) return;
  voting = true;
  document.querySelectorAll('.reaction').forEach(b => b.disabled = true);
  try {
    const result = await change({action,id:active,target,demo:Number(demo)});
    $('notice').textContent = result.message;
    await refresh();
  } catch(e) { error(e); }
  finally { voting = false; document.querySelectorAll('.reaction').forEach(b => b.disabled = false); }
}
// Resize browser uploads before sending a JPEG to the Java server.
async function preparePhoto(file) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1,1000/Math.max(bitmap.width,bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1,Math.round(bitmap.width*scale));
  canvas.height = Math.max(1,Math.round(bitmap.height*scale));
  canvas.getContext('2d').drawImage(bitmap,0,0,canvas.width,canvas.height);
  bitmap.close();
  return new Promise((resolve,reject)=>canvas.toBlob(b => b ? resolve(b) : reject(Error('Не удалось прочитать фото.')),'image/jpeg',0.82));
}
editor.onsubmit = async event => {
  event.preventDefault();
  $('save').disabled = true;
  try {
    const file = $('picture').files[0];
    const photo = file ? await preparePhoto(file) : null;
    const isDemo = !creating && demo;
    const fields = Object.fromEntries(new FormData(editor));
    const result = await change({...fields,action:creating?'create':'edit',id:active,demo:Number(isDemo)});
    demo = isDemo; active = result.id; creating = false; remember();
    if (photo) await request('/photo?'+new URLSearchParams({id:active,demo:Number(demo)}),{method:'POST',body:photo});
    await refresh(); view('search');
    $('notice').textContent = result.message;
  } catch(e) { error(e); } finally { $('save').disabled = false; }
};
$('user').onchange = async event => {
  const value = event.target.value;
  if (value === 'new') { openEditor(true); return; }
  if (value === 'none') { openEditor(true); return; }
  const [mode,id] = value.split(':');
  demo = mode === 'demo'; active = Number(id); remember(); filters.reset();
  $('notice').textContent = '';
  try { await refresh(); view('search'); } catch(e) { error(e); }
};
function toggleFilters(show) {
  filters.hidden = !show;
  $('tune').setAttribute('aria-expanded',String(show));
}
$('tune').onclick = () => { view('search'); toggleFilters(filters.hidden); };
$('open-inbox').onclick = () => {view('inbox');refresh().catch(error);};
filters.onsubmit = event => { event.preventDefault();toggleFilters(false);refresh().catch(error); };
for (const tab of document.querySelectorAll('nav button')) tab.onclick = () => {
  $('notice').textContent = '';
  if (tab.dataset.view === 'profile') openEditor(false);
  else {view(tab.dataset.view);refresh().catch(error);}
};
view('search');refresh().catch(error);
