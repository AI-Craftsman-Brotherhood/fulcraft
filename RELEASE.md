# Release Guide

This document explains the release procedures for FUL.

---

## Pre-Release Checklist

Before releasing a new version, please complete the following items:

### 1. Update the CHANGELOG

```bash
# Edit CHANGELOG.md to include the release notes
# Format Example:
## [X.Y.Z] - YYYY-MM-DD
### Added
- Description of new features

### Changed
- Description of changes

### Fixed
- Description of bug fixes
```

**Checklist:**
- [ ] All significant changes are documented.
- [ ] The release date is correct.
- [ ] Breaking changes use the `BREAKING CHANGE:` prefix.

### 2. Update the Version Number

The version number adheres to [Semantic Versioning (SemVer)](https://semver.org/):

- **MAJOR (X.0.0)**: Incompatible API or breaking changes.
- **MINOR (0.X.0)**: Backward-compatible functionality additions.
- **PATCH (0.0.X)**: Backward-compatible bug fixes.

```text
# Update the version in gradle/libs.versions.toml
[versions]
ful = "X.Y.Z"  # New version number
```

### 3. Verify Tests and CI

```bash
# Run tests locally
./gradlew check

# Verify that the build succeeds
./gradlew :app:shadowJar

# (Optional) Run security scanning
./gradlew :app:dependencyCheckRuntimeAnalyze
./gradlew :app:dependencyCheckToolingAnalyze
```

**Checklist:**
- [ ] `./gradlew check` passes successfully.
- [ ] GitHub Actions CI workflows are all green.
- [ ] Code coverage has not decreased.

---

## Creating a Release

### Automated Release (Recommended)

Pushing a tag triggers GitHub Actions to automatically build, sign, and publish a release.

```bash
# 1. Create a tag
git tag -a vX.Y.Z -m "Release vX.Y.Z"

# 2. Push the tag
git push origin vX.Y.Z

# Example
git tag -a v0.2.0 -m "Release v0.2.0"
git push origin v0.2.0
```

This triggers the `.github/workflows/release.yml` workflow, which performs:

1. Set up the JDK 21 environment.
2. Build distributions via `./gradlew distChecksum`.
3. Verify checksums.
4. Create a GitHub Release.
5. Upload the artifacts (ZIP/JAR/SHA256, and `.asc` if signing is enabled).

**Check Progress:**
- Check the [Actions tab](https://github.com/AI-Craftsman-Brotherhood/fulcraft/actions/workflows/release.yml) on GitHub to view the workflow status.

### Manual Release (Fallback)

Use this method only if the automated release fails or you need to customize the release notes.

1. Create and push a tag (same as above).
2. Go to the **GitHub Repository** > **Releases** > **Draft a new release**.
3. Under **Choose a tag**, select the tag you just created (`vX.Y.Z`).
4. Enter `vX.Y.Z` as the **Release title**.
5. Copy the contents of the relevant version from `CHANGELOG.md` into the **Release notes**.
6. Download the artifacts from the failed workflow run, or build locally (see [Local Distribution Build](#local-distribution-build)).
7. Drag and drop the artifact files into the release.
8. Uncheck **Pre-release** if it's a stable release, then click **Publish release**.

---

## Post-Release Verification

After releasing, ensure the following:

- [ ] All the artifacts are visible on the release page.
- [ ] The downloaded ZIP package unzips smoothly.
- [ ] Running `./scripts/ful --version` prints the new version number.
- [ ] The quickstart links in the `README.md` still work correctly.

---

## Appendix

### Local Distribution Build

When you need to build distribution artifacts locally (e.g., for manual release or local testing):

```bash
./gradlew clean
./gradlew distChecksum
```

This generates the following files inside `app/build/distributions/`:

| File | Description |
|------|-------------|
| `ful-X.Y.Z.zip` | Distribution ZIP package (JAR + scripts + docs) |
| `ful-X.Y.Z.zip.sha256` | SHA256 checksum for the ZIP |
| `ful-X.Y.Z.jar` | Standalone Fat JAR |
| `ful-X.Y.Z.jar.sha256` | SHA256 checksum for the JAR |

**Verify checksums:**

```bash
cd app/build/distributions
sha256sum -c ful-X.Y.Z.zip.sha256
sha256sum -c ful-X.Y.Z.jar.sha256
```

Ensure both commands output an `OK` message.

### GPG Signing (Optional)

GPG signing is supported but not enabled by default. To enable it, configure one of the following environment variable sets before running `./gradlew distChecksum`:

**Option A: GnuPG agent (local machine with GPG keyring)**

```bash
export SIGNING_GNUPG_KEY_NAME="your-key-id"
export SIGNING_GNUPG_PASSPHRASE="your-passphrase"
```

**Option B: In-memory key (CI environments)**

```bash
export GPG_PRIVATE_KEY="-----BEGIN PGP PRIVATE KEY BLOCK-----..."
export GPG_PASSPHRASE="your-passphrase"
```

When signing is enabled, additional `.asc` files are generated alongside the ZIP and JAR:

| File | Description |
|------|-------------|
| `ful-X.Y.Z.zip.asc` | GPG signature for the ZIP |
| `ful-X.Y.Z.jar.asc` | GPG signature for the JAR |

**Verify GPG signatures:**

```bash
gpg --verify ful-X.Y.Z.zip.asc ful-X.Y.Z.zip
gpg --verify ful-X.Y.Z.jar.asc ful-X.Y.Z.jar
```

Ensure a `Good signature` message is displayed.

---

## Support

If any issues occur during the release process, please consult the following:

- [CONTRIBUTING.md](CONTRIBUTING.md) - Development Guidelines.
- [GitHub Issues](https://github.com/AI-Craftsman-Brotherhood/fulcraft/issues) - Bug tracking and questions.
