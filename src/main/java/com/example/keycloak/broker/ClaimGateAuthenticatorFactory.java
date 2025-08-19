package com.example.keycloak.broker;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.ConfigurableAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.List;

public class ClaimGateAuthenticatorFactory
        implements AuthenticatorFactory, ConfigurableAuthenticatorFactory, EnvironmentDependentProviderFactory {

    public static final String ID = "idp-claim-gate";

    private static final List<ProviderConfigProperty> CONFIG = new ArrayList<>();
    static {
        ProviderConfigProperty p1 = new ProviderConfigProperty();
        p1.setName(ClaimGateAuthenticator.CFG_CLAIM_NAME);
        p1.setLabel("Claim name");
        p1.setHelpText("Name des Claims im ID Token (z. B. extension_myClaim)");
        p1.setType(ProviderConfigProperty.STRING_TYPE);
        CONFIG.add(p1);

        ProviderConfigProperty p2 = new ProviderConfigProperty();
        p2.setName(ClaimGateAuthenticator.CFG_EXPECTED_VALUE);
        p2.setLabel("Erwarteter Wert (optional)");
        p2.setHelpText("Wenn gesetzt, muss der Claim exakt diesen Wert haben.");
        p2.setType(ProviderConfigProperty.STRING_TYPE);
        CONFIG.add(p2);

        ProviderConfigProperty p3 = new ProviderConfigProperty();
        p3.setName(ClaimGateAuthenticator.CFG_FAIL_MESSAGE);
        p3.setLabel("Fehlermeldung (optional)");
        p3.setHelpText("Benutzerfreundliche Meldung, die beim Blockieren angezeigt wird.");
        p3.setType(ProviderConfigProperty.STRING_TYPE);
        CONFIG.add(p3);
    }

    @Override public String getId() { return ID; }
    @Override public String getDisplayType() { return "IdP Claim Gate (OIDC)"; }
    @Override public String getHelpText() { return "Blockiert First-/Post-Broker-Login, wenn ein erforderlicher Claim im OIDC-Token fehlt/abweicht."; }
    @Override public Authenticator create(KeycloakSession session) { return new ClaimGateAuthenticator(); }
    @Override public List<ProviderConfigProperty> getConfigProperties() { return CONFIG; }
    @Override public boolean isConfigurable() { return true; }
    @Override public boolean isUserSetupAllowed() { return false; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(org.keycloak.models.KeycloakSessionFactory factory) {}
    @Override public void close() {}

    @Override
    public Requirement[] getRequirementChoices() {
        return new Requirement[] { Requirement.REQUIRED, Requirement.DISABLED };
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    // seit neueren Keycloak-Versionen: Scope-Variante
    @Override
    public boolean isSupported(Config.Scope scope) {
        return true;
    }
}
