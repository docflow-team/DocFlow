package at.fhtw.docflow.identity.api;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    @Override
    public String toString() { return "TokenResponse[redacted]"; }
}
