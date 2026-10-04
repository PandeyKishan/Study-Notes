# CI/CD: Production Pipeline Templates & Workflows

> **Cluster 04 — Module 06**  
> Focus: Copy-pasteable, production-ready GitHub Actions templates with Buildx caching, Trivy security gates, and GitOps triggers.

---

## Template 1: End-to-End Enterprise CI/CD Pipeline

Save this file as `.github/workflows/production-pipeline.yml`:

```yaml
name: Enterprise CI/CD Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

# Cancel stale runs on new pushes
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  # ====================================================
  # Job 1: Quality, Linting & Unit Testing
  # ====================================================
  validate-and-test:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Setup Node.js Runtime
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: 'npm'

      - name: Install Dependencies
        run: npm ci

      - name: Run Linters & Code Style Checks
        run: npm run lint --if-present

      - name: Run Automated Test Suites with Coverage
        run: npm test -- --coverage

      - name: Upload Test Coverage Artifact
        uses: actions/upload-artifact@v4
        with:
          name: coverage-report
          path: coverage/
          retention-days: 7

  # ====================================================
  # Job 2: Secret Scanning Gate
  # ====================================================
  secret-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Run Gitleaks Secret Scanner
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  # ====================================================
  # Job 3: Docker Build, Trivy Security Scan & Registry Push
  # ====================================================
  build-and-scan-image:
    needs: [validate-and-test, secret-scan]
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      security-events: write
    outputs:
      image_tag: ${{ steps.meta.outputs.version }}
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up QEMU (Multi-platform support)
        uses: docker/setup-qemu-action@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GitHub Container Registry (GHCR)
        if: github.event_name != 'pull_request'
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract Docker Metadata & Tags
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,format=short
            type=ref,event=branch
            latest

      # Build image locally first for scanning
      - name: Build Docker Image for Security Scan
        uses: docker/build-push-action@v5
        with:
          context: .
          load: true
          tags: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:test-scan
          cache-from: type=gha
          cache-to: type=gha,mode=max

      # Trivy Vulnerability Gate
      - name: Run Trivy Vulnerability Scanner
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:test-scan
          format: 'sarif'
          output: 'trivy-results.sarif'
          severity: 'CRITICAL,HIGH'
          exit-code: '0' # Set to '1' in strict security environments to block deploy

      - name: Upload Trivy SARIF results to GitHub Security
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: 'trivy-results.sarif'

      # Push scanned image to registry
      - name: Build and Push Final Docker Image
        if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha

  # ====================================================
  # Job 4: GitOps Trigger / Kubernetes Deployment
  # ====================================================
  deploy-production:
    needs: [build-and-scan-image]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment:
      name: production
      url: https://api.mycompany.com
    steps:
      - name: Checkout Manifests Repo
        uses: actions/checkout@v4

      - name: Update Deployment Image Tag (GitOps Strategy)
        run: |
          echo "Updating Kubernetes manifests to use image tag: ${{ github.sha }}"
          # In true GitOps, update the GitOps deployment repo via git commit
          # Or apply directly using kubectl:
          # kubectl set image deployment/order-service order-service=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
```
