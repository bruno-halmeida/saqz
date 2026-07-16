# Android API 35 Gate Evidence

All probes ran independently against commit
`bf5cc792a13d1f8382d826667adfe69a1b3c6428` with this pinned tuple:

- API level: 35
- Target: `google_apis`
- Architecture: `x86_64`
- Profile: `pixel_7`
- RAM: `4096M`
- AVD: `saqz-api35-probe`
- Emulator build: `13823996`
- Boot timeout: 300 seconds
- Test class: `br.com.saqz.androidapp.ModernAndroidBehaviorTest`

| Run | Job | Result | Boot duration | Test result |
| --- | --- | --- | --- | --- |
| [29449093693](https://github.com/bruno-halmeida/saqz/actions/runs/29449093693) | [87466884129](https://github.com/bruno-halmeida/saqz/actions/runs/29449093693/job/87466884129) | success | 37.329s | 4 finished, 0 skipped, 0 failed |
| [29463089984](https://github.com/bruno-halmeida/saqz/actions/runs/29463089984) | [87510505508](https://github.com/bruno-halmeida/saqz/actions/runs/29463089984/job/87510505508) | success | 35.728s | 4 finished, build successful |
| [29463088994](https://github.com/bruno-halmeida/saqz/actions/runs/29463088994) | [87510503079](https://github.com/bruno-halmeida/saqz/actions/runs/29463088994/job/87510503079) | success | 35.417s | 4 finished, build successful |

The tuple above is the fixed T41 contract and is the tuple promoted by T42.
It differs from the older `google_atd` wording in AD-016; the three real runs
are the execution evidence used for promotion.
