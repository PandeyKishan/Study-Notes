# CI/CD: Production Pipeline Templates & Workflows (Pro-Level)

> **Cluster 04 — Module 06**  
> Focus: Copy-pasteable, production-ready GitHub Actions templates implementing OIDC, Buildx caching, Trivy security gates, and GitOps integration.

---

## Template 1: End-to-End Enterprise CI/CD Pipeline (OIDC + DevSecOps)

This template represents a mature, SLSA-aligned pipeline. It avoids static secrets, utilizes advanced caching, scans for vulnerabilities, and prepares a GitOps repository for deployment.

Save this file as `.github/workflows/production-pipeline.yml`:

```yaml
name: Enterprise CI/CD Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

# Cancel stale runs on new pushes to the same branch
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}
  GITOPS_REPO: my-org/infrastructure-manifests

jobs:
  # ====================================================
  # Job 1: Quality, Linting & Unit Testing
  # ====================================================
  validate-and-test:
    name: Code Quality & Testing
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Setup Node.js Runtime with Advanced Caching
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: 'npm'
          cache-dependency-path: '**/package-lock.json'

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
    name: Gitleaks Secret Detection
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0 # Required to scan full history
      - name: Run Gitleaks Secret Scanner
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  # ====================================================
  # Job 3: Docker Build, Trivy Security Scan & Registry Push
  # ====================================================
  build-and-scan-image:
    name: Container Build & Security Scan
    needs: [validate-and-test, secret-scan]
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      security-events: write # Required for SARIF upload
      id-token: write        # Required for OIDC/Cosign keyless signing
    outputs:
      image_tag: ${{ steps.meta.outputs.version }}
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up Docker Buildx (Layer Caching Support)
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
          exit-code: '1' # Block pipeline on critical vulnerabilities
          ignore-unfixed: true

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

      # Install Cosign and sign the image using Keyless OIDC
      - name: Install Cosign
        if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'
        uses: sigstore/cosign-installer@v3.4.0
        
      - name: Sign the Published Docker Image
        if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'
        run: cosign sign --yes ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}@${{ steps.meta.outputs.tags }}

  # ====================================================
  # Job 4: GitOps Trigger / Update Deployment Repo
  # ====================================================
  gitops-deployment-trigger:
    name: Trigger ArgoCD via GitOps
    needs: [build-and-scan-image]
    if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'
    runs-on: ubuntu-latest
    steps:
      - name: Checkout GitOps Infrastructure Repo
        uses: actions/checkout@v4
        with:
          repository: ${{ env.GITOPS_REPO }}
          token: ${{ secrets.GITOPS_PAT_TOKEN }} # Cross-repo PAT required

      - name: Update Kubernetes Manifests (Kustomize/Helm)
        run: |
          # Example using Kustomize to update the image tag
          cd k8s/overlays/production
          kustomize edit set image api-service=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:sha-${{ github.sha::7 }}
          
      - name: Commit and Push to GitOps Repo
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git commit -am "chore: deploy ${{ env.IMAGE_NAME }} release sha-${{ github.sha::7 }}"
          git push
```

---

## Key Pro-Level Features Implemented:
1. **Concurrency Controls:** Cancels redundant builds to save runner minutes.
2. **Buildx `type=gha` Caching:** Drastically speeds up Docker builds by utilizing GitHub's internal cache API for intermediate layers.
3. **Keyless Image Signing (Cosign):** Secures the supply chain without requiring static private keys.
4. **GitOps Trigger Separation:** The pipeline doesn't deploy directly using `kubectl` (Push). Instead, it strictly updates a configuration repo (Pull) and lets ArgoCD handle the actual state reconciliation.
