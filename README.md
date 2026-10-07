# EarLab — headphone test lab

Frequency sweeps, channel checks, hearing walkthroughs and burn-in noise.
Everything is synthesized on-device with AudioTrack — no assets, no network,
no account.

- **Sweep**: logarithmic 20 Hz → 20 kHz in 30 s with live readout and progress arc
- **Ears**: 440 Hz left / right / both identification
- **Hearing**: 6 frequencies × both ears, descending thresholds, saved result
- **Noise**: pink / white looping with 1-5-15 min timer

Start quiet. The sweep fades in over 2 seconds; keep the volume low.

## Build

CI builds every push (`Android CI` workflow): lint + debug APK always; signed
release APK (R8 + minify) when keystore secrets are present.
