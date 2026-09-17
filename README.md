# yssmRecipe

配方原料管理系統，用於內部單人或少數人使用情境。系統以瀏覽器操作，後端負責正式商業計算與資料保存，Excel/CSV 作為匯入與匯出介面。

## 功能範圍

- 商品、原料、配方、配方版本與版本明細維護
- 配方試算
- 商品庫存匯入
- 原料盤點匯入
- 生產計畫產生與匯出
- 請購分析產生、來源追溯與匯出 Excel

目前不做登入權限、Audit Log、匯入 Preview / Confirm、完整採購單、供應商、到貨、MOQ、成本、多公司與多工廠。

## 技術架構

- Backend: Java 21, Spring Boot, Maven, JPA, Flyway, MySQL, Redis, Elasticsearch
- Frontend: React, TypeScript, Vite, Nginx
- Deployment: Docker Desktop / Docker Compose
- Container Registry: Docker Hub, GitHub Container Registry

## 本機開發

### 後端

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

後端預設連線：

- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- Elasticsearch: `http://localhost:9200`
- API: `http://localhost:8080`

### 前端

```powershell
cd frontend
npm ci
npm run build
npm run dev
```

前端開發伺服器會將 `/api` 代理到 `http://localhost:8080`。

## Docker Compose 部署

正式交付客戶端時，建議使用 Docker Desktop 搭配 `docker-compose.deploy.yml`。

客戶端需要準備：

- Windows 電腦
- Docker Desktop
- 可連線 Docker Hub 的網路
- `.env` 設定檔
- `docker-compose.deploy.yml`

部署步驟：

```powershell
docker compose -f docker-compose.deploy.yml --env-file .env pull
docker compose -f docker-compose.deploy.yml --env-file .env up -d
```

若客戶端使用 Docker Hub image，可先複製 `.env.deploy.dockerhub.example` 為 `.env`，並把 `your-dockerhub-namespace` 改成實際 Docker Hub 帳號或 organization 名稱。

預設服務：

- 前端系統: `http://localhost:3000`
- 後端 API: `http://localhost:18080`
- MySQL: `localhost:3307`

## Docker Image 自動建置

GitHub Actions 會在 push 到 `main` 或 `master` 時：

1. 跑後端測試
2. 跑前端 build
3. 建置 backend Docker image
4. 建置 frontend Docker image
5. 推送到 Docker Hub 與 GitHub Container Registry

GitHub Container Registry image：

- `ghcr.io/liujiawei-tw/yssmrecipe/backend:latest`
- `ghcr.io/liujiawei-tw/yssmrecipe/frontend:latest`

Docker Hub image 會使用以下命名規則：

- `<dockerhub-namespace>/yssmrecipe-backend:latest`
- `<dockerhub-namespace>/yssmrecipe-frontend:latest`
- `<dockerhub-namespace>/yssmrecipe-backend:vX.Y.Z`
- `<dockerhub-namespace>/yssmrecipe-frontend:vX.Y.Z`

Docker Hub 發布需要在 GitHub repository 的 `Settings` -> `Secrets and variables` -> `Actions` 設定：

- Secret `DOCKERHUB_USERNAME`: Docker Hub 帳號
- Secret `DOCKERHUB_TOKEN`: Docker Hub personal access token，需可 push image
- Variable `DOCKERHUB_NAMESPACE`: 選填；若 image 要推到 organization，填 organization 名稱。未設定時使用 `DOCKERHUB_USERNAME`

Docker Hub 需要先存在以下 repositories，或確定帳號允許首次 push 建立 repository：

- `yssmrecipe-backend`
- `yssmrecipe-frontend`

如果使用 private repository 或 private package，客戶端部署前需要先登入對應 registry：

```powershell
docker login ghcr.io
docker login
```

## 版本控管原則

- 所有正式程式碼、設定範本、部署檔與需求文件都進 Git
- `.env`、資料庫資料、Docker volume、build output、IDE 設定、本機 Maven cache 不進 Git
- 每次交付以 Git commit 或 tag 對應一組 Docker image
- 客戶端部署時盡量使用固定版本 tag，避免長期只依賴 `latest`

建議發版流程：

```powershell
git checkout master
git pull
git tag vX.Y.Z
git push origin vX.Y.Z
```

## 客戶端資料需求

部署前至少需要確認：

- Docker Desktop 可正常啟動
- 客戶端電腦的前端連接埠，預設 `3000`
- 客戶端電腦的後端連接埠，預設 `18080`
- 客戶端電腦的 MySQL 對外連接埠，預設 `3307`
- MySQL 帳號與密碼
- GitHub Container Registry 是否需要登入
- 備份資料要存放在哪個磁碟或資料夾
- 真實 ERP 商品庫存欄位
- 真實原料盤點欄位
- 舊配方 Excel 欄位與排列方式
