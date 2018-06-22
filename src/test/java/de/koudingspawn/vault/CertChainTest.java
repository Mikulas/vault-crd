package de.koudingspawn.vault;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import de.koudingspawn.vault.crd.Vault;
import de.koudingspawn.vault.crd.VaultSpec;
import de.koudingspawn.vault.crd.VaultType;
import de.koudingspawn.vault.kubernetes.EventHandler;
import de.koudingspawn.vault.kubernetes.scheduler.impl.CertRefresh;
import de.koudingspawn.vault.vault.communication.SecretNotAccessibleException;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static de.koudingspawn.vault.Constants.COMPARE_ANNOTATION;
import static de.koudingspawn.vault.Constants.LAST_UPDATE_ANNOTATION;
import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(
        properties = {
                "kubernetes.vault.url=http://localhost:8206/v1/",
                "kubernetes.initial-delay=5000000"
        },
        classes = {
                TestConfiguration.class
        }

)
public class CertChainTest {

    @ClassRule
    public static WireMockClassRule wireMockClassRule =
            new WireMockClassRule(wireMockConfig().port(8206));

    @Rule
    public WireMockClassRule instanceRule = wireMockClassRule;

    @Autowired
    public EventHandler handler;

    @Autowired
    KubernetesClient client;

    @Autowired
    CertRefresh certRefresh;

    @Before
    public void before() {
        WireMock.resetAllScenarios();
        client.secrets().inAnyNamespace().delete();
    }

    @Test
    public void shouldGenerateCertFromVaultResource() {
        Vault vault = new Vault();
        vault.setMetadata(
                new ObjectMetaBuilder().withName("certificate").withNamespace("default").build()
        );
        VaultSpec spec = new VaultSpec();
        spec.setType(VaultType.CERT);
        spec.setPath("secret/certificate");
        vault.setSpec(spec);

        stubFor(get(urlEqualTo("/v1/secret/certificate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"request_id\": \"6cc090a8-3821-8244-73e4-5ab62b605587\",\n" +
                                "  \"lease_id\": \"\",\n" +
                                "  \"renewable\": false,\n" +
                                "  \"lease_duration\": 2764800,\n" +
                                "  \"data\": {\n" +
                                "    \"data\": {\n" +
                                "      \"certificate\": \"CERTIFICATE\",\n" +
                                "      \"issuing_ca\": \"ISSUINGCA\",\n" +
                                "      \"ca_chain\": [\"ISSUINGCA\"],\n" +
                                "      \"private_key\": \"PRIVATEKEY\"\n" +
                                "    }\n" +
                                "  },\n" +
                                "  \"wrap_info\": null,\n" +
                                "  \"warnings\": null,\n" +
                                "  \"auth\": null\n" +
                                "}")));

        handler.addHandler(vault);

        Secret secret = client.secrets().inNamespace("default").withName("certificate").get();

        assertEquals("certificate", secret.getMetadata().getName());
        assertEquals("default", secret.getMetadata().getNamespace());
        assertEquals("Opaque", secret.getType());
        assertNotNull(secret.getMetadata().getAnnotations().get("vault.koudingspawn.de" + LAST_UPDATE_ANNOTATION));
        assertEquals("GwzyEg3PQ2uSYFL2U6i0X2RibVs9p5gvOoTdZVQdT6s=", secret.getMetadata().getAnnotations().get("vault.koudingspawn.de" + COMPARE_ANNOTATION));

        String crtB64 = secret.getData().get("tls.crt");
        String crt = new String(java.util.Base64.getDecoder().decode(crtB64));
        String keyB64 = secret.getData().get("tls.key");
        String key = new String(java.util.Base64.getDecoder().decode(keyB64));

        assertEquals("CERTIFICATE\nISSUINGCA", crt);
        assertEquals("PRIVATEKEY", key);
    }

    @Test
    public void shouldCheckIfCertificateHasChangedAndReturnFalse() throws SecretNotAccessibleException {
        Vault vault = new Vault();
        vault.setMetadata(
                new ObjectMetaBuilder().withName("certificate").withNamespace("default").build()
        );
        VaultSpec spec = new VaultSpec();
        spec.setType(VaultType.CERT);
        spec.setPath("secret/certificate");
        vault.setSpec(spec);

        stubFor(get(urlEqualTo("/v1/secret/certificate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"request_id\": \"6cc090a8-3821-8244-73e4-5ab62b605587\",\n" +
                                "  \"lease_id\": \"\",\n" +
                                "  \"renewable\": false,\n" +
                                "  \"lease_duration\": 2764800,\n" +
                                "  \"data\": {\n" +
                                "    \"data\": {\n" +
                                "      \"certificate\": \"CERTIFICATE\",\n" +
                                "      \"issuing_ca\": \"ISSUINGCA\",\n" +
                                "      \"ca_chain\": [\"ISSUINGCA\"],\n" +
                                "      \"private_key\": \"PRIVATEKEY\"\n" +
                                "    }\n" +
                                "  },\n" +
                                "  \"wrap_info\": null,\n" +
                                "  \"warnings\": null,\n" +
                                "  \"auth\": null\n" +
                                "}")));

        handler.addHandler(vault);

        assertFalse(certRefresh.refreshIsNeeded(vault));
    }

    @Test
    public void shouldCheckIfCertificateHasChangedAndReturnTrue() throws SecretNotAccessibleException {
        Vault vault = new Vault();
        vault.setMetadata(
                new ObjectMetaBuilder().withName("certificate").withNamespace("default").build()
        );
        VaultSpec spec = new VaultSpec();
        spec.setType(VaultType.CERT);
        spec.setPath("secret/certificate");
        vault.setSpec(spec);

        stubFor(get(urlEqualTo("/v1/secret/certificate"))
                .inScenario("Cert secret change")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("Cert first request done")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"request_id\": \"6cc090a8-3821-8244-73e4-5ab62b605587\",\n" +
                                "  \"lease_id\": \"\",\n" +
                                "  \"renewable\": false,\n" +
                                "  \"lease_duration\": 2764800,\n" +
                                "  \"data\": {\n" +
                                "    \"data\": {\n" +
                                "      \"certificate\": \"CERTIFICATE\",\n" +
                                "      \"issuing_ca\": \"ISSUINGCA\",\n" +
                                "      \"ca_chain\": [\"ISSUINGCA\"],\n" +
                                "      \"private_key\": \"PRIVATEKEY\"\n" +
                                "    }\n" +
                                "  },\n" +
                                "  \"wrap_info\": null,\n" +
                                "  \"warnings\": null,\n" +
                                "  \"auth\": null\n" +
                                "}")));

        stubFor(get(urlEqualTo("/v1/secret/certificate"))
                .inScenario("Cert secret change")
                .whenScenarioStateIs("Cert first request done")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\n" +
                                "  \"request_id\": \"6cc090a8-3821-8244-73e4-5ab62b605587\",\n" +
                                "  \"lease_id\": \"\",\n" +
                                "  \"renewable\": false,\n" +
                                "  \"lease_duration\": 2764800,\n" +
                                "  \"data\": {\n" +
                                "    \"data\": {\n" +
                                "      \"certificate\": \"CERTIFICATECHANGE\",\n" +
                                "      \"issuing_ca\": \"ISSUINGCA\",\n" +
                                "      \"ca_chain\": [\"ISSUINGCA\"],\n" +
                                "      \"private_key\": \"PRIVATEKEY\"\n" +
                                "    }\n" +
                                "  },\n" +
                                "  \"wrap_info\": null,\n" +
                                "  \"warnings\": null,\n" +
                                "  \"auth\": null\n" +
                                "}")));

        handler.addHandler(vault);

        assertTrue(certRefresh.refreshIsNeeded(vault));
    }



}
