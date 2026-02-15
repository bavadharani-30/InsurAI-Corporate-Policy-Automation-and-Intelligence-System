# InsurAI-Corporate-Policy-Automation-and-Intelligence-System
📌 Project Overview:

InsurAI is a full-stack enterprise insurance management platform designed to automate corporate policy administration and enhance decision-making through structured intelligence systems.

The platform enables administrators, agents, and customers to manage policies, consultations, and claims efficiently through secure role-based dashboards and real-time operational insights.

This system focuses on automation, scalability, and structured policy lifecycle management.

🎯 Project Objectives:

Automate corporate policy management workflows

Implement secure role-based authentication

Provide intelligent operational dashboards

Enable structured consultation and booking systems

Develop a scalable full-stack architecture

🚀 Core Features:
🔐 Authentication & Access Control

JWT-based secure login

Role-based access (Admin / Agent / Customer)

Protected API endpoints

🏢 Corporate Policy Management

Policy creation and management

Policy lifecycle tracking

Coverage details monitoring

Status updates and workflow automation

👨‍💼 Agent Module

Consultation scheduling

Decision support interface

Performance tracking dashboard

Request queue management

👤 Customer Module

View available policies

Book consultations

Track active policies

Submit requests

📊 Administrative Dashboard

Total users monitoring

Policy issuance tracking

Agent performance metrics

System health overview

🛠 Technology Stack
Backend:

Java 17

Spring Boot

Spring Security (JWT)

Spring Data JPA

MySQL

Maven

Frontend:

React.js

Axios

Context API

Recharts

Custom CSS Theming

🏗 System Architecture:

The application follows a layered architecture model:

Controller Layer – Handles client requests

Service Layer – Business logic implementation

Repository Layer – Database operations

Security Layer – Authentication & authorization

DTO Layer – Structured data transfer

This modular architecture ensures maintainability and scalability.

🗄 Database Setup

Create a MySQL database:

CREATE DATABASE insurai_db;


Update the database configuration in:

src/main/resources/application.properties


Example configuration:

spring.datasource.url=jdbc:mysql://localhost:3306/insurai_db
spring.datasource.username=root
spring.datasource.password=your_password

▶️ Running the Application
🔹 Backend

Open insurai-backend in IntelliJ

Ensure MySQL server is running

Run:

InsuraiBackendApplication.java


Backend runs at:

http://localhost:8080

🔹 Frontend

Navigate to:

insurai-frontend


Run:

npm install
npm start


Frontend runs at:

http://localhost:3000

🔐 Security Implementation

JWT token-based authentication

Role-based route authorization

Encrypted password handling

RESTful API structure:

📈 Future Enhancements

Predictive policy recommendation engine

Automated claim approval workflows

Email and SMS notification integration

Cloud deployment (AWS / Azure)

CI/CD integration

🎓 Academic Scope:

This project demonstrates:

Full-stack application development

Secure backend API implementation

Frontend dashboard design

Database integration and management

Enterprise-level system structuring
