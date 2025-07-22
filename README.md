# E-Commerce Pet Project

**Technologies**:  
- Java 21  
- Apache Maven  
- **Vespa** (for search and query capabilities)  
- **Docker** (for containerization)  
- **Homebrew** (for managing dependencies on macOS/Linux)

## 📦 Prerequisites

Make sure your system has the following installed:

### Java 21
```bash
java -version
# If not installed:
brew install openjdk@21
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.bash_profile
source ~/.bash_profile
```

### Maven
```bash
mvn -v
# If not installed:
brew install maven
```

### Docker
```bash
docker --version
# If not installed:
brew install --cask docker
open /Applications/Docker.app
```

### Vespa
```bash
vespa --version
# If not installed:
brew install vespa
```

---

## 🚀 Local Development

### 1. Clone the repository
```bash
git clone https://github.com/Follyt/e-commerce-pet-project.git
cd e-commerce-pet-project
```

### 2. Build the project with Maven
```bash
mvn clean package
```

### 3. Start Vespa in Docker
```bash
docker run -d --name vespa   -p 8080:8080 -p 19100:19100 vespaengine/vespa
```

### 4. Deploy Vespa schema
```bash
vespa deploy --wait 60
```

### 5. Feed the data to your schema
```bash
vespa feed data/products.json
```

> Ensure `services.xml` and schemas under `schemas/` are correctly configured.

> The application will be accessible at `http://localhost:8081` (example).

### 6. Validate it's working

- Send CURL request in terminal: curl -i "http://127.0.0.1:8080"
- Follow this link: http://127.0.0.1:8080

If u got code 200 and can see page by link - all good, if not - try again.

---


---

## 🛠 Useful Commands

| Action                  | Command |
|-------------------------|---------|
| Clean the build         | `mvn clean` |
| Build/package project   | `mvn package` |
| Run tests               | `mvn test` |
| Install Java 21         | `brew install openjdk@21` |
| Install Maven           | `brew install maven` |
| Install Docker          | `brew install --cask docker` |
| Install Vespa           | `brew install vespa` |

---

## ✅ Health Check

- Project builds successfully: `mvn clean package`
- Vespa is running: `docker logs vespa`
- App responds: `curl http://localhost:8081/health`
- Search queries work: `curl "http://localhost:8080/search/?query=test"`

---

## 📩 Feedback

Found a bug or want to contribute?  
Feel free to open an issue or pull request.  
Contact for urgent inquiries: roman.godulyan@gmail.com

---

## 📝 Quick Setup Summary

```bash
# Installation (macOS/Linux)
brew install openjdk@21 maven docker vespa
open /Applications/Docker.app

# Clone and build
git clone https://github.com/Follyt/e-commerce-pet-project.git
cd e-commerce-pet-project
mvn clean package

# Run Vespa
docker run -d --name vespa -p 8080:8080 -p 19100:19100 vespaengine/vespa
vespa deploy --wait 60

```
