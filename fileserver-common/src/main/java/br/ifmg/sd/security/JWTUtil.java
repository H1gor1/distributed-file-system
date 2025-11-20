package br.ifmg.sd.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

public class JWTUtil {

    private static final String SECRET_KEY =
        "4760829e4659f70f425ebf4a48dc515cd065545555c2e2255395b64bd36eab18";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(
        SECRET_KEY.getBytes(StandardCharsets.UTF_8)
    );

    private static final long EXPIRATION_TIME = 3600000;

    public static String generateToken(
        String username,
        String userEmail,
        String userId
    ) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_TIME);

        return Jwts.builder()
            .claims()
            .subject(userId)
            .add("email", userEmail)
            .add("username", username)
            .and()
            .issuedAt(now)
            .expiration(expiration)
            .signWith(KEY)
            .compact();
    }

    public static String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(KEY)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claims.getSubject();
    }

    public static boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public static long getExpirationTime(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(KEY)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claims.getExpiration().getTime();
    }
}
