# sonar-report

A command-line Java tool that connects to a SonarQube instance, retrieves code analysis metrics via its REST API, and generates a formatted PDF report.

The report includes quality ratings (reliability, security, maintainability), test coverage, technical debt, security hotspots, issue breakdowns by type and severity, and per-language code metrics — with an indexed table of contents and hyperlinks back to SonarQube coding rules.

---

## Requirements

- Java 17+
- Access to a SonarQube instance and a valid authentication token

**Note:** Maven is not required — the project includes a Maven wrapper that downloads Maven 3.9.6 automatically on first use.

---

## Architecture

The project is organized with clean separation of concerns:

| Module | Purpose | Lines |
|---|---|---|
| `GenerateSonarReport` | Entry point — command-line argument parsing | ~15 |
| `SonarApiClient` | HTTP transport layer — SonarQube API calls with SSL bypass | ~70 |
| `ReportBuilder` | Report orchestration — fetches data and builds PDF sections | ~510 |
| `PDFReportWriter` | PDF rendering utilities — low-level PDF operations | ~860 |

**Package:** `com.ods`

The design minimizes coupling: API client is isolated from report logic, HTTP client is built once and reused, and section builders are cohesive private methods within `ReportBuilder`.

---

## Build

### Using Make (Recommended)

```bash
make build
```

All available Make targets:

```bash
make help                    # Show all available targets
make build                   # Build and package the JAR
make clean                   # Remove build artifacts
make install                 # Install JAR to local Maven repository
make test                    # Run tests (if any)
make quick                   # Fast build without tests
```

### Using Maven Wrapper (Direct)

```bash
cd pdf-generator
../mvnw clean package
```

Or on Windows:

```cmd
cd pdf-generator
..\mvnw.cmd clean package
```

The output JAR is placed at:

```
pdf-generator/target/sonar-report-1.0-jar-with-dependencies.jar
```

Pre-built JARs are also attached to each [GitHub Release](../../releases).

---

## Usage

### Using Make

```bash
make run SONAR_URL='https://sonarqube.example.com' SONAR_TOKEN='squ_abc123yourtoken' SONAR_PROJECT='com.example:my-project'
```

### Using Java Directly

```bash
java -jar sonar-report-1.0-jar-with-dependencies.jar \
  --sonar-url <url> \
  --token <token> \
  --project <key> \
  [--output <file.pdf>]
```

### Arguments

| Flag | Required | Description |
|---|---|---|
| `--sonar-url` | Yes | Base URL of your SonarQube server, e.g. `https://sonarqube.example.com` |
| `--token` | Yes | SonarQube user token (Bearer authentication) |
| `--project` | Yes | The project key as shown in SonarQube, e.g. `com.example:my-project` |
| `--output` | No | Output PDF filename (default: `sonarqube-report.pdf`) |

### Example

```bash
java -jar sonar-report-1.0-jar-with-dependencies.jar \
  --sonar-url "https://sonarqube.example.com" \
  --token "squ_abc123yourtoken" \
  --project "com.example:my-project"
```

With a custom output filename:

```bash
java -jar sonar-report-1.0-jar-with-dependencies.jar \
  --sonar-url "https://sonarqube.example.com" \
  --token "squ_abc123yourtoken" \
  --project "com.example:my-project" \
  --output "my-project-report.pdf"
```

The report is written to the specified file (or `sonarqube-report.pdf` by default) in the current working directory.

---

## Report Contents

The generated PDF contains:

- Project name, version, and quality gate status
- Quality ratings: Reliability, Security, Maintainability (grades A–E)
- Code coverage, complexity, duplication, and comment density
- Technical debt (formatted as days/hours/minutes)
- Security hotspots and vulnerabilities
- Open issues grouped by type and severity, linked to coding rules
- Metrics broken down by programming language
- Auto-generated table of contents with page bookmarks

---

## Notes

- The tool accepts self-signed TLS certificates, making it suitable for internal SonarQube deployments.
- Paginated API results (hotspots, issues) are fully iterated — no result cap at 500.
- All arguments are passed as named flags (`--sonar-url`, `--token`, `--project`, `--output`); no environment variables or config files are required.
- **Bug fix:** Tests section now fetches correct metrics (was incorrectly reusing Metrics URL).
- **Performance:** HTTP client is built once and reused across all API calls.

---

## CI/CD

Two GitHub Actions workflows are included:

| Workflow | Trigger | Purpose |
|---|---|---|
| `build_jar.yml` | Push, PR, Release | Builds the uber-JAR and attaches it to releases |
| `check_calls.yml` | Push, PR | Validates all SonarQube API endpoints used by the tool |

The JAR artifact is named after the branch, PR number, or release tag automatically.

---

## License

See [LICENSE](LICENSE).
