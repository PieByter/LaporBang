# LaporBang — Pothole Reporting App (Android)

AI-powered mobile app for reporting road potholes. Uses YOLOv11 for pothole detection and U-Net for segmentation, with an API backend for persistence and analytics.

## Features
- Capture or import road images, auto-detect potholes (YOLOv11) and segment damage (U-Net).
- Attach location, notes, and severity; submit structured reports via REST API.
- Offline queue with background sync; retry on failure.
- Browse submitted reports with status tracking.
- Minimal battery/data impact (on-device prefilter; server-side heavy tasks optional).

## Architecture
- **Mobile:** Android (Kotlin/Java), CameraX, ML inference pipeline, Retrofit/OkHttp, Room for offline cache.
- **AI Models:** YOLOv11 (detection) + U-Net (segmentation). TFLite/ONNX runtime variants for mobile.
- **Backend:** REST API (pothole reports, media upload, auth), backed by relational DB. Ready-to-use API/database.

## Tech Stack
- Android SDK, Kotlin/Java, Jetpack (Lifecycle, ViewModel, Room), CameraX.
- Networking: Retrofit/OkHttp + TLS.
- ML: YOLOv11 + U-Net (TFLite/ONNX), optional server-side inference endpoint.
- Backend: REST API (JSON), relational DB (e.g., Postgres/MySQL).

## Data Flow
1. User captures/imports image.
2. On-device detection (YOLOv11) → optional segmentation (U-Net).
3. User adds metadata (location, severity, notes).
4. Report POSTed to API; media uploaded; response tracked.
5. Offline mode queues jobs; background worker syncs when online.

## API (examples)
- `POST /auth/login` — obtain token.
- `POST /reports` — create report (metadata).
- `POST /reports/{id}/media` — upload image.
- `GET /reports` — list reports (filter by status/user/date).
- `GET /reports/{id}` — detail + inference results.

## Models
- **YOLOv11:** Single-class pothole detector; optimized to TFLite/ONNX for mobile.
- **U-Net:** Segmentation mask for damaged surface area; optional step after detection.
- Include model files in `app/src/main/assets/models/` (e.g., `yolov11-pothole.tflite`, `unet-pothole.tflite`) or fetch from a secure CDN on first launch.

## Getting Started
1. Clone repository.
2. Place model files in `app/src/main/assets/models/` (or configure remote download).
3. Configure API base URL and tokens in `local.properties` or an `.env`-style config.
4. Build & run:
   - Android Studio: Build > Make Project.
   - CLI: `./gradlew assembleDebug` (or `gradlew.bat` on Windows).

## Configuration
- `API_BASE_URL`: REST endpoint.
- `API_TOKEN` or auth credentials.
- `MODEL_SOURCE`: `assets` or `remote`.
- `OFFLINE_SYNC`: enable/disable background retries.

## Testing
- Unit tests: `./gradlew test`
- Instrumentation/UI: `./gradlew connectedAndroidTest`
- (Optional) ML sanity check: run a small image set through YOLOv11 + U-Net and compare mAP/IoU to reference outputs.

## Performance Notes
- Prefer TFLite int8/float16 for speed; fall back to full precision on higher-end devices.
- Resize images before inference; cache models; reuse interpreters.
- Use server-side inference for very low-end devices (configurable).

## Roadmap
- Add multi-class road-defect support (cracks, patches).
- Push notifications on report status changes.
- Export masks to GIS formats.
- In-app analytics dashboard.

## Security & Privacy
- All API calls over TLS.
- Media stored securely; strip EXIF if required.
- Tokens stored in encrypted prefs/keystore.

## License
Specify your license (e.g., MIT/Apache-2.0) here.
