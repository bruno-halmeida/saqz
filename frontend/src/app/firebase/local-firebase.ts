import { FirebaseApp, FirebaseOptions, initializeApp } from 'firebase/app';
import { Auth, connectAuthEmulator, getAuth } from 'firebase/auth';

export const localFirebaseOptions: FirebaseOptions = {
  apiKey: 'fake-saqz-local-api-key',
  authDomain: 'saqz-local.firebaseapp.com',
  projectId: 'saqz-local',
  appId: '1:123456789000:web:saqzlocal',
  messagingSenderId: '123456789000',
};

export const localAuthEmulatorUrl = 'http://127.0.0.1:9099';

export interface FirebaseAuthBootstrapClient {
  initializeApp(options: FirebaseOptions): FirebaseApp;
  getAuth(app: FirebaseApp): Auth;
  connectAuthEmulator(auth: Auth, url: string): void;
}

export const liveFirebaseAuthBootstrapClient: FirebaseAuthBootstrapClient = {
  initializeApp,
  getAuth,
  connectAuthEmulator(auth, url) {
    connectAuthEmulator(auth, url, { disableWarnings: true });
  },
};

export function initializeLocalFirebaseAuth(
  client: FirebaseAuthBootstrapClient = liveFirebaseAuthBootstrapClient,
): Auth {
  const app = client.initializeApp(localFirebaseOptions);
  const auth = client.getAuth(app);
  client.connectAuthEmulator(auth, localAuthEmulatorUrl);
  return auth;
}
