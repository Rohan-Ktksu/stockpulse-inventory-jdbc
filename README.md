# StockPulse — Java Full Stack Inventory Management System (JDBC + MySQL)

A complete **Core Java + JDBC + MySQL** full-stack web application built **without Maven**, featuring user registration/login, a live KPI dashboard, and full CRUD operations backed by MySQL.

## Tech Stack
- **Frontend**: HTML5, CSS3, Vanilla JavaScript (`web/index.html`, `web/styles.css`, `web/app.js`)
- **Backend**: Core Java (`com.sun.net.httpserver.HttpServer`, `java.sql.*`, `java.security.MessageDigest`) — no Spring, no Servlets, no Maven
- **Database**: MySQL 8.x+ connected via **JDBC** (`DriverManager`, `Connection`, `Statement`, `PreparedStatement`, `ResultSet`)

## Project Structure
```text
inventory-management-jdbc/
├── config/
│   └── db.properties                  # MySQL URL, user, password, HTTP port
├── lib/
│   └── mysql-connector-j-*.jar        # Place MySQL Connector/J JAR here
├── sql/
│   └── schema.sql                     # MySQL database, tables, and sample data
├── src/com/stockpulse/
│   ├── Main.java                      # Initializes MySQL schema & starts HTTP server
│   ├── config/DBConnection.java       # DriverManager, Connection, Statement, ResultSet
│   ├── model/User.java                # User entity
│   ├── model/Product.java             # Product entity
│   ├── dao/UserDAO.java               # User registration & login (PreparedStatement, ResultSet)
│   ├── dao/ProductDAO.java            # Full Product CRUD & Dashboard stats
│   ├── server/ApiHandler.java         # JSON REST API endpoints (/api/*)
│   ├── server/StaticFileHandler.java  # Serves web/ static frontend files
│   ├── server/SessionManager.java     # Bearer-token session management
│   └── util/
│       ├── JsonUtil.java              # Pure-Java JSON serializer & parser
│       └── PasswordUtil.java          # SHA-256 password hashing
├── web/
│   ├── index.html                     # Login/Register + Dashboard + CRUD UI
│   ├── styles.css                     # Responsive styling
│   └── app.js                         # Fetch API client & DOM state logic
├── compile.bat / compile.sh           # Compile without Maven
└── run.bat / run.sh                   # Run with lib/* classpath
```

## JDBC Concepts Demonstrated
- `DriverManager.getConnection(...)` in `DBConnection.java`
- `Connection` (with try-with-resources) in `DBConnection.java`, `UserDAO.java`, and `ProductDAO.java`
- `Statement` in `DBConnection.initializeDatabase()`, `DBConnection.testConnection()`, and `ProductDAO.getDashboardStats()`
- `PreparedStatement` (parameterized queries + `Statement.RETURN_GENERATED_KEYS`) in `UserDAO.java` and `ProductDAO.java`
- `ResultSet` for reading rows, generated keys, and aggregate SQL metrics

## Setup & Run (Without Maven)

1. **Download MySQL Connector/J**:
   Download the platform-independent ZIP from the official MySQL site (`mysql-connector-j-8.x.x.jar` or `9.x.x.jar`) and copy the `.jar` file into the `lib/` folder.
2. **Configure MySQL Credentials**:
   Edit `config/db.properties` and set `db.user` and `db.password` to match your local MySQL Server.
   *(Optional)* You can also run `sql/schema.sql` manually in MySQL Workbench or `mysql` CLI, though `Main.java` automatically creates `stockpulse_db`, `users`, `products`, and 5 sample products on startup if they don't exist.
3. **Compile & Run**:
   - **Windows**:
     ```bat
     compile.bat
     run.bat
     ```
   - **macOS / Linux**:
     ```bash
     ./compile.sh
     ./run.sh
     ```
4. **Open in Browser**:
   Visit `http://localhost:8080`, register a new user account, and manage your inventory!

## Deploying on Railway (Recommended — Built-in Managed MySQL)
Railway includes managed MySQL in the same project, so you don't need an external database:

1. Open [Railway](https://railway.com), create a **New Project → Deploy from GitHub Repo**, and select `Rohan-Ktksu/stockpulse-inventory-jdbc` (`railway.toml` and `Dockerfile` are detected automatically).
2. Inside the same Railway project, click **+ New → Database → Add MySQL**.
3. Open your Java web service **Variables** tab and add variable references from the MySQL service:
   - `MYSQLHOST=${{MySQL.MYSQLHOST}}`
   - `MYSQLPORT=${{MySQL.MYSQLPORT}}`
   - `MYSQLDATABASE=${{MySQL.MYSQLDATABASE}}`
   - `MYSQLUSER=${{MySQL.MYSQLUSER}}`
   - `MYSQLPASSWORD=${{MySQL.MYSQLPASSWORD}}`
4. Railway injects `PORT` and redeploys automatically. Generate a public domain under **Settings → Networking** to access the app!

## Deploying to GitHub & Render
This repo includes `Dockerfile`, `render.yaml`, and `.gitignore` so it deploys directly on Render without Maven:

1. **Push to GitHub**:
   ```bash
   git remote add origin https://github.com/<your-username>/stockpulse-inventory-jdbc.git
   git push -u origin main
   ```
   *(Or authenticate GitHub CLI with `gh auth login -h github.com` and run `gh repo create stockpulse-inventory-jdbc --public --source=. --push`.)*
2. **Provision a MySQL Database**:
   Render does not include a native managed MySQL add-on, so create a free cloud MySQL database (e.g., Aiven, Railway, or FreeSQLDatabase) and note your host, port, database name, username, and password.
3. **Deploy on Render**:
   In the Render dashboard, choose **New -> Blueprint** (or **New -> Web Service -> Docker**), select your GitHub repository, and set the environment variables:
   - `DB_HOST`
   - `DB_PORT` (default `3306`)
   - `DB_NAME` (default `stockpulse_db`)
   - `DB_USER`
   - `DB_PASSWORD`
   - `DB_SSL_MODE` (`REQUIRED` for most cloud MySQL providers, or leave blank/`PREFERRED`)
   Render automatically injects `PORT`, builds the Docker image (fetching `mysql-connector-j-8.4.0.jar` and compiling with `javac`), and starts `com.stockpulse.Main`.

## API Endpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/health` | Verifies live MySQL JDBC connectivity |
| `POST` | `/api/register` | Registers a new user in `users` table |
| `POST` | `/api/login` | Authenticates user and returns session token |
| `POST` | `/api/logout` | Invalidates session token |
| `GET` | `/api/me` | Returns authenticated user info |
| `GET` | `/api/dashboard` | Returns KPI totals & category breakdown |
| `GET` | `/api/products` | Lists/searches/filters products from MySQL |
| `POST` | `/api/products` | Inserts a new product into MySQL |
| `PUT` | `/api/products/{id}` | Updates an existing product in MySQL |
| `DELETE` | `/api/products/{id}` | Deletes a product from MySQL |
