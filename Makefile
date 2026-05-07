.PHONY: help build clean run install package

# Default target
help:
	@echo "sonar-report - Makefile targets"
	@echo ""
	@echo "Note: Uses Maven wrapper (./mvnw) - no Maven installation needed"
	@echo ""
	@echo "Targets:"
	@echo "  build       Build the project and generate JAR"
	@echo "  clean       Remove build artifacts"
	@echo "  package     Create distribution JAR (alias: build)"
	@echo "  run         Run the JAR with SonarQube (requires SONAR_URL, SONAR_TOKEN, SONAR_PROJECT)"
	@echo "  install     Install JAR to local Maven repository"
	@echo "  test        Run Maven tests (if any)"
	@echo ""
	@echo "Environment variables for 'make run':"
	@echo "  SONAR_URL      SonarQube base URL (required)"
	@echo "  SONAR_TOKEN    Authentication token (required)"
	@echo "  SONAR_PROJECT  Project key (required)"
	@echo ""
	@echo "Example:"
	@echo "  make build"
	@echo "  make run SONAR_URL='https://sonarqube.example.com' SONAR_TOKEN='squ_token' SONAR_PROJECT='com.example:my-project'"

# Build the project
build: clean
	./mvnw -f pdf-generator/pom.xml clean package -DskipTests

# Clean build artifacts
clean:
	./mvnw -f pdf-generator/pom.xml clean
	rm -f sonarqube-report.pdf

# Alias for build
package: build

# Install JAR to local Maven repository
install:
	./mvnw -f pdf-generator/pom.xml install -DskipTests

# Run the application
run: build
	@if [ -z "$(SONAR_URL)" ] || [ -z "$(SONAR_TOKEN)" ] || [ -z "$(SONAR_PROJECT)" ]; then \
		echo "Error: Required environment variables not set"; \
		echo "Usage: make run SONAR_URL='<url>' SONAR_TOKEN='<token>' SONAR_PROJECT='<key>'"; \
		exit 1; \
	fi
	java -jar pdf-generator/target/sonar-report-1.0-jar-with-dependencies.jar \
		"$(SONAR_URL)" \
		"$(SONAR_TOKEN)" \
		"$(SONAR_PROJECT)"

# Run tests (if any)
test:
	./mvnw -f pdf-generator/pom.xml test

# Quick build without tests
quick:
	./mvnw -f pdf-generator/pom.xml package -DskipTests -T 1C
