# AI Camera
Android camera prototype.

- Original photos: `DCIM/Camera`
- AI output: `DCIM/AI Camera`
- CameraX capture + WorkManager background pipeline
- Original is never overwritten.

## AI processing
The app already queues every captured image for the AI pipeline. The provider call belongs in `AiEditWorker`. API credentials must be kept server-side, never embedded in the APK. Until that backend is connected, v0.1 writes a separate placeholder copy into the AI Camera album so the full capture/storage flow can be tested safely.

GitHub Actions builds a debug APK on every push.
