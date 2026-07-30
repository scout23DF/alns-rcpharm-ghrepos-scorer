# GitHub Repository Popularity Scorer - CLI Utility (`ghreposscorer-util-cli`)

Command-line utility (CLI) for querying and displaying GitHub repository popularity rankings built with **Quarkus PicoCLI** and Hexagonal Architecture.

---

## 🚀 How to Run the CLI Application

### Method 1: Running via Executable JAR (Standard JVM)

#### 1. Build the CLI package:
```bash
mvn clean package -pl ghreposscorer-util-cli -am
```

#### 2. Execute the CLI tool:

- **Display Help & Available Options**:
  ```bash
  java -jar ghreposscorer-util-cli/target/quarkus-app/quarkus-run.jar --help
  ```

- **Query Popular Repositories for a Specific Language**:
  ```bash
  java -jar ghreposscorer-util-cli/target/quarkus-app/quarkus-run.jar -l Kotlin -n 5
  ```

- **Custom Date Filter and Limit**:
  ```bash
  java -jar ghreposscorer-util-cli/target/quarkus-app/quarkus-run.jar --language Java --created-after 2015-01-01 --limit 10
  ```

---

### Method 2: Native Executable Compilation (GraalVM 25)

Building a GraalVM native binary produces a standalone executable file with **instant startup (~10ms)**, low memory usage, and no JVM requirement at runtime.

#### Option A: Building with local GraalVM installed
```bash
mvn package -Pnative -pl ghreposscorer-util-cli -am
```

#### Option B: Building via Docker Container (No local GraalVM required)
```bash
mvn package -Pnative -Dquarkus.native.container-build=true -pl ghreposscorer-util-cli -am
```

#### Execute Native Executable Binary:

- **Run Help**:
  ```bash
  ./ghreposscorer-util-cli/target/ghreposscorer-util-cli-runner --help
  ```

- **Run Ranking Query**:
  ```bash
  ./ghreposscorer-util-cli/target/ghreposscorer-util-cli-runner -l Go -n 5
  ```

---

### Method 3: Running via Quarkus Dev Mode

```bash
mvn quarkus:dev -pl ghreposscorer-util-cli -Dquarkus.args="-l Python -n 5"
```

---

## 📋 Available Options

| Short Flag | Long Flag | Default | Description |
|---|---|---|---|
| `-l` | `--language` | `Java` | Programming language to query (e.g. `Java`, `Kotlin`, `Python`, `Go`) |
| `-d` | `--created-after` | `2010-01-01` | Filter repositories created after date (`YYYY-MM-DD`) |
| `-n` | `--limit` | `10` | Maximum number of ranked repositories to display |
| `-h` | `--help` | — | Display help message and exit |
| `-V` | `--version` | — | Print version information and exit |

---

## 📊 Example Console Output

```text
Fetching popular repositories for language 'Kotlin' created after 2010-01-01 (Limit: 5)...

==========================================================================================================
 RANK | SCORE        | STARS      | FORKS      | LAST PUSHED  | REPOSITORY NAME                         
----------------------------------------------------------------------------------------------------------
 1    | 83767.21     | 61944      | 18120      | 2026-07-29   | topjohnwu/Magisk                        
 2    | 69804.20     | 60321      | 7836       | 2026-07-29   | 2dust/v2rayNG                           
 3    | 60938.20     | 53201      | 6381       | 2026-07-30   | JetBrains/kotlin                        
 4    | 60083.83     | 45771      | 11862      | 2026-07-27   | android/architecture-samples            
 5    | 59247.74     | 49345      | 8201       | 2026-06-30   | bannedbook/fanqiang                     
==========================================================================================================
```
