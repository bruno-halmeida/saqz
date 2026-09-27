#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const DEBUG_CERTIFICATE = '10:01:9C:47:79:13:10:94:5D:BA:03:50:6E:89:D7:53:FE:A0:1A:8F:5F:6F:5B:4A:CF:BF:16:F9:FD:DB:DA:8F';
const defaultFile = path.resolve(__dirname, '../.well-known/assetlinks.json');

function normalizeFingerprint(value) {
  const input = value.trim();
  if (!/^(?:[a-f\d]{64}|(?:[a-f\d]{2}:){31}[a-f\d]{2})$/i.test(input)) {
    throw new Error('SHA-256 inválido: copie os 32 pares hexadecimais do certificado de assinatura do app no Play Console.');
  }
  const fingerprint = input.replaceAll(':', '').toUpperCase().match(/.{2}/g).join(':');
  if (fingerprint === DEBUG_CERTIFICATE) throw new Error('O certificado informado é de debug. Use o certificado de assinatura do Google Play.');
  if (/^(00:){31}00$/.test(fingerprint)) throw new Error('Um fingerprint preenchido com zeros não é um certificado de produção.');
  return fingerprint;
}

function association(fingerprints) {
  if (!fingerprints.length) throw new Error('Falta o SHA-256 do certificado de assinatura do app no Google Play.');
  return [{
    relation: ['delegate_permission/common.handle_all_urls'],
    target: {
      namespace: 'android_app',
      package_name: 'app.saqz',
      sha256_cert_fingerprints: [...new Set(fingerprints.map(normalizeFingerprint))],
    },
  }];
}

function validateAssociation(data) {
  if (!Array.isArray(data) || data.length !== 1 ||
      data[0]?.target?.namespace !== 'android_app' || data[0]?.target?.package_name !== 'app.saqz' ||
      !Array.isArray(data[0]?.relation) || !data[0].relation.includes('delegate_permission/common.handle_all_urls') ||
      !Array.isArray(data[0]?.target?.sha256_cert_fingerprints)) {
    throw new Error('assetlinks.json não associa corretamente o pacote app.saqz.');
  }
  association(data[0].target.sha256_cert_fingerprints);
}

function main(args) {
  const fingerprints = [];
  let file = defaultFile;
  let check = false;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--check') check = true;
    else if (args[i] === '--sha256' && args[i + 1]) fingerprints.push(args[++i]);
    else if (args[i] === '--file' && args[i + 1]) file = path.resolve(args[++i]);
    else throw new Error('Uso: node links-page/scripts/configure-app-links.cjs --sha256 "SHA-256 DO PLAY" [--sha256 "OUTRA CHAVE DO PLAY"] ou --check');
  }
  if (check) {
    if (fingerprints.length) throw new Error('Use --check sem --sha256 para verificar o arquivo já configurado.');
    validateAssociation(JSON.parse(fs.readFileSync(file, 'utf8')));
    console.log('Associação Android validada. Confirme a origem do certificado no Play Console antes de publicar.');
  } else {
    const data = association(fingerprints);
    fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n');
    console.log('Associação atualizada para app.saqz. Nenhum arquivo foi publicado.');
  }
}

if (require.main === module) {
  try { main(process.argv.slice(2)); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = { normalizeFingerprint, association, validateAssociation, DEBUG_CERTIFICATE };
