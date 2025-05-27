# Employee Management Portal

This project is a simple Spring Boot application that serves as an Employee Management Portal.  This project provides a variety of self-service actions for its users.

## Project Structure

```
employee-management-service
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── example
│   │   │           ├── EmployeeManagementPortal.java
│   │   │           ├── controller
│   │   │           │   └── EmployeeController.java
│   │   │           ├── service
│   │   │           │   └── EmployeeService.java
│   │   │           └── repository
│   │   │               └── UserRepository.java
│   │   └── resources
│   │       ├── application.properties
│   │       └── templates
│   │           └── user.html
│   └── test
│       └── java
│           └── com
│               └── example
│                   └── EmployeeManagementPortalTests.java
├── pom.xml
└── README.md
```

## Features Demonstrated

1. **Database Queries**: 
   - The application demonstrates how to construct and execute SQL queries with user input.

2. **HTML Content Rendering**: 
   - User input is rendered in web pages to show how data can be displayed dynamically.

3. **External Resource Fetching**: 
   - The application allows users to input a URL, which is then fetched by the server to demonstrate API integration capabilities.

## Getting Started

### Prerequisites

- Java 11 or higher
- Maven

### Running the Application

1. Clone the repository:
   ```
   git clone <repository-url>
   cd employee-management-service
   ```

2. Build the application:
   ```
   mvn clean install
   ```

3. Run the application:
   ```
   mvn spring-boot:run
   ```

   To run on a different port (e.g., 9090 instead of the default port):
   ```
   mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9090
   ```
   
   Alternatively, you can use an environment variable:
   ```
   SERVER_PORT=9090 mvn spring-boot:run
   ```
   
   **Troubleshooting port conflicts:**
   
   If you see an error like "Port X is already in use", you can:
   
   - Find and stop the process using the port:
     ```
     lsof -i :PORT_NUMBER
     kill PROCESS_ID
     ```
   - Or modify the `server.port` property in `application.properties` to use a different port

4. Access the application at `http://localhost:9090` (or whichever port you configured).

### Using the Application

1. **Home Page**: Go to `http://localhost:9090` to access the main user interface with all features
2. **H2 Database Console**: Access `http://localhost:9090/h2-console` to inspect the in-memory database
   - JDBC URL: `jdbc:h2:mem:testdb`
   - Username: `sa`
   - Password: (leave empty)

### Testing Individual API Endpoints

- **Database Queries**: `http://localhost:9090/api/user-search?username=yourQueryHere`
- **HTML Rendering**: `http://localhost:9090/api/render-content?userInput=yourTextHere`
- **URL Fetching**: `http://localhost:9090/api/fetch-url?url=http://example.com`
