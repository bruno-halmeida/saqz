const assert = require('node:assert/strict');
const { spawnSync, execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { test } = require('node:test');
const root = path.resolve(__dirname, '..');
const config = {
  PROJECT_ID: 'saqz-production', API_KEY: 'test-only-key', GCM_SENDER_ID: '123',
  GOOGLE_APP_ID: '1:123:ios:test', BUNDLE_ID: 'app.saqz',
  CLIENT_ID: 'test.apps.googleusercontent.com', REVERSED_CLIENT_ID: 'com.googleusercontent.apps.test',
};

function fixture(t, { configuration = 'Release', firebase = config } = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'saqz-firebase-config-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const resources = path.join(dir, 'build/App.app');
  fs.mkdirSync(resources, { recursive: true });
  fs.copyFileSync(path.join(root, 'SaqzIOS/Info.plist'), path.join(resources, 'Info.plist'));
  if (firebase) {
    const plistDir = path.join(dir, 'SaqzIOS/Config', configuration === 'Debug' ? 'Dev' : 'Prod');
    fs.mkdirSync(plistDir, { recursive: true });
    const file = path.join(plistDir, 'GoogleService-Info.plist');
    fs.writeFileSync(file, JSON.stringify(firebase));
    execFileSync('plutil', ['-convert', 'xml1', file]);
  }
  return {
    dir,
    resources,
    run: () => spawnSync('/bin/sh', [path.join(root, 'scripts/copy-firebase-config.sh')], {
      encoding: 'utf8', env: { ...process.env, CONFIGURATION: configuration, SRCROOT: dir,
        TARGET_BUILD_DIR: path.join(dir, 'build'), UNLOCALIZED_RESOURCES_FOLDER_PATH: 'App.app',
        INFOPLIST_PATH: 'App.app/Info.plist', PRODUCT_BUNDLE_IDENTIFIER: 'app.saqz', SAQZ_ENVIRONMENT: 'prod' },
    }),
  };
}

test('Release cannot build without Firebase config', t => {
  const result = fixture(t, { firebase: null }).run();
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Production Firebase configuration is required/);
});

test('Release rejects Firebase config for another application', t => {
  const result = fixture(t, { firebase: { ...config, BUNDLE_ID: 'wrong.app' } }).run();
  assert.equal(result.status, 1);
  assert.match(result.stderr, /BUNDLE_ID must match/);
});

test('Firebase config needs Google OAuth as well as Firebase keys', t => {
  const { CLIENT_ID, ...incomplete } = config;
  const result = fixture(t, { firebase: incomplete }).run();
  assert.equal(result.status, 1);
  assert.match(result.stderr, /missing CLIENT_ID/);
});

test('Release cannot use the local emulator project', t => {
  const result = fixture(t, { firebase: { ...config, PROJECT_ID: 'saqz-local' } }).run();
  assert.equal(result.status, 1);
  assert.match(result.stderr, /production Firebase environment/);
});

test('valid production config is bundled with Google OAuth redirect exactly once', t => {
  const f = fixture(t);
  assert.equal(f.run().status, 0);
  assert.equal(f.run().status, 0);
  const read = file => JSON.parse(execFileSync('plutil', ['-convert', 'json', '-o', '-', path.join(f.resources, file)]));
  assert.deepEqual(read('GoogleService-Info.plist'), config);
  const info = read('Info.plist');
  assert.equal(info.GIDClientID, config.CLIENT_ID);
  assert.equal(info.CFBundleURLTypes.flatMap(type => type.CFBundleURLSchemes)
    .filter(scheme => scheme === config.REVERSED_CLIENT_ID).length, 1);
});

test('configless Debug removes stale Firebase config and can use the emulator', t => {
  const f = fixture(t, { configuration: 'Debug', firebase: null });
  const stale = path.join(f.resources, 'GoogleService-Info.plist');
  fs.writeFileSync(stale, 'stale production config');
  assert.equal(f.run().status, 0);
  assert.equal(fs.existsSync(stale), false);
});

test('Xcode executes the validated copy phase', () => {
  const project = JSON.parse(execFileSync('plutil', ['-convert', 'json', '-o', '-', path.join(root, 'SaqzIOS.xcodeproj/project.pbxproj')]));
  const phase = Object.values(project.objects).find(o => o.name === 'Copy Firebase Plist');
  assert.match(phase.shellScript, /\/bin\/sh "\$SRCROOT\/scripts\/copy-firebase-config.sh"/);
});

test('Release API phase accepts production and refuses a development endpoint', t => {
  const f = fixture(t);
  const project = JSON.parse(execFileSync('plutil', ['-convert', 'json', '-o', '-', path.join(root, 'SaqzIOS.xcodeproj/project.pbxproj')]));
  const phase = Object.values(project.objects).find(o => o.name === 'Configure API Base URL');
  const ios = path.join(f.dir, 'ios-app');
  const apiConfig = path.join(f.dir, 'compose-app/build/xcode-api-config/saqz-api-config.plist');
  fs.mkdirSync(ios);
  fs.mkdirSync(path.dirname(apiConfig), { recursive: true });
  for (const [url, expectedStatus] of [['http://127.0.0.1:8080', 1], ['https://saqz-api.brunoalmeida.dev', 1], ['https://api.saqz.app', 0]]) {
    fs.writeFileSync(apiConfig, JSON.stringify({ prod: url }));
    execFileSync('plutil', ['-convert', 'xml1', apiConfig]);
    const result = spawnSync('/bin/sh', ['-c', phase.shellScript], {
      encoding: 'utf8', env: { ...process.env, CONFIGURATION: 'Release', SRCROOT: ios,
        TARGET_BUILD_DIR: path.join(f.dir, 'build'), INFOPLIST_PATH: 'App.app/Info.plist' },
    });
    assert.equal(result.status, expectedStatus, result.stderr);
  }
  const info = JSON.parse(execFileSync('plutil', ['-convert', 'json', '-o', '-', path.join(f.resources, 'Info.plist')]));
  assert.equal(info.SaqzAPIBaseURL, 'https://api.saqz.app');
});
