const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');

const iosRoot = path.resolve(__dirname, '..');
const readPlist = (relativePath) => JSON.parse(execFileSync(
  'plutil', ['-convert', 'json', '-o', '-', path.join(iosRoot, relativePath)],
  { encoding: 'utf8' },
));
const project = readPlist('SaqzIOS.xcodeproj/project.pbxproj');
const target = Object.values(project.objects).find(
  (object) => object.isa === 'PBXNativeTarget' && object.name === 'SaqzIOS',
);
const configurations = project.objects[target.buildConfigurationList].buildConfigurations
  .map((id) => project.objects[id]);
const settings = (name) => configurations.find((config) => config.name === name).buildSettings;
const scheme = (name) => readFileSync(
  path.join(iosRoot, `SaqzIOS.xcodeproj/xcshareddata/xcschemes/${name}.xcscheme`), 'utf8',
);

test('SaqzDev runs Debug without entitlements unsupported by Personal Team', () => {
  assert.equal(settings('Debug').CODE_SIGN_STYLE, 'Automatic');
  assert.equal(settings('Debug').CODE_SIGN_ENTITLEMENTS, '');
  assert.match(scheme('SaqzDev'), /<LaunchAction\s+buildConfiguration="Debug"/);
});

test('SaqzProd preserves Associated Domains in Release', () => {
  assert.equal(settings('Release').CODE_SIGN_ENTITLEMENTS, 'SaqzIOS/SaqzIOS.entitlements');
  const entitlements = readPlist(settings('Release').CODE_SIGN_ENTITLEMENTS);
  assert.deepEqual(entitlements['com.apple.developer.associated-domains'], [
    'applinks:$(BRANCH_DOMAIN)',
  ]);
  assert.match(scheme('SaqzProd'), /<LaunchAction\s+buildConfiguration="Release"/);
});

test('development signing preserves app identity and native saqz links', () => {
  for (const name of ['Debug', 'Release']) {
    assert.equal(settings(name).PRODUCT_BUNDLE_IDENTIFIER, 'app.saqz');
    assert.equal(settings(name).DEVELOPMENT_TEAM, '8A3G5G4G2B');
    const info = readPlist(settings(name).INFOPLIST_FILE);
    const schemes = info.CFBundleURLTypes.flatMap((type) => type.CFBundleURLSchemes);
    assert.ok(schemes.includes('saqz'));
  }
});
