# Update Quarkus Platform GitHub Action

Automatically checks [Quarkus Platform](https://github.com/quarkusio/quarkus-platform) members for newer releases on Maven Central and creates individual pull requests for each update.

Works like Dependabot, but for platform members defined in `platformConfig/members` in the platform `pom.xml`.

## How it works

The action runs once and processes all configured branches (default branch + maintenance branches). For each branch, and for each tracked member on that branch:

1. Queries Maven Central for the latest final release (alphas, betas, milestones, RCs, and snapshots are ignored)
2. Compares it to the current version in the platform `pom.xml`
3. If a newer version is available (and matches the configured update policy), creates a branch, updates the version property, runs `./mvnw -Dsync`, and opens a pull request

Each branch and member is processed independently: if one fails, the others are still processed.

## Usage

### 1. Create a GitHub App

A GitHub App token is required (instead of `GITHUB_TOKEN`) so that PRs created by the action trigger CI workflows.

1. Go to your organization's **Settings > Developer settings > GitHub Apps > New GitHub App**
2. Fill in:
   - **Name**: e.g. `Quarkus Platform Updater`
   - **Homepage URL**: the repository URL
   - **Webhook**: uncheck "Active" (not needed)
3. Set **Repository permissions**:
   - **Contents**: Read & write (to push branches)
   - **Pull requests**: Read & write (to create PRs)
4. Click **Create GitHub App**
5. Note the **App ID** displayed on the app's settings page
6. Under **Private keys**, click **Generate a private key** — a `.pem` file will be downloaded

### 2. Configure repository secrets

In the target repository (e.g. `quarkusio/quarkus-platform`):

1. Go to **Settings > Secrets and variables > Actions**
2. Add a **secret**: `UPDATE_PLATFORM_APP_ID` with the App ID from step 1
3. Add a **secret**: `UPDATE_PLATFORM_APP_PRIVATE_KEY` with the contents of the `.pem` file

### 3. Install the GitHub App

1. Go to your GitHub App's settings page
2. Click **Install App** in the sidebar
3. Install it on the target repository (e.g. `quarkusio/quarkus-platform`)

### 4. Add the workflow

Add a workflow to your Quarkus Platform repository:

```yaml
name: Update Platform Members

on:
  schedule:
    - cron: '0 6 * * *'
  workflow_dispatch:

jobs:
  update:
    runs-on: ubuntu-latest
    steps:
      - name: Generate GitHub App token
        id: app-token
        uses: actions/create-github-app-token@v3
        with:
          app-id: ${{ secrets.UPDATE_PLATFORM_APP_ID }}
          private-key: ${{ secrets.UPDATE_PLATFORM_APP_PRIVATE_KEY }}

      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
          token: ${{ steps.app-token.outputs.token }}

      - name: Set up JDK 25
        uses: actions/setup-java@v5
        with:
          java-version: 25
          distribution: temurin
          cache: maven

      - uses: quarkusio/update-quarkus-platform-action@main
        with:
          github-token: ${{ steps.app-token.outputs.token }}
```

The action runs once and processes all configured branches (main + any branches defined in the config file). `fetch-depth: 0` is required so the action can check out maintenance branches. The app token is also passed to `actions/checkout` so that git push uses it.

## Configuration

Create a `.github/update-quarkus-platform.yml` file in your repository. Only members explicitly listed in this file are tracked.

### Basic configuration

```yaml
members:
  - name: Camel
  - name: CXF
  - name: AmazonServices
```

Member names must match the `<name>` element in `platformConfig/members` in the platform `pom.xml`.

### Update policies

You can control which versions are accepted using update policies.

| Policy | Meaning | Example (current: `3.17.0`) |
|--------|---------|----------------------------|
| `any` (default) | Any newer final version | `3.17.1`, `3.18.0`, `4.0.0` |
| `minor` | Same major version only | `3.17.1`, `3.18.0` (not `4.0.0`) |
| `micro` | Same major.minor version only | `3.17.1` (not `3.18.0`) |

#### Default policy for all members

```yaml
default-update-policy: micro
members:
  - name: Camel
  - name: CXF
```

Both Camel and CXF will only get micro/patch updates.

#### Per-member override

```yaml
default-update-policy: micro
members:
  - name: Camel
  - name: CXF
    update-policy: minor
```

Camel inherits `default-update-policy` (`micro`), CXF overrides it with `minor`.

### Maintenance branches

The configuration file is always read from the default branch (e.g. `main`). Branch-specific configs are defined using the `branches` map:

```yaml
# Config for main
members:
  - name: Camel
  - name: CXF

# Config for maintenance branches
branches:
  latest:
    default-update-policy: micro
    members:
      - name: Camel
      - name: CXF
  3.32:
    default-update-policy: micro
    members:
      - name: Camel
```

#### How branches are resolved

The action processes the following branches in order:

1. **Default branch** (e.g. `main`): uses the root `members` and `default-update-policy` (skipped if no root members)
2. **Each entry in `branches`**: checked out and processed with its own config
3. **`latest` pseudo-branch**: resolved to the newest `X.Y`-formatted branch in the repository (see below)

If both an exact branch entry and `latest` resolve to the same branch, the exact entry takes priority.

#### The `latest` pseudo-branch

`latest` automatically resolves to the newest branch matching the `X.Y` format (e.g. if branches `3.31`, `3.32`, `3.33` exist, `latest` resolves to `3.33`). This is useful so you don't have to update the config file every time a new maintenance branch is created.

## Pull requests

- **Branch naming**: `update-automation/{base-branch}-{member-name}-{version}` (e.g. `update-automation/main-camel-3.18.0`)
- **Deduplication**: if a PR already exists for a given branch name, the action skips that member
- **Commit author**: `github-actions[bot]`

## Development

Built with [Quarkus GitHub Action](https://github.com/quarkiverse/quarkus-github-action) and distributed as an uber-jar via GitHub Packages Maven repository, executed at runtime via JBang.

```bash
# Build and run tests
./mvnw -B clean verify
```
