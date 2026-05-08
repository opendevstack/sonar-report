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
| `SonarApiClient` | HTTP transport layer — SonarQube API calls with SSL bypass | ~75 |
| `ReportBuilder` | Report orchestration — fetches data and builds PDF sections | ~510 |
| `PDFReportWriter` | PDF rendering utilities — low-level PDF operations | ~860 |

**Tests:** Unit tests in `src/test/java/com/ods/` using JUnit 5 + Mockito — 31 test cases covering CLI, HTTP, aggregation, and PDF utilities.

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
make test                    # Run unit tests (quiet)
make test-verbose            # Run unit tests (verbose)
make install                 # Install JAR to local Maven repository
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

## Testing

### Running Tests

#### Using Make (Recommended)

```bash
make test              # Run tests (quiet output)
make test-verbose      # Run tests with detailed output
```

#### Using Maven Wrapper (Direct)

```bash
cd pdf-generator
../mvnw test
```

### Test Structure

The project includes comprehensive unit tests covering all major components:

| Test Class | Module | Coverage |
|---|---|---|
| `GenerateSonarReportTest` | CLI argument parsing | 5 test cases |
| `ReportBuilderTest` | Report data aggregation | 13 test cases |
| `PDFReportWriterTest` | PDF utilities | 7 test cases |
| `SonarApiClientTest` | HTTP API transport | 6 test cases |
| **Total** | — | **31 test cases** |

### Test Details

**GenerateSonarReportTest** — Validates command-line argument parsing:
- All known flags (`--sonar-url`, `--token`, `--project`, `--branch`, `--output`)
- Empty arguments, dangling flags, non-flag arguments
- Duplicate flag handling

**ReportBuilderTest** — Tests data transformation utilities:
- `extractComponent()` — file path extraction from component identifiers
- `ratingToLetter()` — quality rating conversion (A–E)
- `minsToDaysHoursMins()` — technical debt time formatting
- `groupHotspotsByRule()` — aggregates security hotspots by rule key
- `groupIssuesByRule()` — aggregates code issues by rule key

**PDFReportWriterTest** — Tests PDF rendering utilities:
- `getCurrentGMTTimeFormatted()` — GMT timestamp formatting with regex validation
- `splitBySlash()` — path component splitting (handles slashes at end)

**SonarApiClientTest** — Tests HTTP API transport with mocked responses:
- Successful 200 responses return parsed JSON
- Error responses (401, 404) throw `IOException`
- URL encoding of special characters in project keys
- Branch parameter handling (with/without/blank)

### Test Framework

- **Framework:** JUnit 5 (Jupiter)
- **Mocking:** Mockito 5.11.0
- **Compiler:** Java 17+

All tests are run during the standard Maven build via the Maven Surefire plugin.

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
  [--branch <branch>] \
  [--output <file.pdf>]
```

### Arguments

| Flag | Required | Description |
|---|---|---|
| `--sonar-url` | Yes | Base URL of your SonarQube server, e.g. `https://sonarqube.example.com` |
| `--token` | Yes | SonarQube user token (Bearer authentication) |
| `--project` | Yes | The project key as shown in SonarQube, e.g. `com.example:my-project` |
| `--branch` | No | Branch to analyze (default: SonarQube project default branch) |
| `--output` | No | Output PDF filename (default: `sonarqube-report.pdf`) |

### Example

```bash
java -jar sonar-report-1.0-jar-with-dependencies.jar \
  --sonar-url "https://sonarqube.example.com" \
  --token "squ_abc123yourtoken" \
  --project "com.example:my-project" \
  --branch "main"
```

With a custom output filename:

```bash
java -jar sonar-report-1.0-jar-with-dependencies.jar \
  --sonar-url "https://sonarqube.example.com" \
  --token "squ_abc123yourtoken" \
  --project "com.example:my-project" \
  --branch "develop" \
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
- **Test Coverage:** Comprehensive unit tests (31 test cases) validate CLI parsing, API transport, data aggregation, and PDF utilities.
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
