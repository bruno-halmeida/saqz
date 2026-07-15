import { FirebaseApp, FirebaseOptions } from 'firebase/app';
import { Auth } from 'firebase/auth';
import { describe, expect, it } from 'vitest';
import { bootstrapSaqzWeb } from './local-firebase.bootstrap';
import {
  FirebaseAuthBootstrapClient,
  initializeLocalFirebaseAuth,
} from './local-firebase';

describe('local Firebase Auth bootstrap', () => {
  it('uses the exact local Firebase options and Auth Emulator endpoint', () => {
    const client = new RecordingFirebaseClient();

    initializeLocalFirebaseAuth(client);

    expect(client.events).toEqual([
      {
        type: 'initializeApp',
        options: {
          apiKey: 'fake-saqz-local-api-key',
          authDomain: 'saqz-local.firebaseapp.com',
          projectId: 'saqz-local',
          appId: '1:123456789000:web:saqzlocal',
          messagingSenderId: '123456789000',
        },
      },
      { type: 'getAuth' },
      { type: 'connectAuthEmulator', url: 'http://127.0.0.1:9099' },
    ]);
  });

  it('connects Auth Emulator before Angular bootstrap completes', async () => {
    const client = new RecordingFirebaseClient();

    await bootstrapSaqzWeb(client, () => {
      client.events.push({ type: 'bootstrapAngular' });
      return Promise.resolve();
    });

    expect(client.events.map((event) => event.type)).toEqual([
      'initializeApp',
      'getAuth',
      'connectAuthEmulator',
      'bootstrapAngular',
    ]);
  });
});

type FirebaseEvent =
  | { type: 'initializeApp'; options: FirebaseOptions }
  | { type: 'getAuth' }
  | { type: 'connectAuthEmulator'; url: string }
  | { type: 'bootstrapAngular' };

class RecordingFirebaseClient implements FirebaseAuthBootstrapClient {
  readonly events: FirebaseEvent[] = [];
  private readonly app = {} as FirebaseApp;
  private readonly auth = {} as Auth;

  initializeApp(options: FirebaseOptions): FirebaseApp {
    this.events.push({ type: 'initializeApp', options });
    return this.app;
  }

  getAuth(app: FirebaseApp): Auth {
    expect(app).toBe(this.app);
    this.events.push({ type: 'getAuth' });
    return this.auth;
  }

  connectAuthEmulator(auth: Auth, url: string): void {
    expect(auth).toBe(this.auth);
    this.events.push({ type: 'connectAuthEmulator', url });
  }
}
