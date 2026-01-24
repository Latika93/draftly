# Draftly - AI-Powered Email Draft Assistant

Draftly is a Spring Boot application that helps users generate intelligent email replies using AI. The application integrates with Gmail API to fetch emails and OpenAI API to generate context-aware email drafts that match the user's writing style.

## Overview

This application provides a comprehensive email management system where users can:
- Authenticate using JWT or OAuth2 (Google)
- Fetch inbox emails from Gmail
- Generate AI-powered email reply drafts that mimic their writing style
- Approve, reject, or regenerate email drafts
- Send approved drafts directly to Gmail

## Architecture & Approach

### Technology Stack
- **Backend Framework**: Spring Boot 4.0.1
- **Database**: MySQL (with JPA/Hibernate)
- **Security**: Spring Security with JWT authentication and OAuth2
- **AI Integration**: OpenAI GPT API
- **Email Integration**: Gmail API
- **Build Tool**: Maven

### Core Components

#### 1. Authentication System
The application supports dual authentication mechanisms:

**JWT Authentication:**
- Users can sign up and log in with email/password (not using in integration)
- JWT tokens are generated for authenticated sessions
- Access tokens expire in 10 minutes, refresh tokens last 6 months
- JWT filter intercepts requests and validates tokens

**OAuth2 Authentication:**
- Users can authenticate via Google OAuth2
- On successful OAuth login, the application:
  - Creates or retrieves user account
  - Stores Google access token for Gmail API calls
  - Generates application JWT token
  - Redirects to frontend with tokens

#### 2. Email Draft Generation
The core functionality revolves around generating intelligent email replies:

**Process Flow:**
1. User selects an email from inbox
2. System fetches user's past 10 sent emails to analyze writing style
3. AI analyzes the incoming email (subject, body, sender)
4. OpenAI generates a reply that:
   - Matches user's writing style and structure
   - Maintains vocabulary patterns and formatting
   - Adjusts tone based on user preference (Formal, Friendly, Concise)
   - Avoids copying content from original email
5. Draft is created in Gmail and saved to database
6. User can approve, reject, or regenerate the draft

**Key Features:**
- **Style Mimicking**: Analyzes past emails to maintain consistent writing style
- **Tone Control**: Users can select tone (Formal, Friendly, Concise)
- **No-Reply Detection**: Automatically skips no-reply emails
- **Draft Management**: Full CRUD operations on drafts
- **Retry Logic**: Automatic retry with exponential backoff for email sending

#### 3. Database Design
The application uses MySQL to persist:
- **User Information**: Email, password, OAuth tokens, roles
- **Email Reply Drafts**: Thread ID, message ID, draft content, status, Gmail draft ID
- **Draft Status Tracking**: GENERATED, SENT, REJECTED states

#### 4. API Integration

**Gmail API Integration:**
- Fetches inbox emails (last 50)
- Retrieves sent emails for style analysis
- Creates and updates Gmail drafts
- Sends approved emails
- Handles Gmail API errors with proper exception handling

**OpenAI API Integration:**
- Uses GPT-4.1-mini model for email generation
- Constructs system and user prompts for context-aware generation
- Handles API errors gracefully

#### 5. Logging & Monitoring
- Logging for all draft actions
- Tracks AI generation lifecycle
- Logs API requests and responses
- Error tracking with context

## API Endpoints

### Authentication
- `POST /auth/signup` - User registration
- `POST /auth/login` - User login (returns JWT tokens)
- `POST /auth/refresh` - Refresh access token

### Email Management
- `GET /emails/inbox` - Fetch last 50 inbox emails
- `GET /emails` - Fetch last 10 sent email bodies
- `GET /emails/thread/body` - Get email body by thread ID

### Draft Operations
- `POST /emails/draft` - Generate new email draft
- `POST /emails/draft/reply` - Generate reply draft for an email
- `POST /emails/draft/reply/regenerate` - Regenerate existing draft with optional tone change
- `POST /emails/draft/reply/approve` - Approve and send draft
- `POST /emails/draft/reply/reject` - Reject and delete draft
- `POST /emails/thread/reject` - Reject thread (alias for reject)

## Configuration

### Application Properties
The application requires configuration in `application.properties`:
- Database connection (MySQL)
- JWT secret key
- OpenAI API key and model
- OAuth2 client credentials (Google)

### Security Configuration
- CORS enabled for frontend (localhost:5173)
- JWT filter for token validation
- OAuth2 login flow configured
- Public endpoints: `/auth/**`, `/oauth2/**`

## Key Design Decisions

1. **Dual Authentication**: Supports both traditional JWT and OAuth2 to provide flexibility
2. **Style Analysis**: Uses past sent emails to maintain user's unique writing voice
3. **Draft Persistence**: Saves drafts to database for tracking and management
4. **Status Management**: Tracks draft lifecycle (GENERATED → SENT/REJECTED)
5. **Error Handling**: Comprehensive exception handling with retry logic for transient failures
6. **Separation of Concerns**: Clear separation between services, controllers, and integrations

## Database Schema

### User Table
- Stores user credentials and OAuth tokens
- Supports role-based access (USER, CREATOR, ADMIN)

### Email Reply Drafts Table
- Tracks all generated drafts
- Links to Gmail drafts via `gmailDraftId`
- Maintains status and timestamps
- Soft delete support

## Security Features

- Password encryption using BCrypt
- JWT token-based authentication
- OAuth2 integration for Google
- Secure token storage
- CORS configuration
- Request authentication via filter chain

## Error Handling

- Global exception handler for consistent error responses
- Custom exceptions for Gmail API errors
- Resource not found handling
- Validation error responses
- Retry logic for transient failures

## Future Enhancements

Potential improvements:
- Email templates
- Scheduled email sending
- Multi-language support
- Advanced tone customization
- Email analytics
- Bulk operations

## Development

### Prerequisites
- Java 17
- Maven
- MySQL database
- OpenAI API key
- Google OAuth2 credentials

### Running the Application
1. Configure database and API keys in `application.properties`
2. Run `mvn spring-boot:run`
3. Application starts on default port 8080

### Testing
The application includes comprehensive logging for debugging and monitoring API interactions.
