const { test } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const plist = (file) => JSON.parse(execFileSync('plutil', ['-convert', 'json', '-o', '-', path.join(root, file)], { encoding: 'utf8' }));

test('manifest covers account content payments and linked diagnostics without tracking', () => {
  const manifest = plist('SaqzIOS/PrivacyInfo.xcprivacy');
  assert.equal(manifest.NSPrivacyTracking, false);
  assert.deepEqual(manifest.NSPrivacyTrackingDomains, []);
  const types = Object.fromEntries(manifest.NSPrivacyCollectedDataTypes.map(item => [item.NSPrivacyCollectedDataType, item]));
  for (const suffix of ['Name', 'EmailAddress', 'PhoneNumber', 'UserID', 'PhotosorVideos', 'OtherUserContent',
    'PhysicalAddress', 'PaymentInfo', 'OtherFinancialInfo', 'PurchaseHistory', 'DeviceID', 'CoarseLocation',
    'ProductInteraction', 'CrashData', 'PerformanceData']) {
    const item = types['NSPrivacyCollectedDataType' + suffix];
    assert.ok(item, suffix);
    assert.equal(item.NSPrivacyCollectedDataTypeLinked, true);
    assert.equal(item.NSPrivacyCollectedDataTypeTracking, false);
    assert.ok(item.NSPrivacyCollectedDataTypePurposes.length > 0);
  }
});

test('iOS analytics has no IDFA-capable product and disables advertising data uses', () => {
  const project = plist('SaqzIOS.xcodeproj/project.pbxproj');
  const products = Object.values(project.objects).filter(it => it.isa === 'XCSwiftPackageProductDependency').map(it => it.productName);
  assert.ok(products.includes('FirebaseAnalyticsCore'));
  assert.ok(!products.includes('FirebaseAnalytics'));
  const info = plist('SaqzIOS/Info.plist');
  for (const key of ['GOOGLE_ANALYTICS_IDFV_COLLECTION_ENABLED', 'GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_STORAGE',
    'GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_USER_DATA', 'GOOGLE_ANALYTICS_DEFAULT_ALLOW_AD_PERSONALIZATION_SIGNALS']) {
    assert.equal(info[key], false, key);
  }
  assert.deepEqual(info.CFBundleLocalizations, ['pt-BR']);
});

test('Android removes advertising permissions from transitive SDK manifests', () => {
  const manifest = readFileSync(path.join(root, '../android-app/src/main/AndroidManifest.xml'), 'utf8');
  for (const name of ['com.google.android.gms.permission.AD_ID', 'android.permission.ACCESS_ADSERVICES_AD_ID',
    'android.permission.ACCESS_ADSERVICES_ATTRIBUTION', 'android.permission.ACCESS_ADSERVICES_TOPICS']) {
    assert.ok(manifest.includes(`android:name="${name}" tools:node="remove"`));
  }
});
