# Contributing to CycloneDX Gradle Plugin

Welcome to the CycloneDX Gradle Plugin project! We appreciate your interest in contributing to this open source project.
This guide will help you get started with setting up your development environment and understanding our contribution
process.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Development Environment Setup](#development-environment-setup)
3. [Working with Gradle Toolchains](#working-with-gradle-toolchains)
4. [Code Style and Formatting](#code-style-and-formatting)
5. [Building and Testing](#building-and-testing)
6. [Publishing Plugin Locally](#publishing-plugin-locally)
7. [Submitting Contributions](#submitting-contributions)
8. [Community Guidelines](#community-guidelines)

## Getting Started

The CycloneDX Gradle Plugin creates Software Bill of Materials (SBOM) from Gradle projects, supporting the CycloneDX
specification. All open issues are welcome to contributors, and we value contributions of all sizes - from bug reports
and documentation improvements to new features.

Before contributing, please:

- Read through this contributing guide
- Check our [Code of Conduct](CODE_OF_CONDUCT.md)
- Browse existing [issues](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues) to see if your idea or bug has
  already been reported

## Development Environment Setup

### Prerequisites

- **Java Development Kit (JDK)**: Java 8 or higher
- **Git**: For version control
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java support

### Clone the Repository

```bash
git clone https://github.com/CycloneDX/cyclonedx-gradle-plugin.git
cd cyclonedx-gradle-plugin
```

## Working with Gradle Toolchains

This project uses Gradle toolchains to ensure compatibility across different Java versions. The build is configured to:

- **Target Java Version**: Java 8 (for maximum compatibility)
- **Testing**: Multiple LTS Java versions (8, 11, 17, 21)
- **Gradle**: Java 21

### Testing with Multiple Java Versions

Tests are automatically run against multiple Java versions:

```bash
# Run tests with all supported Java versions
./gradlew test

# Run tests with a specific Java version
./gradlew testJava8
./gradlew testJava11
./gradlew testJava17
./gradlew testJava21
```

### Configuring Local Toolchains

If you need to specify custom Java installations, you can configure toolchains in your global `gradle.properties` file (
`~/.gradle/gradle.properties`):

```properties
# Define custom Java installation paths
org.gradle.java.installations.paths=/path/to/jdk8,/path/to/jdk11,/path/to/jdk17,/path/to/jdk21
# Disable automatic detection if needed
org.gradle.java.installations.auto-detect=false
```

## Code Style and Formatting

This project uses the **Spotless** plugin with **PalantirJavaFormat** for consistent code formatting.

### Code Style Requirements

- **Formatter**: PalantirJavaFormat
- **Line Length**: 120 characters
- **Indentation**: 4 spaces
- **License Header**: Required on all Java files

### Working with Spotless

This project uses Spotless plugin that will:

- Check code formatting during build
- Apply automatic license headers
- Format code according to PalantirJavaFormat standards

### Running Spotless

```bash
# Check for formatting violations
./gradlew spotlessCheck

# Apply formatting fixes automatically
./gradlew spotlessApply
```

### Code Style Documentation

For detailed information about the PalantirJavaFormat style, see:

- **Official Documentation**: [PalantirJavaFormat on GitHub](https://github.com/palantir/palantir-java-format)
- **Spotless**: [palantir-java-format Gradle Plugin](https://github.com/diffplug/spotless)

## Building and Testing

### Building the Plugin

```bash
# Clean and build the project
./gradlew clean build
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests with specific Java version
./gradlew testJava11

# Run tests with verbose output
./gradlew test --info
```

### Continuous Integration

The project uses GitHub Actions for CI/CD. All pull requests must:

- Pass all tests on multiple Java versions (8, 11, 17, 21)
- Pass Spotless formatting checks
- Build successfully
- Every commit must be signed off with `git commit -s` to acknowledge
  the [Developer Certificate of Origin (DCO)](https://probot.github.io/apps/dco/)

## Publishing Plugin Locally

To test your changes with other projects, you can publish the plugin to your local Maven repository:

### Local Publishing Steps

1. **Build and publish locally:**
   ```bash
   ./gradlew publishToMavenLocal
   ```

2. **Configure your test project** to use the local plugin by adding to `settings.gradle` or `settings.gradle.kts`:
   ```kotlin
   pluginManagement {
       repositories {
           mavenLocal()  // This must be first for priority
           gradlePluginPortal()
           mavenCentral()
       }
   }
   ```

3. **Use the plugin** in your test project's `build.gradle`:
   ```groovy
   plugins {
       id 'org.cyclonedx.bom' version '<version>'
   }
   ```

### Verifying Local Installation

After publishing locally, you can verify the installation:

```bash
# Check local Maven repository
ls ~/.m2/repository/org/cyclonedx/cyclonedx-gradle-plugin/
```

## Submitting Contributions

### Pull Request Process

1. **Fork** the repository
2. **Create a branch** for your feature or fix:
   ```bash
   git checkout -b feature/my-new-feature
   ```
3. **Make your changes** following the coding standards
4. **Run tests and formatting**:
   ```bash
   ./gradlew spotlessApply
   ./gradlew test
   ```
5. **Commit your changes** with a clear message:
   ```bash
   git commit -s -m "feat: add support for new SBOM feature"
   ```
6. **Push to your fork** and **create a pull request**

### Pull Request Guidelines

- **Clear Description**: Explain what changes you made and why
- **Tests**: Include tests for new functionality
- **Documentation**: Update documentation for new features
- **Small Changes**: Keep PRs focused and manageable
- **Code Quality**: Ensure all checks pass (formatting, tests, build)

### Commit Message Format

We follow [conventional commit format](https://www.conventionalcommits.org/en/v1.0.0/):

- `feat:` for new features
- `fix:` for bug fixes
- `docs:` for documentation changes
- `style:` for formatting changes
- `refactor:` for code refactoring
- `test:` for adding tests
- `chore:` for maintenance tasks

## Community Guidelines

### Open Issues

**All open issues are welcome to contributors!** Whether you're a beginner or experienced developer, there are issues
suitable for all skill levels. Look for:

- **Good First Issue**: Great for newcomers
- **Help Wanted**: Issues that need community assistance
- **Bug**: Issues requiring fixes
- **Enhancement**: Feature requests and improvements

### Getting Help

If you need help with:

- **Development Questions**: Open a discussion or comment on relevant issues
- **CycloneDX Specification**: Visit [CycloneDX.org](https://cyclonedx.org)
- **Community Support**: Join the [CycloneDX Slack](https://cyclonedx.org/participate/contribute/)

### Code Review Process

1. All contributions require review from project maintainers
2. Feedback will be provided constructively and promptly
3. Address review comments and update your PR
4. Once approved, maintainers will merge your contribution

### Contributor Recognition

We value all contributions and recognize contributors through:

- Attribution in release notes
- Contributor acknowledgments
- Community recognition

## Additional Resources

- **Project Website**: [CycloneDX.org](https://cyclonedx.org)
- **CycloneDX Specification**: [CycloneDX Documentation](https://cyclonedx.org/docs/)
- **Issue Tracker**: [GitHub Issues](https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues)
- **Discussions**: [GitHub Discussions](https://github.com/CycloneDX/cyclonedx-gradle-plugin/discussions)

---

Thank you for contributing to CycloneDX Gradle Plugin! Your efforts help improve software supply chain security for the
entire community.
