# Release Guide

This document explains the release procedures for FUL.

---

## 📋 Pre-Release Checklist

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
# Update the version in gradle/libs.versions.toml (or appropriate config file)
[versions]
ful = "X.Y.Z"  # New version number
```

### 3. Verify All Tests and CI Success

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

## 📦 Generating Distributions

### Step 1: Clean Build

```bash
./gradlew clean
```

### Step 2: Generate Distributions, Signatures, and Checksums

```bash
./gradlew distChecksum
```

If you wish to enable GPG signing, configure the following environment variables:

```bash
export SIGNING_GNUPG_KEY_NAME="your-key-id"
export SIGNING_GNUPG_PASSPHRASE="your-passphrase"
```

For in-memory signing, you can also use Gradle environment variables:

```bash
export ORG_GRADLE_PROJECT_signingKey="-----BEGIN PGP PRIVATE KEY BLOCK-----..."
export ORG_GRADLE_PROJECT_signingPassword="your-passphrase"
```

In CI environments (e.g., GitHub Actions), the following variable names might be used instead:

```bash
export GPG_PRIVATE_KEY="-----BEGIN PGP PRIVATE KEY BLOCK-----..."
export GPG_PASSPHRASE="your-passphrase"
```

This will generate the following files inside `app/build/distributions/`:

| File | Description |
|---------|------|
| `ful-X.Y.Z.zip` | Distribution ZIP package (JAR + scripts + docs) |
| `ful-X.Y.Z.zip.sha256` | SHA256 checksum for the ZIP |
| `ful-X.Y.Z.zip.asc` | GPG signature for the ZIP |
| `ful-X.Y.Z.jar` | Standalone Fat JAR |
| `ful-X.Y.Z.jar.sha256` | SHA256 checksum for the JAR |
| `ful-X.Y.Z.jar.asc` | GPG signature for the JAR |

### Step 3: Verify Checksums and Signatures

```bash
cd app/build/distributions
sha256sum -c ful-X.Y.Z.zip.sha256
sha256sum -c ful-X.Y.Z.jar.sha256
```

Ensure both commands output an `OK` message.

**Verify GPG Signatures:**

```bash
gpg --verify ful-X.Y.Z.zip.asc ful-X.Y.Z.zip
gpg --verify ful-X.Y.Z.jar.asc ful-X.Y.Z.jar
```

Ensure a `Good signature` message is displayed.

## 🔗 Future Supply Chain Security Improvements

- Integration with SLSA provenance and Sigstore-based signing will be considered.
- These procedures will be updated once further implementations are introduced.

---

## 🚀 Creating a GitHub Release

### 🤖 Method 1: Automated Release (Recommended)

Simply pushing a new tag will trigger GitHub Actions to automatically build and release.

```bash
# 1. Create a tag
git tag -a vX.Y.Z -m "Release vX.Y.Z"

# 2. Push the tag
git push origin vX.Y.Z
```

This triggers the `.github/workflows/release.yml` workflow, performing the following automated steps:

1. ✅ Set up the JDK 21 environment.
2. ✅ Build distributions via `./gradlew distChecksum`.
3. ✅ Verify checksums.
4. ✅ Create a GitHub Release.
5. ✅ Upload the artifacts (ZIP/JAR/SHA256/asc).

**Check Progress:**
- Check the [Actions tab](../../actions/workflows/release.yml) on GitHub to view the workflow status.

### 📝 Method 2: Manual Release

If the automated release fails for any reason or you need to customize the release notes manually:

#### Step 1: Create a Release Tag

```bash
# Create an annotated tag
git tag -a vX.Y.Z -m "Release vX.Y.Z"

# Example
git tag -a v1.0.0 -m "Release v1.0.0"
```

#### Step 2: Push the Tag

```bash
git push origin vX.Y.Z

# Example
git push origin v1.0.0
```

#### Step 3: Create the Release on GitHub

1. Go to the **GitHub Repository**.
2. Click on **Releases** → **Draft a new release**.
3. Under **Choose a tag**, select the tag you just created (`vX.Y.Z`).
4. Enter `vX.Y.Z` as the **Release title**.
5. Copy the contents of the relevant version from `CHANGELOG.md` into the **Release notes**.

#### Step 4: Upload Artifacts

Drag and drop the following files into the release:

- `ful-X.Y.Z.zip`
- `ful-X.Y.Z.zip.sha256`
- `ful-X.Y.Z.zip.asc`
- `ful-X.Y.Z.jar`
- `ful-X.Y.Z.jar.sha256`
- `ful-X.Y.Z.jar.asc`

#### Step 5: Publish the Release

- Uncheck **Pre-release** if it's a stable release.
- Click **Publish release**.

---

## ✅ Post-Release Verification

After releasing, ensure the following:

- [ ] All the artifacts are visible on the release page.
- [ ] The downloaded ZIP package unzips smoothly.
- [ ] Running `./ful --version` or `./ful-cli --version` prints the new version number.
- [ ] The quickstart links in the `README.md` still work correctly.

---

## 🔮 Future Extensions

### Publishing to Maven Central

If the project is to be published as a library to Maven Central, refer to the following resources:

- [Gradle Publishing to Maven Central](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Sonatype OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [gradle-nexus-publish-plugin](https://github.com/gradle-nexus/publish-plugin)

**Prerequisites:**
1. Create a Sonatype OSSRH account.
2. Generate and publish a GPG key.
3. Add the `maven-publish` plugin to your `build.gradle.kts`.

### Distributing Docker Images

If publishing an image to Docker Hub or GitHub Container Registry (GHCR):

- [Docker Hub Quick Start](https://docs.docker.com/docker-hub/quickstart/)
- [Working with the Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)

**Dockerfile Example:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY app/build/libs/ful-*.jar /app/ful.jar
ENTRYPOINT ["java", "-jar", "/app/ful.jar"]
```

### GitHub Actions Workflow

The automatic release flow is defined within `.github/workflows/release.yml`.
See [release.yml](../.github/workflows/release.yml) for details.

---

## 📞 Support

If any issues occur during the release process, please consult the following:

- [CONTRIBUTING.md](CONTRIBUTING.md) - Development Guidelines.
- [GitHub Issues](https://github.com/your-org/fulcraft/issues) - Bug tracking and questions.
