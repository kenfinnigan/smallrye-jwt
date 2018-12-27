package io.smallrye.jwt.auth.principal;


import java.util.List;

import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;

/**
 * A default implementation of the abstract JWTCallerPrincipalFactory that uses the Keycloak token parsing classes.
 */
public class DefaultJWTCallerPrincipalFactory extends JWTCallerPrincipalFactory {

    /**
     * Tries to load the JWTAuthContextInfo from CDI if the class level authContextInfo has not been set.
     */
    public DefaultJWTCallerPrincipalFactory() {
    }

    @Override
    public JWTCallerPrincipal parse(final String token, final JWTAuthContextInfo authContextInfo) throws ParseException {
        JWTCallerPrincipal principal;

        try {
            JwtConsumerBuilder builder = new JwtConsumerBuilder()
                    .setRequireExpirationTime()
                    .setRequireSubject()
                    .setSkipDefaultAudienceValidation()
                    .setJwsAlgorithmConstraints(
                            new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST,
                                                     AlgorithmIdentifiers.RSA_USING_SHA256));

            if (authContextInfo.isRequireIssuer()) {
                builder.setExpectedIssuer(true, authContextInfo.getIssuedBy());
            } else {
                builder.setExpectedIssuer(false, null);
            }
            if (authContextInfo.getSignerKey() != null) {
                builder.setVerificationKey(authContextInfo.getSignerKey());
            } else if (authContextInfo.isFollowMpJwt11Rules()) {
                builder.setVerificationKeyResolver(new KeyLocationResolver(authContextInfo.getJwksUri()));
            } else {
                final List<JsonWebKey> jsonWebKeys = authContextInfo.loadJsonWebKeys();
                builder.setVerificationKeyResolver(new JwksVerificationKeyResolver(jsonWebKeys));
            }

            if (authContextInfo.getExpGracePeriodSecs() > 0) {
                builder.setAllowedClockSkewInSeconds(authContextInfo.getExpGracePeriodSecs());
            } else {
                builder.setEvaluationTime(NumericDate.fromSeconds(0));
            }

            JwtConsumer jwtConsumer = builder.build();
            JwtContext jwtContext = jwtConsumer.process(token);
            String type = jwtContext.getJoseObjects().get(0).getHeader("typ");
            //  Validate the JWT and process it to the Claims
            jwtConsumer.processContext(jwtContext);
            JwtClaims claimsSet = jwtContext.getJwtClaims();

            principal = new DefaultJWTCallerPrincipal(token, type, claimsSet);
        } catch (InvalidJwtException e) {
            throw new ParseException("Failed to verify token", e);
        }

        return principal;
    }
}
