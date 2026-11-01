// Executes the production PC-page handlers against an ADB-forwarded emulator.
// This tests JavaScript + HTTP integration, not browser layout/permissions.
// Pair first; keep the token in ignored .signing/test-session.json. Node 22+.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const base = 'http://127.0.0.1:8765';
const mode = process.argv[2] || 'upload';
const token = JSON.parse(fs.readFileSync('.signing/test-session.json', 'utf8')).token;
const receiptPath = '.signing/apk-test-receipt.json';
const elements = new Map();
function element(id) {
  if (!elements.has(id)) elements.set(id, {id, value: '', files: [], textContent: '', disabled: false, replaceChildren() {}, append() {}, focus() {}});
  return elements.get(id);
}
async function api(path) {
  const r = await fetch(base + path, {headers: {Authorization: 'Bearer ' + token}});
  assert.equal(r.status, 200, 'Paired emulator API unavailable');
  return r.json();
}
let droppedResponse = false;
class TestXHR {
  upload = {};
  headers = {};
  open(method, path) { this.method = method; this.path = path; }
  setRequestHeader(key, value) { this.headers[key] = value; }
  abort() { this.controller?.abort(); }
  async send(body) {
    this.controller = new AbortController();
    try {
      this.upload.onprogress?.({lengthComputable: true, loaded: body.size, total: body.size});
      if (mode === 'cancel') element('cancelApk').onclick();
      const r = await fetch(base + this.path, {method: this.method, headers: this.headers, body, signal: this.controller.signal});
      this.status = r.status; this.responseText = await r.text();
      // Drop one successful response: production code must recover the committed offset.
      if (mode === 'upload' && !droppedResponse && r.ok) { droppedResponse = true; this.onerror(); }
      else this.onload();
    } catch (e) { if (this.controller.signal.aborted) this.onabort(); else this.onerror(); }
  }
}
async function main() {
  const html = fs.readFileSync('app/src/main/assets/manager.html', 'utf8');
  const context = vm.createContext({document: {getElementById: element, querySelectorAll: () => [...elements.values()], createElement: () => ({append() {}, click() {}})},
    fetch: (path, options) => fetch(new URL(path, base), options), XMLHttpRequest: TestXHR, confirm: () => true, Blob, URL, setTimeout});
  vm.runInContext(html.split('<script>')[1].split('</script>')[0], context);
  vm.runInContext('token=' + JSON.stringify(token), context);
  const before = await api('/library');
  const initial = await api('/apk');
  if (mode === 'delete') {
    assert.deepEqual(initial.apk, JSON.parse(fs.readFileSync(receiptPath, 'utf8')), 'Refusing to delete a file outside this test receipt');
    await element('deleteApk').onclick();
    assert.equal((await api('/apk')).apk, null);
    assert.match(element('apkMessage').textContent, /deleted/);
    fs.unlinkSync(receiptPath);
  } else {
    assert.equal(initial.apk, null, 'Remove any existing APK manually before this test');
    assert.equal((await api('/apk-upload')).uploadId, null, 'Another transfer is active');
    if (mode !== 'validation') {
      const file = new Blob([fs.readFileSync('app/build/outputs/apk/release/app-release.apk')]);
      file.name = 'mortal-tv-test.apk'; element('apkFile').files = [file];
    }
    await element('uploadApk').onclick();
    if (mode === 'upload') {
      const result = (await api('/apk')).apk;
      assert.equal(result?.packageName, 'lk.mortal.tv');
      assert.match(element('apkMessage').textContent, /Upload complete/);
      assert.ok(droppedResponse, 'Retry recovery branch was not exercised');
      fs.writeFileSync(receiptPath, JSON.stringify(result));
    } else if (mode === 'cancel') {
      assert.equal((await api('/apk')).apk, null);
      assert.equal((await api('/apk-upload')).uploadId, null);
      assert.match(element('apkMessage').textContent, /cancelled/);
    } else if (mode === 'validation') assert.match(element('apkMessage').textContent, /Choose an APK/);
    else throw Error('Use upload, delete, cancel or validation');
  }
  assert.deepEqual(await api('/library'), before, 'APK actions changed the channel/link library');
  assert.ok([...elements.values()].every(e => !e.disabled), 'Controls stayed disabled');
  console.log('PC APK handler + emulator HTTP integration passed: ' + mode);
}
main().catch(e => { console.error(e.message); process.exitCode = 1; });
