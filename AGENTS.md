# Project Instructions

## Git & Repository Workflow
- For git operations and GitHub updates on this repository, push direct commits to the `main` branch instead of opening Pull Requests.

## Release & Versioning Rules
- The release version is fixed to `1.0.0` (`APKUpdater-1.0.0.apk`). Do not bump or change this version unless explicitly requested by the user.
- GitHub Actions workflow is set up at `.github/workflows/release.yml` to automatically build and publish `APKUpdater-1.0.0.apk` to GitHub Releases on push to `main` or manual trigger.
