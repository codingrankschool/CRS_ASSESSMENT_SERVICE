# CRS Assessment, Contest, Assignment & Quiz Engine (Java 21 + Spring Boot 3.3.4)

Enterprise microservice powering timed competitive programming contests, automated code assignments, multi-choice quizzes, anti-cheat proctoring, and real-time live leaderboards for **CodingRank School**.

Directly connected to **MongoDB Atlas (`codingrankschool`)** as the single source of truth for users, questions, test cases, and submissions.

---

## 1. Why Direct MongoDB Atlas + Spring Boot is the Best Choice

* **Single Source of Truth**: No database synchronization lag, dual-writes, or schema drift between the LMS and Assessment Engine.
* **Shared Authentication & Passwords**: Uses Spring Security's `BCryptPasswordEncoder` to authenticate against the existing `User` collection's `$2b$10$...` hashes without requiring password resets or user migration.
* **Unified Submissions**: Saves student solutions directly to MongoDB Atlas `Submission` and `CheatEvent` collections, so LMS student dashboards and progress tracking update immediately.
* **CPU & I/O Isolation**: Java 21 Virtual Threads (Project Loom) handle CPU-heavy compilation, code sandboxing, and WebSocket broadcasting without impacting the main LMS web servers.

---

## 2. MongoDB Atlas Collections Mapped

| Collection | Spring Document | Purpose |
| :--- | :--- | :--- |
| `User` | `com.codingrank.assessment.model.User` | BCrypt password authentication, JWT token claims |
| `Question` | `com.codingrank.assessment.model.Question` | Problem descriptions, difficulty, constraints, signatures |
| `TestCase` | `com.codingrank.assessment.model.TestCase` | Visible and hidden test cases for automated grading |
| `Quiz` | `com.codingrank.assessment.model.Quiz` | Multi-choice questions, options, and answer keys |
| `Contest` | `com.codingrank.assessment.model.Contest` | Timed competitive programming contests and problem lists |
| `Assignment` | `com.codingrank.assessment.model.Assignment` | Graded programming assignments, deadlines, point totals |
| `Submission` | `com.codingrank.assessment.model.Submission` | Execution verdicts (`PASSED`, `FAILED`), scores, runtimes |
| `CheatEvent` | `com.codingrank.assessment.model.CheatEvent` | Anti-cheat audit logs (tab-switches, copy/paste, fullscreen exit) |
| `CurriculumItem` | `com.codingrank.assessment.model.CurriculumItem` | LMS hierarchy linking courses and units to assessments |
| `LeaderboardEntry` | `com.codingrank.assessment.model.LeaderboardEntry` | ICPC penalty times, ranks, and live contest scores |

---

## 3. End-to-End Authentication Flow

### 1. Login with Existing MongoDB Atlas Credentials
* **Endpoint**: `POST /api/v1/auth/login`
* **Request**:
  ```json
  {
    "email": "manishparihar1441@gmail.com",
    "password": "yourPasswordHere"
  }
  ```
* **Process**:
  1. Searches `User` collection by email in Atlas.
  2. Verifies raw password against the `$2b$10$...` BCrypt hash.
  3. Verifies `accountStatus == ACTIVE`.
  4. Generates a signed JWT Bearer token valid for 7 days.
* **Response**:
  ```json
  {
    "success": true,
    "message": "Login successful",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiJ9...",
      "tokenType": "Bearer",
      "expiresInMs": 604800000,
      "user": {
        "id": "6a409c1ac739179b4607c22d",
        "email": "manishparihar1441@gmail.com",
        "name": "Manish Parihar",
        "role": "STUDENT",
        "accountStatus": "ACTIVE",
        "verificationStatus": "APPROVED",
        "phoneNumber": "7060725853"
      }
    }
  }
  ```

### 2. Accessing Protected Endpoints
Send the token in the `Authorization` header:
```http
Authorization: Bearer <your-jwt-token>
```

### 3. Get Authenticated User Profile
* **Endpoint**: `GET /api/v1/auth/me`
* **Response**: Returns current user profile decoded from the session.

---

## 4. Assessment, Contest & Quiz Endpoints

### Submissions & Code Execution
* `POST /api/v1/submissions/code` — Evaluates code against Atlas `TestCase` collection (Java, Python, C++, Node.js).
* `POST /api/v1/submissions/quiz` — Auto-grades quiz answers against Atlas `Quiz` answer key.
* `POST /api/v1/submissions/cheat-event` — Logs anti-cheat infractions (tab switch, window blur, fullscreen exit).

### Contests & Live Leaderboards
* `GET /api/v1/contests` — List active contests from Atlas.
* `GET /api/v1/contests/{id}` — Get contest details and problems.
* `GET /api/v1/contests/{id}/leaderboard` — Get full leaderboard with ICPC penalty minutes.
* `WebSocket /ws-contest` (Topic: `/topic/contest/{id}/leaderboard`) — Live updates pushed to connected clients.

### Quizzes & Assignments
* `GET /api/v1/assessments/quiz/{itemId}` — Get quiz questions and time limit.
* `GET /api/v1/assessments/assignment/{itemId}` — Get assignment details and problem statement.
* `GET /api/v1/assessments/contest/{itemId}` — Get contest problems and proctoring config.

---

## 5. How to Run from IntelliJ IDEA (Step-by-Step)

1. **Open the Project in IntelliJ**:
   - `File` → `Open...` → Select `/Users/mac/Documents/Product Development/Codingrank School/CRS_ASSESSMENT_SERVICE`.
2. **Set Project SDK**:
   - `File` → `Project Structure...` → `Project` → Set SDK to **Java 21**.
3. **Run the Application**:
   - Navigate to `src/main/java/com/codingrank/assessment/AssessmentApplication.java`.
   - Right-click `AssessmentApplication` → Click **Run 'AssessmentApplication.main()'** (or press `^R`).
4. **Environment Variables (Optional / Defaults)**:
   - By default, `application.yml` already includes your MongoDB Atlas connection string:
     `mongodb+srv://anandsinghyadav_codingrank:Anand%40codingrankschool@codingrank.stidlhy.mongodb.net/codingrankschool?appName=codingrank`
   - To override via IntelliJ Run Configuration:
     `Edit Configurations` → `Environment variables` → Add `DATABASE_URL=...` and `PORT=8085`.
5. **Verify Running State**:
   - Open browser or Postman at: `http://localhost:8085/api/v1/assessments`
   - Test login at: `POST http://localhost:8085/api/v1/auth/login`
