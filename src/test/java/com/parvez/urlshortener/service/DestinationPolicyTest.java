package com.parvez.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.parvez.urlshortener.exception.InvalidUrlException;
import com.parvez.urlshortener.exception.SafetyScanUnavailableException;
import com.parvez.urlshortener.safety.AddressResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies normalization and address policy without external DNS. */
@Tag("unit")
class DestinationPolicyTest {
    private final AddressResolver resolver = mock(AddressResolver.class);
    private final DestinationPolicy policy = new DestinationPolicy(resolver, "8.8.4.0/24");

    @Test
    void normalize_publicUrl_preservesEncodedPathAndQuery() throws Exception {
        when(resolver.resolve("example.com")).thenReturn(addresses("8.8.8.8"));
        assertThat(policy.normalize("HTTPS://Example.COM.:443/a/../b%2Fc?q=a%26b#fragment").toString())
                .isEqualTo("https://example.com/b%2Fc?q=a%26b");
        assertThat(policy.normalize("http://example.com:80").toString()).isEqualTo("http://example.com/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com", "https://user:pass@example.com", "http://127.1",
            "http://2130706433", "http://0177.0.0.1", "http://0x7f.0.0.1", "http://256.1.1.1",
            "https://example.com:0", "https://example.com:65536", "http://[fe80::1%25eth0]", "/relative"})
    void normalize_invalidUrl_rejectsBeforeDns(String input) {
        assertThatThrownBy(() -> policy.normalize(input)).isInstanceOf(InvalidUrlException.class);
        verifyNoInteractions(resolver);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254",
            "100.64.0.1", "::1", "fc00::1", "fe80::1", "::ffff:127.0.0.1", "8.8.4.4", "2002:7f00:1::"})
    void normalize_nonPublicOrConfiguredAddress_rejectsEveryAnswer(String address) throws Exception {
        when(resolver.resolve("example.com")).thenReturn(new InetAddress[] {
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName(address)});
        assertThatThrownBy(() -> policy.normalize("https://example.com")).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void normalize_dnsUnavailable_failsClosed() throws Exception {
        when(resolver.resolve("example.com")).thenThrow(new UnknownHostException("private DNS detail"));
        assertThatThrownBy(() -> policy.normalize("https://example.com"))
                .isInstanceOf(SafetyScanUnavailableException.class).hasMessageNotContaining("private DNS");
    }

    @Test
    void normalize_emptyDnsAnswer_failsClosed() throws Exception {
        when(resolver.resolve("example.com")).thenReturn(new InetAddress[0]);
        assertThatThrownBy(() -> policy.normalize("https://example.com"))
                .isInstanceOf(SafetyScanUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hostname/24", "8.8.8.8/33", "::1/129", "8.8.8.8", "8.8.8.8/-1"})
    void constructor_invalidCidr_rejectsConfiguration(String cidr) {
        assertThatThrownBy(() -> new DestinationPolicy(resolver, cidr)).isInstanceOf(IllegalArgumentException.class);
    }

    private InetAddress[] addresses(String address) throws Exception {
        return new InetAddress[] {InetAddress.getByName(address)};
    }
}
