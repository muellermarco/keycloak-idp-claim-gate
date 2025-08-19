package com.example.keycloak.broker;

import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.JsonWebToken;

import java.lang.reflect.Method;
import java.util.Map;

public class ClaimGateAuthenticator extends AbstractIdpAuthenticator {

    private static final Logger LOG = Logger.getLogger(ClaimGateAuthenticator.class);

    static final String CFG_CLAIM_NAME = "claimName";
    static final String CFG_EXPECTED_VALUE = "expectedValue"; // optional
    static final String CFG_FAIL_MESSAGE = "failMessage";     // optional

    @Override
    protected void actionImpl(AuthenticationFlowContext context,
                              SerializedBrokeredIdentityContext serializedCtx,
                              BrokeredIdentityContext bic) {
        // no form action required
    }

    @Override
    protected void authenticateImpl(AuthenticationFlowContext context,
                                    SerializedBrokeredIdentityContext serializedCtx,
                                    BrokeredIdentityContext bic) {

        if (bic == null) {
            deny(context, "Brokered identity context not found.");
            return;
        }

        LOG.debug("=== ClaimGateAuthenticator started for IdP login ===");
        for (Map.Entry<String, Object> e : bic.getContextData().entrySet()) {
            Object val = e.getValue();
            LOG.debugf("ContextData: key=%s, valueClass=%s, value=%s",
                    e.getKey(),
                    (val != null ? val.getClass().getName() : "null"),
                    val);
        }

        // 1) Try VALIDATED_ID_TOKEN (often JsonWebToken: ID/Access token)
        Object tokenObj = bic.getContextData().get(OIDCIdentityProvider.VALIDATED_ID_TOKEN);
        Map<String, Object> claims = null;

        if (tokenObj instanceof JsonWebToken) {
            LOG.debugf("VALIDATED_ID_TOKEN found: %s", tokenObj.getClass());
            JsonWebToken jwt = (JsonWebToken) tokenObj;
            claims = jwt.getOtherClaims();
        } else {
            LOG.debug("No VALIDATED_ID_TOKEN found, trying USER_INFO...");
            // 2) Fallback: USER_INFO – reflectively access getOtherClaims() or treat as Map
            Object uiObj = bic.getContextData().get(OIDCIdentityProvider.USER_INFO);
            if (uiObj != null) {
                LOG.debugf("USER_INFO found: %s", uiObj.getClass());
                try {
                    Method m = uiObj.getClass().getMethod("getOtherClaims");
                    Object res = m.invoke(uiObj);
                    if (res instanceof Map) {
                        //noinspection unchecked
                        claims = (Map<String, Object>) res;
                    }
                } catch (Exception ex) {
                    LOG.debugf("USER_INFO has no getOtherClaims method (%s)", ex.toString());
                    if (uiObj instanceof Map) {
                        //noinspection unchecked
                        claims = (Map<String, Object>) uiObj;
                    }
                }
            } else {
                LOG.debug("No USER_INFO in ContextData either.");
            }
        }

        if (claims == null) {
            deny(context, "OIDC claims not available (no ID token or userinfo). Please check: IdP=OIDC, scope 'openid', First/Post Broker Login Flow.");
            return;
        }

        String claimName = getConfig(context, CFG_CLAIM_NAME);
        String expectedValue = getConfig(context, CFG_EXPECTED_VALUE);
        String failMessage   = getConfig(context, CFG_FAIL_MESSAGE);

        LOG.debugf("Configuration: claimName=%s, expectedValue=%s, failMessage=%s",
                claimName, expectedValue, failMessage);

        if (claimName == null || claimName.isBlank()) {
            deny(context, "ClaimGate misconfigured: claimName missing.");
            return;
        }

        Object valObj = claims.get(claimName);
        String val = normalize(valObj);
        LOG.debugf("Resolved claim %s = %s (raw=%s)", claimName, val, valObj);

        boolean ok;
        if (expectedValue == null || expectedValue.isBlank()) {
            ok = (val != null && !val.isBlank()); // presence check
        } else {
            ok = (val != null) && val.equals(expectedValue); // exact, case-sensitive
            // If you want case-insensitive: ok = (val != null) && val.equalsIgnoreCase(expectedValue);
        }

        if (!ok) {
            String msg = (failMessage != null && !failMessage.isBlank())
                    ? failMessage
                    : "Your account does not meet the login requirements. (Claim missing/invalid)";
            deny(context, msg);
            return;
        }

        LOG.debug("ClaimGateAuthenticator successful: claim present and valid.");
        context.success();
    }

    private String getConfig(AuthenticationFlowContext context, String key) {
        var cfg = context.getAuthenticatorConfig();
        return (cfg != null) ? cfg.getConfig().get(key) : null;
    }

    // IMPORTANT: ACCESS_DENIED + failureChallenge => stops flow and renders KC error page
    private void deny(AuthenticationFlowContext context, String message) {
        LOG.debugf("Login denied (First/Post Broker Flow): %s", message);

        try {
            context.getEvent().error(org.keycloak.events.Errors.ACCESS_DENIED);
        } catch (Throwable ignored) {}

        Response challenge = context.form()
                .setError(message)
                .createErrorPage(Response.Status.FORBIDDEN);

        context.failureChallenge(AuthenticationFlowError.ACCESS_DENIED, challenge);
    }

    private String normalize(Object v) {
        if (v == null) return null;
        if (v instanceof java.util.Collection<?> col) {
            if (col.isEmpty()) return null;
            Object first = col.iterator().next();
            return first != null ? first.toString().trim() : null;
        }
        return v.toString().trim();
    }

    @Override
    public boolean requiresUser() { return false; }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, org.keycloak.models.UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, org.keycloak.models.UserModel user) {
        // none
    }
}
